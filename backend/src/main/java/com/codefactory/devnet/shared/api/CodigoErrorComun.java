package com.codefactory.devnet.shared.api;

import org.springframework.http.HttpStatus;

/**
 * Codigos de error transversales, no atribuibles a un modulo de negocio.
 *
 * <p>Los codigos propios de cada dominio viven en el {@code enum} de su modulo.
 * Aqui solo esta lo que puede ocurrir en cualquier endpoint.</p>
 */
public enum CodigoErrorComun implements CodigoError {

    VALIDACION_FALLIDA(
            HttpStatus.BAD_REQUEST,
            "Los datos enviados no son validos. Revisa el detalle de cada campo."),

    CUERPO_ILEGIBLE(
            HttpStatus.BAD_REQUEST,
            "No se pudo leer el cuerpo de la peticion. Verifica que sea JSON valido."),

    PARAMETRO_INVALIDO(
            HttpStatus.BAD_REQUEST,
            "Uno de los parametros de la peticion no tiene el formato esperado."),

    AUTH_REQUERIDA(
            HttpStatus.UNAUTHORIZED,
            "Necesitas iniciar sesion para realizar esta accion."),

    AUTH_TOKEN_INVALIDO(
            HttpStatus.UNAUTHORIZED,
            "Tu sesion expiro o el token no es valido. Vuelve a iniciar sesion."),

    ACCESO_DENEGADO(
            HttpStatus.FORBIDDEN,
            "No tienes permiso para realizar esta accion."),

    RECURSO_NO_ENCONTRADO(
            HttpStatus.NOT_FOUND,
            "El recurso solicitado no existe o no esta disponible para ti."),

    METODO_NO_PERMITIDO(
            HttpStatus.METHOD_NOT_ALLOWED,
            "Esta operacion no esta disponible sobre este recurso."),

    /** Violacion de unicidad o de integridad referencial detectada por la base. */
    CONFLICTO_DATOS(
            HttpStatus.CONFLICT,
            "La operacion entra en conflicto con datos que ya existen."),

    /** Bloqueo optimista: otro cambio gano la carrera. Ver publicacion.version. */
    CONFLICTO_CONCURRENCIA(
            HttpStatus.CONFLICT,
            "Alguien mas modifico este recurso mientras lo editabas. Recarga e intenta de nuevo."),

    LIMITE_TASA_SUPERADO(
            HttpStatus.TOO_MANY_REQUESTS,
            "Demasiados intentos. Espera un momento antes de volver a intentar."),

    ERROR_INTERNO(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Ocurrio un error inesperado. Reporta el identificador de seguimiento para que podamos revisarlo.");

    private final HttpStatus estado;
    private final String mensaje;

    CodigoErrorComun(HttpStatus estado, String mensaje) {
        this.estado = estado;
        this.mensaje = mensaje;
    }

    @Override
    public String codigo() {
        return name();
    }

    @Override
    public HttpStatus estado() {
        return estado;
    }

    @Override
    public String mensajePorDefecto() {
        return mensaje;
    }
}