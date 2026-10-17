package com.codefactory.devnet.interaction.domain;

import com.codefactory.devnet.shared.api.ExcepcionNegocio;

import java.util.Locale;

/**
 * Tipos de reaccion que acepta la base ({@code ck_reaccion_pub_tipo}).
 *
 * <p>Si la peticion no dice cual, es {@link #ME_GUSTA}: es el "like" de la HU-14.</p>
 */
public enum TipoReaccion {
    ME_GUSTA, UTIL, INNOVADOR;

    /** Convierte el texto de la peticion; vacio o ausente es ME_GUSTA, desconocido es 400. */
    public static TipoReaccion de(String texto) {
        if (texto == null || texto.isBlank()) {
            return ME_GUSTA;
        }
        try {
            return valueOf(texto.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ExcepcionNegocio(CodigoErrorInteraccion.REACCION_TIPO_INVALIDO);
        }
    }
}
