package com.codefactory.devnet.interaction.application;

import com.codefactory.devnet.identity.api.UsuarioDirectorio;
import com.codefactory.devnet.interaction.domain.CodigoErrorInteraccion;
import com.codefactory.devnet.interaction.domain.RepositorioReacciones;
import com.codefactory.devnet.interaction.domain.TipoReaccion;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * HU-14, criterios 1 y 3: reaccionar a una publicacion.
 *
 * <ol>
 *   <li>al reaccionar, el contador sube en uno;</li>
 *   <li>solo una reaccion por usuario y publicacion: reaccionar de nuevo no la duplica
 *       (si cambia el tipo, se reemplaza; si es el mismo, no pasa nada).</li>
 * </ol>
 *
 * <p>El usuario sale SIEMPRE del token.</p>
 */
@Service
public class Reaccionar {

    private final RepositorioReacciones reacciones;
    private final UsuarioDirectorio directorio;

    public Reaccionar(RepositorioReacciones reacciones, UsuarioDirectorio directorio) {
        this.reacciones = reacciones;
        this.directorio = directorio;
    }

    @Transactional
    public Resultado ejecutar(Comando comando) {
        UsuarioDirectorio.UsuarioResumen usuario = directorio.autenticado()
                .orElseThrow(() -> ExcepcionNegocio.accesoDenegado("sin_sesion"));

        if (!directorio.estaActivo(usuario.id())) {
            throw new ExcepcionNegocio(CodigoErrorInteraccion.REACCION_AUTOR_SIN_PERMISO_DE_ESCRITURA);
        }

        // Primero la forma (400), despues la existencia (404).
        TipoReaccion tipo = TipoReaccion.de(comando.tipo());

        if (!reacciones.publicacionReaccionable(comando.publicacionId())) {
            throw ExcepcionNegocio.noEncontrado("publicacion", comando.publicacionId());
        }

        reacciones.reaccionar(usuario.id(), comando.publicacionId(), tipo);
        return new Resultado(comando.publicacionId(), tipo, reacciones.total(comando.publicacionId()));
    }

    public record Comando(UUID publicacionId, String tipo) { }

    public record Resultado(UUID publicacionId, TipoReaccion tipo, int total) { }
}
