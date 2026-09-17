package com.codefactory.devnet.profile.domain;

import com.codefactory.devnet.shared.api.CodigoError;

public enum CodigoErrorPerfil implements CodigoError {
    PERFIL_INVALIDO(400, "Los datos del perfil no son validos."),
    PERFIL_NO_ENCONTRADO(404, "Perfil no encontrado.");

    private final int estado;
    private final String mensaje;

    CodigoErrorPerfil(int estado, String mensaje) {
        this.estado = estado;
        this.mensaje = mensaje;
    }

    public String codigo() { return name(); }
    public int estadoHttp() { return estado; }
    public String mensajePorDefecto() { return mensaje; }
}
