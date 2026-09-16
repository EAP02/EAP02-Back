package com.codefactory.devnet.project.domain;

/**
 * Madurez del proyecto. Debe coincidir con ck_proyecto_estado en V1__baseline.sql.
 *
 * <p>No confundir con el estado de la publicacion ({@code BORRADOR}, {@code PUBLICADO},
 * {@code ARCHIVADO}): este describe el trabajo, aquel su visibilidad.</p>
 */
public enum EstadoProyecto {
    IDEA,
    EN_DESARROLLO,
    ESTABLE,
    PAUSADO
}