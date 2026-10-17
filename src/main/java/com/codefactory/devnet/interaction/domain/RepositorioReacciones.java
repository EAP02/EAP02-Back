package com.codefactory.devnet.interaction.domain;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Puerto de persistencia de las reacciones a publicaciones (HU-14). */
public interface RepositorioReacciones {

    /** Que paso al reaccionar; solo {@link #CREADA} mueve el contador. */
    enum Efecto { CREADA, CAMBIADA, SIN_CAMBIO }

    /** La publicacion existe y esta PUBLICADA. */
    boolean publicacionReaccionable(UUID publicacionId);

    /**
     * Deja a lo mas UNA reaccion del usuario en la publicacion. Si no tenia, la crea y
     * suma uno al contador; si tenia otra de distinto tipo, solo cambia el tipo; si era
     * la misma, no hace nada. Es atomico: dos peticiones simultaneas no duplican.
     */
    Efecto reaccionar(UUID usuarioId, UUID publicacionId, TipoReaccion tipo);

    /** Quita la reaccion del usuario y resta uno al contador. {@code false} si no tenia. */
    boolean quitar(UUID usuarioId, UUID publicacionId);

    /** Valor del contador {@code publicacion.contador_reacciones}. */
    int total(UUID publicacionId);

    /** Cuantas reacciones hay de cada tipo (solo los tipos con al menos una). */
    Map<TipoReaccion, Integer> porTipo(UUID publicacionId);

    Optional<TipoReaccion> deUsuario(UUID usuarioId, UUID publicacionId);
}
