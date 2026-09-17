package com.codefactory.devnet.shared.api;



/**
 * Codigos de error transversales, no atribuibles a un modulo de negocio.
 *
 * <p>Los codigos propios de cada dominio viven en el {@code enum} de su modulo.
 * Aqui solo esta lo que puede ocurrir en cualquier endpoint.</p>
 */
public enum CodigoErrorComun implements CodigoError {

    VALIDACION_FALLIDA(
            400,
            "Los datos enviados no son validos. Revisa el detalle de cada campo."),

    CUERPO_ILEGIBLE(
            400,
            "No se pudo leer el cuerpo de la peticion. Verifica que sea JSON valido."),

    PARAMETRO_INVALIDO(
            400,
            "Uno de los parametros de la peticion no tiene el formato esperado."),

    AUTH_REQUERIDA(
            401,
            "Necesitas iniciar sesion para realizar esta accion."),

    AUTH_TOKEN_INVALIDO(
            401,
            "Tu sesion expiro o el token no es valido. Vuelve a iniciar sesion."),

    ACCESO_DENEGADO(
            403,
            "No tienes permiso para realizar esta accion."),

    RECURSO_NO_ENCONTRADO(
            404,
            "El recurso solicitado no existe o no esta disponible para ti."),

    METODO_NO_PERMITIDO(
            405,
            "Esta operacion no esta disponible sobre este recurso."),

    /** Violacion de unicidad o de integridad referencial detectada por la base. */
    CONFLICTO_DATOS(
            409,
            "La operacion entra en conflicto con datos que ya existen."),

    /** Bloqueo optimista: otro cambio gano la carrera. Ver publicacion.version. */
    CONFLICTO_CONCURRENCIA(
            409,
            "Alguien mas modifico este recurso mientras lo editabas. Recarga e intenta de nuevo."),

    LIMITE_TASA_SUPERADO(
            429,
            "Demasiados intentos. Espera un momento antes de volver a intentar."),

    ERROR_INTERNO(
            500,
            "Ocurrio un error inesperado. Reporta el identificador de seguimiento para que podamos revisarlo.");

    private final int estado;
    private final String mensaje;

    CodigoErrorComun(int estado, String mensaje) {
        this.estado = estado;
        this.mensaje = mensaje;
    }

    @Override
    public String codigo() {
        return name();
    }

    @Override
    public int estadoHttp() {
        return estado;
    }

    @Override
    public String mensajePorDefecto() {
        return mensaje;
    }
}