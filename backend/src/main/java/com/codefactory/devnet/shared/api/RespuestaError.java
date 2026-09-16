package com.codefactory.devnet.shared.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Cuerpo unico de toda respuesta de error de la API.
 *
 * <p>Forma parte del contrato publico: los nombres de campo estan fijados por
 * ADR-005 y por el lineamiento 3.3. Cambiarlos o eliminarlos exige una version
 * nueva de la API.</p>
 *
 * @param errorCode codigo estable en SCREAMING_SNAKE_CASE, prefijado por modulo.
 *                  Es contra esto que programa un cliente, no contra el mensaje.
 * @param message   texto en espanol dirigido a una persona. Nunca contiene nombres
 *                  de clase, SQL, rutas de archivo ni trazas de pila.
 * @param details   siempre un arreglo, aunque venga vacio o con un solo elemento.
 * @param traceId   correlaciona esta respuesta con el log JSON y con la fila de
 *                  auditoria. Es lo que un usuario reporta cuando algo falla.
 * @param timestamp momento del fallo, en UTC.
 * @param path      ruta que se invoco.
 */
@Schema(name = "RespuestaError", description = "Cuerpo uniforme de error (ADR-005)")
public record RespuestaError(

        @Schema(example = "PUBLICACION_TRANSICION_INVALIDA")
        String errorCode,

        @Schema(example = "No se puede publicar una publicacion archivada.")
        String message,

        List<DetalleError> details,

        @Schema(example = "4b1e9f2a7c3d5e80")
        String traceId,

        Instant timestamp,

        @Schema(example = "/api/v1/publicaciones/8f3a.../publicacion")
        String path
) {
    public RespuestaError {
        details = details == null ? List.of() : List.copyOf(details);
    }
}