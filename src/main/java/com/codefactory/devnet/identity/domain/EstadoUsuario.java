package com.codefactory.devnet.identity.domain;

/**
 * Estados de una cuenta. Debe coincidir con ck_usuario_estado en V1__baseline.sql.
 *
 * <p>No existe PENDIENTE_VERIFICACION: la verificacion se resuelve vinculando
 * GitHub, lo que elimina la dependencia de un proveedor de correo (ADR-004).</p>
 */
public enum EstadoUsuario {

    /** Puede operar con normalidad. */
    ACTIVO,

    /** Sancionada temporalmente. Puede leer, no puede publicar ni interactuar. */
    SUSPENDIDO,

    /** Cerrada por su titular o por administracion. No puede autenticarse. */
    DESACTIVADO;

    public boolean puedeAutenticarse() {
        return this == ACTIVO || this == SUSPENDIDO;
    }

    public boolean puedeEscribir() {
        return this == ACTIVO;
    }
}