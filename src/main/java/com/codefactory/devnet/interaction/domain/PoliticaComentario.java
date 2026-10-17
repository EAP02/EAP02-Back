package com.codefactory.devnet.interaction.domain;

import com.codefactory.devnet.shared.api.ExcepcionNegocio;

import java.util.UUID;

/**
 * Regla ABAC de los comentarios: quien puede eliminar uno.
 *
 * <p>HU-13, criterios 3 y 4: el autor elimina el suyo y los administradores pueden
 * eliminar cualquiera. El RBAC de {@code @PreAuthorize} solo dice si un rol puede
 * eliminar comentarios; si puede eliminar <i>este</i> depende de quien lo escribio, y
 * eso solo se sabe tras leerlo (lineamiento 6.2). Vive en el dominio, como
 * {@code PoliticaProyecto}.</p>
 */
public final class PoliticaComentario {

    /** Permiso que concede V6: solo el rol ADMIN. */
    public static final String PERMISO_ELIMINAR_AJENOS = "comentario:eliminar-ajeno";

    private PoliticaComentario() {
    }

    /**
     * @param autorId               autor del comentario
     * @param actorId               usuario de la peticion; nulo si es anonima
     * @param puedeEliminarAjenos   si el actor tiene {@link #PERMISO_ELIMINAR_AJENOS}
     */
    public static boolean puedeEliminar(UUID autorId, UUID actorId, boolean puedeEliminarAjenos) {
        if (actorId == null) {
            return false;
        }
        return actorId.equals(autorId) || puedeEliminarAjenos;
    }

    /** {@code true} si quien elimina no es el autor: un administrador sobre contenido ajeno. */
    public static boolean esIntervencionDeAdministrador(UUID autorId, UUID actorId) {
        return actorId != null && !actorId.equals(autorId);
    }

    public static ExcepcionNegocio rechazo() {
        return new ExcepcionNegocio(CodigoErrorInteraccion.COMENTARIO_AJENO);
    }
}
