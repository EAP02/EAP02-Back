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
            "El usuario no existe.");

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