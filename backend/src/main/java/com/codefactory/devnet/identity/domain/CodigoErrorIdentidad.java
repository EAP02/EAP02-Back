package com.codefactory.devnet.identity.domain;

import com.codefactory.devnet.shared.api.CodigoError;
import org.springframework.http.HttpStatus;

/** Codigos de error del modulo de identidad. Prefijo {@code AUTH_}. */
public enum CodigoErrorIdentidad implements CodigoError {

    /**
     * Credenciales incorrectas.
     *
     * <p>Un solo codigo para "el correo no existe" y "la clave no coincide": dos
     * codigos distintos permitirian enumerar cuentas registradas probando correos.</p>
     */
    AUTH_CREDENCIALES_INVALIDAS(
            HttpStatus.UNAUTHORIZED,
            "Correo o contrasena incorrectos."),

    AUTH_CUENTA_BLOQUEADA(
            HttpStatus.UNAUTHORIZED,
            "La cuenta esta bloqueada temporalmente por intentos fallidos. Intenta mas tarde."),

    AUTH_CUENTA_SUSPENDIDA(
            HttpStatus.FORBIDDEN,
            "Tu cuenta esta suspendida y no puede realizar esta accion."),

    AUTH_CUENTA_DESACTIVADA(
            HttpStatus.FORBIDDEN,
            "Esta cuenta esta desactivada."),

    /**
     * Rol con MFA obligatorio que aun no inscribio el segundo factor.
     *
     * <p>Lineamiento 3.4: MFA para accesos administrativos o sensibles. El rol
     * MODERADOR o ADMIN existe, pero no puede ejercer sus permisos hasta inscribirlo.</p>
     */
    AUTH_MFA_REQUERIDO(
            HttpStatus.FORBIDDEN,
            "Este rol exige un segundo factor de autenticacion. Inscribelo antes de continuar."),

    AUTH_USUARIO_NO_ENCONTRADO(
            HttpStatus.NOT_FOUND,
            "El usuario no existe.");

    private final HttpStatus estado;
    private final String mensaje;

    CodigoErrorIdentidad(HttpStatus estado, String mensaje) {
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