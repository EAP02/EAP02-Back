package com.codefactory.devnet.identity.domain;

import com.codefactory.devnet.shared.api.CodigoError;


/** Codigos de error del modulo de identidad. Prefijo {@code AUTH_}. */
public enum CodigoErrorIdentidad implements CodigoError {

    /**
     * Credenciales incorrectas.
     *
     * <p>Un solo codigo para "el correo no existe" y "la clave no coincide": dos
     * codigos distintos permitirian enumerar cuentas registradas probando correos.</p>
     */
    AUTH_CREDENCIALES_INVALIDAS(
            401,
            "Correo o contrasena incorrectos."),

    AUTH_CUENTA_BLOQUEADA(
            401,
            "La cuenta esta bloqueada temporalmente por intentos fallidos. Intenta mas tarde."),

    /**
     * No llego la cookie de refresco.
     *
     * <p>Los cinco codigos {@code AUTH_REFRESCO_*} devuelven 401 y le dicen al usuario
     * exactamente lo mismo, porque lo que tiene que hacer es lo mismo en los cinco
     * casos: volver a iniciar sesion. La distincion existe para el log y la auditoria,
     * no para el cliente.</p>
     *
     * <p>Podrian fusionarse en uno solo. No se hace porque {@code AUTH_REFRESCO_REUSADO}
     * es la senal de que alguien pudo copiar una cookie, y diluirla dentro de un codigo
     * generico anularia el valor de haber implementado la deteccion de reuso.</p>
     */
    AUTH_REFRESCO_AUSENTE(
            401,
            "No hay sesion que renovar. Inicia sesion de nuevo."),

    /** El token no existe en la tabla: falsificado, o de una sesion ya purgada. */
    AUTH_REFRESCO_INVALIDO(
            401,
            "La sesion no se puede renovar. Inicia sesion de nuevo."),

    AUTH_REFRESCO_EXPIRADO(
            401,
            "La sesion expiro. Inicia sesion de nuevo."),

    /** Anulado por un cierre de sesion, o por la caida de su familia. */
    AUTH_REFRESCO_REVOCADO(
            401,
            "La sesion fue cerrada. Inicia sesion de nuevo."),

    /**
     * Llego un refresco ya consumido: la familia entera queda revocada.
     *
     * <p>Este es el codigo que hay que vigilar en los tableros. Si aparece, alguien uso
     * dos veces la misma credencial y no se puede saber si fue el titular repitiendo una
     * peticion o un tercero con la cookie copiada.</p>
     */
    AUTH_REFRESCO_REUSADO(
            401,
            "La sesion se cerro por seguridad. Inicia sesion de nuevo."),

    AUTH_CUENTA_SUSPENDIDA(
            403,
            "Tu cuenta esta suspendida y no puede realizar esta accion."),

    AUTH_CUENTA_DESACTIVADA(
            403,
            "Esta cuenta esta desactivada."),

    /**
     * Rol con MFA obligatorio que aun no inscribio el segundo factor.
     *
     * <p>Lineamiento 3.4: MFA para accesos administrativos o sensibles. El rol
     * MODERADOR o ADMIN existe, pero no puede ejercer sus permisos hasta inscribirlo.</p>
     */
    AUTH_MFA_REQUERIDO(
            403,
            "Este rol exige un segundo factor de autenticacion. Inscribelo antes de continuar."),

    AUTH_USUARIO_NO_ENCONTRADO(
            404,
            "El usuario no existe."),

    AUTH_CORREO_YA_REGISTRADO(
            409,
            "Ya existe una cuenta con ese correo."),

    AUTH_NOMBRE_USUARIO_YA_REGISTRADO(
            409,
            "Ese nombre de usuario ya esta en uso.");

    private final int estado;
    private final String mensaje;

    CodigoErrorIdentidad(int estado, String mensaje) {
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
