package com.codefactory.devnet.messaging.domain;

import com.codefactory.devnet.shared.api.CodigoError;

public enum CodigoErrorMensajeria implements CodigoError {
    MENSAJE_CONTENIDO_INVALIDO(400, "El mensaje debe contener entre 1 y 4000 caracteres no vacios."),
    MENSAJE_DESTINATARIO_INVALIDO(400, "Debes indicar un destinatario."),
    MENSAJE_PROPIO(422, "No puedes enviarte un mensaje a ti mismo."),
    MENSAJE_REMITENTE_INACTIVO(422, "Tu cuenta no puede enviar mensajes en este momento.");

    private final int estado;
    private final String mensaje;

    CodigoErrorMensajeria(int estado, String mensaje) {
        this.estado = estado;
        this.mensaje = mensaje;
    }

    public String codigo() { return name(); }
    public int estadoHttp() { return estado; }
    public String mensajePorDefecto() { return mensaje; }
}
