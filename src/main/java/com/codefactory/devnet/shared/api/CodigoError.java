package com.codefactory.devnet.shared.api;

/**
 * Contrato que debe cumplir todo codigo de error de la API.
 *
 * <p>Cada modulo declara sus codigos en un {@code enum} propio que implementa esta
 * interfaz, con el prefijo del modulo: {@code AUTH_}, {@code PERFIL_},
 * {@code PROYECTO_}, {@code DISCUSION_}, {@code COMENTARIO_}, {@code MENSAJE_}.</p>
 *
 * <p>Que el codigo HTTP viva aqui y no en el controlador es deliberado: obliga a
 * decidirlo en el mismo sitio donde se nombra el error, y hace imposible que la misma
 * condicion devuelva 400 en un endpoint y 422 en otro.</p>
 *
 * <p>Se expone como {@code int} y no como {@code HttpStatus} de Spring a proposito.
 * Los {@code enum} de codigos de cada modulo viven en su capa {@code domain}, y esa
 * capa no puede depender del framework (regla 2 de {@code FronterasModularesTest}).
 * Un entero es un valor, no una dependencia; el manejador global lo convierte.</p>
 *
 * <p>Un test de arquitectura prohibe construir una {@link RespuestaError} fuera del
 * kernel: el codigo siempre sale de una implementacion de esta interfaz.</p>
 *
 * <p>Ver el mapa completo de situaciones a codigos HTTP en
 * {@code docs/adr/ADR-005-contrato-errores-traceid.md}.</p>
 */
public interface CodigoError {

    /** Codigo estable en SCREAMING_SNAKE_CASE. Forma parte del contrato publico. */
    String codigo();

    /** Estado HTTP con el que se responde esta condicion. Por ejemplo, 422. */
    int estadoHttp();

    /** Mensaje en espanol dirigido a una persona, sin detalles tecnicos. */
    String mensajePorDefecto();
}