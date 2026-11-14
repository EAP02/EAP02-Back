package com.codefactory.devnet.administration.domain;

/** Lo publicado y los registros nuevos en un rango, sumados de {@code metricas_diarias}. */
public record Totales(long proyectos, long discusiones, long comentarios, long usuariosNuevos) {
}
