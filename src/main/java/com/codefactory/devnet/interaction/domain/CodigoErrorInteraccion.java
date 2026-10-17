package com.codefactory.devnet.interaction.domain;

import com.codefactory.devnet.shared.api.CodigoError;

/** Codigos de error del modulo de interaccion. Prefijo {@code COMENTARIO_}. */
public enum CodigoErrorInteraccion implements CodigoError {

    /**
     * HU-13: un comentario vacio se rechaza.
     *
     * <p>400 y no 422: es un problema de forma de la peticion, no una regla que dependa
     * del estado del sistema.</p>
     */
    COMENTARIO_VACIO(
            400,
            "El comentario no puede estar vacio."),

    COMENTARIO_DEMASIADO_LARGO(
            400,
            "El comentario supera la longitud maxima permitida."),

    COMENTARIO_AUTOR_SIN_PERMISO_DE_ESCRITURA(
            422,
            "Tu cuenta no puede comentar en este momento."),

    /** El comentario no existe, o ya fue eliminado y por eso deja de mostrarse. */
    COMENTARIO_NO_ENCONTRADO(
            404,
            "El comentario no existe o no esta disponible para ti."),

    /**
     * HU-13: eliminar el comentario de otro sin ser administrador.
     *
     * <p>403 y no 404: el comentario es publico, su existencia ya se puede comprobar
     * con un GET, y 403 es lo honesto (mismo criterio que {@code PROYECTO_AJENO}).</p>
     */
    COMENTARIO_AJENO(
            403,
            "Solo el autor del comentario o un administrador puede eliminarlo.");

    private final int estado;
    private final String mensaje;

    CodigoErrorInteraccion(int estado, String mensaje) {
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
