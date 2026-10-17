package com.codefactory.devnet.interaction.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia de comentarios. Lo implementa {@code infrastructure}.
 */
public interface RepositorioComentarios {

    /**
     * Si la publicacion existe y esta PUBLICADA, sea proyecto o discusion.
     *
     * <p>Una publicacion oculta, archivada o inexistente no admite comentarios ni los
     * muestra; el llamador responde 404 en los tres casos (ADR-005).</p>
     */
    boolean publicacionComentable(UUID publicacionId);

    void guardar(Comentario comentario);

    Optional<Comentario> porId(UUID comentarioId);

    /** Pasa el comentario a ELIMINADO. La fila se conserva. */
    void marcarEliminado(UUID comentarioId);

    /** Comentarios PUBLICADOS de la publicacion, del mas antiguo al mas reciente. */
    List<Comentario> visiblesDe(UUID publicacionId);
}
