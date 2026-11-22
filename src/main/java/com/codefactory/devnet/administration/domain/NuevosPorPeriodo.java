package com.codefactory.devnet.administration.domain;

import java.time.LocalDate;

/**
 * Registros nuevos de un periodo.
 *
 * @param periodo primer dia del periodo (lunes de la semana, o dia 1 del mes). El primero y el
 *                ultimo pueden quedar parcialmente fuera del rango pedido: solo se suman los dias
 *                que caen dentro.
 */
public record NuevosPorPeriodo(LocalDate periodo, long nuevos) {
}
