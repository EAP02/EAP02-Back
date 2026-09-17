package com.codefactory.devnet.project.domain;

import com.codefactory.devnet.shared.api.CodigoError;


/** Codigos de error del modulo de proyectos. Prefijo {@code PROYECTO_}. */
public enum CodigoErrorProyecto implements CodigoError {

    /**
     * HU-07, criterio 2: faltan titulo o descripcion.
     *
     * <p>400 y no 422: es un problema de forma de la peticion, no una regla de
     * negocio que dependa del estado del sistema.</p>
     */
    PROYECTO_DATOS_INVALIDOS(
            400,
            "Faltan datos obligatorios o no cumplen el formato esperado."),

    /**
     * HU-07, criterio 3: intentar publicar a nombre de otro.
     *
     * <p>403 y no 404 porque el recurso que se intenta crear es del propio actor:
     * no hay existencia ajena que ocultar, y decirlo claro evita que el cliente
     * reintente pensando que fue un fallo transitorio.</p>
     */
    PROYECTO_AUTOR_AJENO(
            403,
            "Solo puedes publicar proyectos a tu propio nombre."),

    /**
     * HU-07, criterio 1: el proyecto queda asociado al perfil del autor, asi que el
     * perfil tiene que existir.
     */
    PROYECTO_PERFIL_INEXISTENTE(
            422,
            "Necesitas completar tu perfil antes de publicar un proyecto."),

    PROYECTO_AUTOR_SIN_PERMISO_DE_ESCRITURA(
            422,
            "Tu cuenta no puede publicar en este momento."),

    PROYECTO_TECNOLOGIA_DESCONOCIDA(
            422,
            "Alguna de las tecnologias seleccionadas no existe o no esta aprobada."),

    PROYECTO_REPOSITORIO_DUPLICADO(
            409,
            "Ese repositorio ya esta enlazado a otro proyecto."),

    PROYECTO_NO_ENCONTRADO(
            404,
            "El proyecto no existe o no esta disponible para ti.");

    private final int estado;
    private final String mensaje;

    CodigoErrorProyecto(int estado, String mensaje) {
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