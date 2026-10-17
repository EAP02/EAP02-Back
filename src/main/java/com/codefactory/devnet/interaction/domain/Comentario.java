package com.codefactory.devnet.interaction.domain;

import com.codefactory.devnet.shared.api.DetalleError;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * HU-13: un comentario sobre una publicacion (proyecto o discusion).
 *
 * <p>Se asocia a la publicacion por su identificador, no por su tipo: en la base,
 * {@code publicacion} es el supertipo de proyectos y discusiones y {@code comentario}
 * tiene una clave foranea real hacia ella. Por eso un mismo comentario sirve para los
 * dos sin una referencia polimorfica.</p>
 *
 * <p>Inmutable: eliminar devuelve otro comentario en estado ELIMINADO. La fila no se
 * borra, asi el rastro (y el hilo de {@code discussion}, que solo cuenta los
 * PUBLICADOS) sigue siendo coherente.</p>
 */
public record Comentario(
        UUID id,
        UUID publicacionId,
        UUID autorId,
        String contenido,
        Estado estado,
        Instant creadoEn
) {

    /** Debe coincidir con ck_comentario_contenido de V1. */
    public static final int LONGITUD_MAXIMA = 5000;

    /** Debe coincidir con ck_comentario_estado de V1. */
    public enum Estado { PUBLICADO, OCULTO, ELIMINADO }

    /**
     * Criterio 1 y 2 de HU-13: un comentario valido nace PUBLICADO y asociado a su autor
     * y a la publicacion; uno vacio se rechaza. La regla vive aqui y no solo en el
     * controlador para que ninguna otra ruta pueda saltarsela.
     */
    public static Comentario nuevo(UUID publicacionId, UUID autorId, String contenido) {
        String limpio = contenido == null ? "" : contenido.strip();

        if (limpio.isEmpty()) {
            throw new ExcepcionNegocio(
                    CodigoErrorInteraccion.COMENTARIO_VACIO,
                    List.of(DetalleError.de("contenido", "obligatorio")));
        }
        if (limpio.length() > LONGITUD_MAXIMA) {
            throw new ExcepcionNegocio(
                    CodigoErrorInteraccion.COMENTARIO_DEMASIADO_LARGO,
                    List.of(DetalleError.de("contenido", limpio.length(), "longitud_maxima_" + LONGITUD_MAXIMA)));
        }
        return new Comentario(UUID.randomUUID(), publicacionId, autorId, limpio,
                Estado.PUBLICADO, Instant.now());
    }

    public boolean estaVisible() {
        return estado == Estado.PUBLICADO;
    }

    /** Criterio 3: eliminado deja de mostrarse. */
    public Comentario eliminado() {
        return new Comentario(id, publicacionId, autorId, contenido, Estado.ELIMINADO, creadoEn);
    }
}
