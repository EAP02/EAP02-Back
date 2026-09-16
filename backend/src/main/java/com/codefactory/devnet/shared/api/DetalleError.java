package com.codefactory.devnet.shared.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Un elemento de {@link RespuestaError#details()}.
 *
 * <p>Para errores de validacion se emite uno por campo invalido. Para reglas de
 * negocio, uno por condicion incumplida.</p>
 *
 * <p>Cuidado al poblar {@code valor}: no debe llevar datos secretos ni personales.
 * Ver la clasificacion de datos en {@code docs/bd/03-diccionario-datos.md}.</p>
 */
@Schema(name = "DetalleError")
public record DetalleError(

        @Schema(example = "estado", description = "Campo o atributo afectado")
        String campo,

        @Schema(example = "ARCHIVADO", description = "Valor recibido. Nunca datos sensibles.")
        String valor,

        @Schema(example = "transicion_no_permitida", description = "Motivo legible por maquina")
        String razon
) {
    public static DetalleError de(String campo, String razon) {
        return new DetalleError(campo, null, razon);
    }

    public static DetalleError de(String campo, Object valor, String razon) {
        return new DetalleError(campo, valor == null ? null : valor.toString(), razon);
    }
}