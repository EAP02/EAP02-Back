package com.codefactory.devnet.interaction.application;

import com.codefactory.devnet.identity.api.UsuarioDirectorio;
import com.codefactory.devnet.interaction.domain.CodigoErrorInteraccion;
import com.codefactory.devnet.interaction.domain.Comentario;
import com.codefactory.devnet.interaction.domain.RepositorioComentarios;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * HU-13, criterios 1 y 2: comentar en un proyecto o una discusion.
 *
 * <ol>
 *   <li>un comentario valido se publica y queda asociado a su autor y a la
 *       publicacion;</li>
 *   <li>uno vacio se rechaza (lo decide {@link Comentario#nuevo}).</li>
 * </ol>
 *
 * <p>El autor sale SIEMPRE del token; la peticion ni siquiera tiene un campo para
 * declararlo.</p>
 */
@Service
public class PublicarComentario {

    private final RepositorioComentarios comentarios;
    private final UsuarioDirectorio directorio;

    public PublicarComentario(RepositorioComentarios comentarios, UsuarioDirectorio directorio) {
        this.comentarios = comentarios;
        this.directorio = directorio;
    }

    @Transactional
    public Resultado ejecutar(Comando comando) {
        UsuarioDirectorio.UsuarioResumen autor = directorio.autenticado()
                .orElseThrow(() -> ExcepcionNegocio.accesoDenegado("sin_sesion"));

        if (!directorio.estaActivo(autor.id())) {
            throw new ExcepcionNegocio(CodigoErrorInteraccion.COMENTARIO_AUTOR_SIN_PERMISO_DE_ESCRITURA);
        }

        // Primero la forma (400), despues la existencia (404): lo barato antes que la base.
        Comentario nuevo = Comentario.nuevo(comando.publicacionId(), autor.id(), comando.contenido());

        if (!comentarios.publicacionComentable(comando.publicacionId())) {
            throw ExcepcionNegocio.noEncontrado("publicacion", comando.publicacionId());
        }

        comentarios.guardar(nuevo);
        return new Resultado(nuevo.id(), nuevo.publicacionId(), nuevo.autorId(),
                nuevo.contenido(), nuevo.creadoEn());
    }

    public record Comando(UUID publicacionId, String contenido) { }

    public record Resultado(UUID id, UUID publicacionId, UUID autorId, String contenido, Instant creadoEn) { }
}
