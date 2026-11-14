package com.codefactory.devnet.administration.domain;

import com.codefactory.devnet.shared.api.ExcepcionNegocio;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Rango de dias, ambos extremos incluidos. Cualquiera de los dos puede faltar (rango
 * abierto).
 *
 * <p>Los dias son de {@code America/Bogota}, no UTC, igual que en
 * {@code sp_consolidar_metricas_diarias} (V11): un dia UTC empieza a las 7 p. m. en
 * Colombia y partiria la actividad de una tarde en dos fechas.</p>
 *
 * <p>Vive aqui y no en el registro de auditoria porque HU-19 (reporte de actividad)
 * rechaza el mismo caso: una fecha final anterior a la inicial.</p>
 */
public record RangoFechas(LocalDate desde, LocalDate hasta) {

    public static final ZoneId ZONA = ZoneId.of("America/Bogota");

    public RangoFechas {
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw new ExcepcionNegocio(CodigoErrorAdministracion.RANGO_FECHAS_INVALIDO);
        }
    }

    /** Primer instante del dia inicial, o {@code null} si el rango no tiene inicio. */
    public Instant inicio() {
        return desde == null ? null : desde.atStartOfDay(ZONA).toInstant();
    }

    /**
     * Primer instante del dia siguiente al final, o {@code null} si no hay final. Es un
     * limite exclusivo ({@code creado_en < fin}), que evita el error de medianoche.
     */
    public Instant finExclusivo() {
        return hasta == null ? null : hasta.plusDays(1).atStartOfDay(ZONA).toInstant();
    }
}
