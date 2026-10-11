package com.codefactory.devnet.discussion.domain;

import com.codefactory.devnet.shared.api.CodigoError;

/** Codigos de error del modulo de discusiones. Prefijo {@code DISCUSION_}. */
public enum CodigoErrorDiscusion implements CodigoError {

    /**
     * HU-11: falta un campo obligatorio (por ejemplo, el cuerpo) o no cumple el formato.
     *
     * <p>400, como {@code PROYECTO_DATOS_INVALIDOS}: es un problema de forma de la
     * peticion, no una regla que dependa del estado del sistema.</p>
     */
    DISCUSION_DATOS_INVALIDOS(
            400,
            "Faltan datos obligatorios o no cumplen el formato esperado."),

    DISCUSION_TECNOLOGIA_DESCONOCIDA(
            422,
            "Alguna de las tecnologias seleccionadas no existe o no esta aprobada."),

    DISCUSION_AUTOR_SIN_PERMISO_DE_ESCRITURA(
            422,
            "Tu cuenta no puede publicar en este momento."),

    DISCUSION_NO_ENCONTRADA(
            404,
            "La discusion no existe o no esta disponible para ti.");

    private final int estado;
    private final String mensaje;

    CodigoErrorDiscusion(int estado, String mensaje) {
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
