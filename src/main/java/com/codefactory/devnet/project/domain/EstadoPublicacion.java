package com.codefactory.devnet.project.domain;

/**
 * Visibilidad de una publicacion. Debe coincidir con {@code ck_publicacion_estado} en
 * {@code V1__baseline.sql}.
 *
 * <p>No confundir con {@link EstadoProyecto}: aquel describe la madurez del trabajo
 * (idea, en desarrollo...), este decide quien puede verla y que se puede hacer con
 * ella. La maquina de estados completa esta en
 * {@code docs/arquitectura/proceso-principal.md}.</p>
 */
public enum EstadoPublicacion {

    /** Solo la ve su autor. Hoy ningun caso de uso crea borradores. */
    BORRADOR,

    /** Visible al publico, editable por su autor. */
    PUBLICADO,

    /** Pertenecia al flujo de reportes, que quedo fuera del alcance. Sin uso. */
    EN_REVISION,

    /** Retirada por moderacion (R2). Ni visible ni editable. */
    OCULTO,

    /** Retirada definitivamente por su autor (R3). Terminal. */
    ARCHIVADO;

    /**
     * HU-08: solo una publicacion visible admite cambios de contenido.
     *
     * <p>Editar algo oculto permitiria al autor cambiar el contenido que motivo la
     * ocultacion y borrar asi la evidencia; editar algo archivado contradiria que
     * archivar es terminal.</p>
     */
    public boolean admiteEdicion() {
        return this == PUBLICADO;
    }

    /** Lo que cualquier visitante puede ver, sin sesion ni permisos. */
    public boolean esVisiblePublicamente() {
        return this == PUBLICADO;
    }
}
