package com.codefactory.devnet.messaging.domain;

import com.codefactory.devnet.shared.api.ExcepcionNegocio;

import java.time.Instant;
import java.util.UUID;

public record Mensaje(UUID id, UUID conversacionId, UUID remitenteId, String contenido, Instant enviadoEn) {
    public static String validarContenido(String contenido) {
        if (contenido == null || contenido.isBlank() || contenido.length() > 4000) {
            throw new ExcepcionNegocio(CodigoErrorMensajeria.MENSAJE_CONTENIDO_INVALIDO);
        }
        return contenido;
    }
}
