package com.codefactory.devnet.identity.application;

import com.codefactory.devnet.identity.api.UsuarioRegistrado;
import com.codefactory.devnet.identity.domain.CodigoErrorIdentidad;
import com.codefactory.devnet.identity.domain.RepositorioUsuarios;
import com.codefactory.devnet.identity.domain.Usuario;
import com.codefactory.devnet.shared.api.CodigoErrorComun;
import com.codefactory.devnet.shared.api.DetalleError;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class RegistrarUsuario {

    /**
     * Debe coincidir con {@code ck_usuario_nombre_usuario_formato} en V1__baseline.sql.
     *
     * <p>Se comprueba aqui para responder 400 con el campo senalado. Sin esto, un
     * nombre que la base rechaza (por ejemplo {@code ana.dev}, derivado de un correo
     * con punto) llegaba como violacion de integridad y salia como un 409 que no
     * explicaba nada.</p>
     */
    private static final Pattern NOMBRE_VALIDO = Pattern.compile("^[a-z0-9][a-z0-9_-]{2,29}$");

    private final RepositorioUsuarios usuarios;
    private final PasswordEncoder codificador;
    private final ApplicationEventPublisher eventos;

    public RegistrarUsuario(RepositorioUsuarios usuarios, PasswordEncoder codificador,
                            ApplicationEventPublisher eventos) {
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.eventos = eventos;
    }

    @Transactional
    public Resultado ejecutar(String correo, String clave, String nombreSolicitado) {
        String correoNormalizado = correo.strip().toLowerCase(Locale.ROOT);
        if (usuarios.existeCorreo(correoNormalizado)) {
            throw new ExcepcionNegocio(CodigoErrorIdentidad.AUTH_CORREO_YA_REGISTRADO);
        }

        String nombre = normalizarNombre(nombreSolicitado, correoNormalizado);
        if (!NOMBRE_VALIDO.matcher(nombre).matches()) {
            throw new ExcepcionNegocio(
                    CodigoErrorComun.VALIDACION_FALLIDA,
                    "El nombre de usuario debe tener entre 3 y 30 caracteres: letras, numeros, '_' o '-', "
                            + "y empezar por letra o numero.",
                    List.of(DetalleError.de("nombreUsuario", nombre, "formato_invalido")));
        }
        if (usuarios.existeNombreUsuario(nombre)) {
            throw new ExcepcionNegocio(CodigoErrorIdentidad.AUTH_NOMBRE_USUARIO_YA_REGISTRADO);
        }

        Usuario creado = usuarios.crear(correoNormalizado, nombre, codificador.encode(clave));
        eventos.publishEvent(new UsuarioRegistrado(creado.id(), creado.nombreUsuario()));
        return new Resultado(creado.id().toString(), correoNormalizado, creado.nombreUsuario());
    }

    private String normalizarNombre(String solicitado, String correo) {
        if (solicitado != null && !solicitado.isBlank()) {
            return limpiar(solicitado);
        }
        // Si lo derivado no cumple NOMBRE_VALIDO ("ab", "-ana", mas de 30 caracteres),
        // no se corrige a escondidas: ejecutar responde 400 y el cliente envia un
        // nombre elegido por la persona, en vez de recibir uno que nadie escogio.
        return limpiar(correo.substring(0, correo.indexOf('@')));
    }

    /** El punto se descarta igual que cualquier otro simbolo: la base no lo admite. */
    private static String limpiar(String nombre) {
        return nombre.strip().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
    }

    public record Resultado(String id, String correo, String nombreUsuario) { }
}
