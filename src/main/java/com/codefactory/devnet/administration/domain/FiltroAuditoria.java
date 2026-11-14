package com.codefactory.devnet.administration.domain;

import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import com.codefactory.devnet.shared.audit.EventoAuditoria;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Criterios con los que un administrador acota el registro de auditoria. Todos son
 * opcionales y se combinan con AND.
 *
 * @param operacion nombre de una {@link EventoAuditoria.Operacion}, ya normalizado
 * @param actorId   quien hizo la accion
 * @param tabla     objeto afectado ({@code usuario_rol}, {@code comentario}...)
 * @param rango     dias en los que ocurrio
 */
public record FiltroAuditoria(String operacion, UUID actorId, String tabla, RangoFechas rango) {

    /** Normaliza los textos y valida la operacion contra el enum del registro. */
    public static FiltroAuditoria de(String operacion, UUID actorId, String tabla, RangoFechas rango) {
        return new FiltroAuditoria(normalizarOperacion(operacion), actorId, vacioANulo(tabla), rango);
    }

    private static String normalizarOperacion(String operacion) {
        String texto = vacioANulo(operacion);
        if (texto == null) {
            return null;
        }
        String candidata = texto.toUpperCase(Locale.ROOT);
        boolean valida = Arrays.stream(EventoAuditoria.Operacion.values())
                .anyMatch(o -> o.name().equals(candidata));
        if (!valida) {
            throw new ExcepcionNegocio(CodigoErrorAdministracion.AUDITORIA_OPERACION_INVALIDA,
                    "La operacion indicada no es valida. Usa una de: "
                            + Arrays.toString(EventoAuditoria.Operacion.values()) + ".",
                    List.of());
        }
        return candidata;
    }

    private static String vacioANulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
