package com.codefactory.devnet.administration.domain;

import com.codefactory.devnet.shared.api.CodigoError;

/**
 * Codigos de error del modulo de administracion (auditoria y, despues, reportes).
 *
 * <p>Los tres son 400 y no 422: describen la <b>forma</b> de la consulta, no una regla
 * que dependa del estado del sistema (mismo criterio que {@code COMENTARIO_VACIO}).</p>
 */
public enum CodigoErrorAdministracion implements CodigoError {

    /** La fecha final es anterior a la inicial. Lo comparten el registro de auditoria y el reporte. */
    RANGO_FECHAS_INVALIDO(
            400,
            "Rango de fechas invalido: la fecha final no puede ser anterior a la inicial."),

    /** La operacion pedida como filtro no existe. El mensaje concreto lista las validas. */
    AUDITORIA_OPERACION_INVALIDA(
            400,
            "La operacion indicada no es valida."),

    /** Pagina negativa o tamano fuera de 1..100. */
    PAGINACION_INVALIDA(
            400,
            "La paginacion no es valida: la pagina empieza en 0 y el tamano va de 1 a 100.");

    private final Definicion definicion;

    CodigoErrorAdministracion(int estado, String mensaje) {
        this.definicion = new Definicion(estado, mensaje);
    }

    @Override
    public Definicion definicion() {
        return definicion;
    }
}
