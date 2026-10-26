package com.codefactory.devnet.profile.domain;

import com.codefactory.devnet.shared.api.CodigoError;

public enum CodigoErrorPerfil implements CodigoError {
    PERFIL_INVALIDO(400, "Los datos del perfil no son validos."),

    /** Intento de editar el perfil de otra persona. Ver {@link PoliticaPerfil}. */
    PERFIL_AJENO(403, "Solo puedes editar tu propio perfil."),

    PERFIL_NO_ENCONTRADO(404, "Perfil no encontrado."),

    /**
     * Alguna tecnologia declarada no esta en el catalogo o no esta aprobada.
     *
     * <p>422 y no 400: la peticion esta bien formada, lo que falla es una regla que
     * depende del estado del catalogo.</p>
     */
    PERFIL_TECNOLOGIA_DESCONOCIDA(422,
            "Alguna de las tecnologias seleccionadas no existe o no esta aprobada."),

    /**
     * HU-15, criterio 3: un usuario no puede seguirse a si mismo.
     *
     * <p>422 y no 400: la peticion esta bien formada, lo que falla es una regla de
     * negocio (el seguido resulta ser quien pide).</p>
     */
    SEGUIMIENTO_PROPIO(422, "No puedes seguirte a ti mismo."),

    /** HU-15: la cuenta no puede seguir a nadie (no esta activa). */
    SEGUIMIENTO_SIN_PERMISO_DE_ESCRITURA(422, "Tu cuenta no puede seguir usuarios en este momento.");

    private final Definicion definicion;

    CodigoErrorPerfil(int estado, String mensaje) {
        this.definicion = new Definicion(estado, mensaje);
    }

    @Override
    public Definicion definicion() {
        return definicion;
    }
}
