package com.codefactory.devnet.administration.domain;

import com.codefactory.devnet.shared.api.ExcepcionNegocio;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Como se agrupa el crecimiento de registros: por semana (lunes a domingo) o por mes calendario. */
public enum Agrupacion {
    SEMANA, MES;

    /** Sin valor, semana. Se acepta sin importar mayusculas ni espacios. */
    public static Agrupacion de(String texto) {
        if (texto == null || texto.isBlank()) {
            return SEMANA;
        }
        String candidata = texto.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(a -> a.name().equals(candidata))
                .findFirst()
                .orElseThrow(() -> new ExcepcionNegocio(
                        CodigoErrorAdministracion.AGRUPACION_INVALIDA,
                        "La agrupacion no es valida. Usa SEMANA o MES.",
                        List.of()));
    }
}
