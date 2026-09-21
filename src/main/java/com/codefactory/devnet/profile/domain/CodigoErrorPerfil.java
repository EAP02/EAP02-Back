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
            "Alguna de las tecnologias seleccionadas no existe o no esta aprobada.");

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
