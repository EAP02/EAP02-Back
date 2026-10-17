package com.codefactory.devnet.interaction.application;

import com.codefactory.devnet.identity.api.UsuarioDirectorio;
import com.codefactory.devnet.interaction.domain.RepositorioComentarios;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HU-13, criterio 3 ("deja de mostrarse"): los comentarios visibles de una publicacion.
 *
 * <p>Lectura publica. Solo salen los PUBLICADOS, asi un comentario eliminado
 * desaparece de la lista.</p>
 */
@Service
public class ListarComentarios {

    private final RepositorioComentarios comentarios;
    private final UsuarioDirectorio directorio;

    public ListarComentarios(RepositorioComentarios comentarios, UsuarioDirectorio directorio) {
        this.comentarios = comentarios;
        this.directorio = directorio;
    }

    @Transactional(readOnly = true)
    public List<ComentarioVisible> ejecutar(UUID publicacionId) {
        if (!comentarios.publicacionComentable(publicacionId)) {
            throw ExcepcionNegocio.noEncontrado("publicacion", publicacionId);
        }

        // El nombre pertenece a identity: se pide una vez por autor.
        Map<UUID, String> nombres = new HashMap<>();
        return comentarios.visiblesDe(publicacionId).stream()
                .map(c -> new ComentarioVisible(
                        c.id(),
                        c.autorId(),
                        nombres.computeIfAbsent(c.autorId(), this::nombreDe),
                        c.contenido(),
                        c.creadoEn()))
                .toList();
    }

    private String nombreDe(UUID usuarioId) {
        return directorio.buscar(usuarioId)
                .map(UsuarioDirectorio.UsuarioResumen::nombreUsuario)
                .orElse(null);
    }

    public record ComentarioVisible(UUID id, UUID autorId, String autorNombreUsuario,
                                    String contenido, Instant creadoEn) { }
}
