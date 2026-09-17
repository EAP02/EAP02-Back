package com.codefactory.devnet.identity.application;

import com.codefactory.devnet.identity.api.UsuarioRegistrado;
import com.codefactory.devnet.identity.domain.CodigoErrorIdentidad;
import com.codefactory.devnet.identity.domain.RepositorioUsuarios;
import com.codefactory.devnet.identity.domain.Usuario;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class RegistrarUsuario {
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
        if (usuarios.existeNombreUsuario(nombre)) {
            throw new ExcepcionNegocio(CodigoErrorIdentidad.AUTH_NOMBRE_USUARIO_YA_REGISTRADO);
        }

        Usuario creado = usuarios.crear(correoNormalizado, nombre, codificador.encode(clave));
        eventos.publishEvent(new UsuarioRegistrado(creado.id(), creado.nombreUsuario()));
        return new Resultado(creado.id().toString(), correoNormalizado, creado.nombreUsuario());
    }

    private String normalizarNombre(String solicitado, String correo) {
        String base = solicitado == null || solicitado.isBlank()
                ? correo.substring(0, correo.indexOf('@'))
                : solicitado;
        return base.strip().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "");
    }

    public record Resultado(String id, String correo, String nombreUsuario) { }
}
