package com.codefactory.devnet.project.domain;

import com.codefactory.devnet.shared.api.DetalleError;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;

import java.util.List;
import java.util.UUID;

/**
 * Reglas ABAC del modulo de proyectos: quien puede modificar un proyecto existente.
 *
 * <p>HU-08, criterio 2: "un usuario que no es el autor (ni moderador) no puede editar
 * ni eliminar el proyecto". El RBAC de {@code @PreAuthorize} solo dice si un rol
 * <i>puede editar proyectos</i>; si puede editar <i>este</i> depende de quien es su
 * autor, y eso solo se sabe tras leerlo. Por eso hace falta una regla de atributos
 * ademas del permiso (lineamiento 6.2).</p>
 *
 * <p>Vive en el dominio, como {@code PoliticaPerfil}, para que ninguna ruta futura
 * pueda saltarsela. La aplican por igual la edicion (HU-08.2) y la eliminacion
 * (HU-08.4).</p>
 */
public final class PoliticaProyecto {

    /** Permiso que convierte en moderador a quien lo tiene: MODERADOR y ADMIN. */
    public static final String PERMISO_MODERAR = "publicacion:moderar";

    private PoliticaProyecto() {
    }

    /**
     * Puede modificar el autor, y cualquiera con permiso de moderacion.
     *
     * @param autorId       autor del proyecto
     * @param actorId       usuario de la peticion; nulo si es anonima
     * @param puedeModerar  si el actor tiene {@link #PERMISO_MODERAR} en su token
     */
    public static boolean puedeModificar(UUID autorId, UUID actorId, boolean puedeModerar) {
        if (actorId == null) {
            return false;
        }
        return actorId.equals(autorId) || puedeModerar;
    }

    /**
     * {@code true} si quien modifica no es el autor: es un moderador actuando sobre
     * contenido ajeno, y eso debe quedar auditado.
     */
    public static boolean esIntervencionDeModeracion(UUID autorId, UUID actorId) {
        return actorId != null && !actorId.equals(autorId);
    }

    /**
     * Error con el que se rechaza a quien no puede modificar el proyecto.
     *
     * <p>Si el proyecto no es visible al publico, responde 404 y no 403: confirmar que
     * existe un proyecto oculto a quien no tiene derecho a verlo filtraria informacion
     * por diferencia de respuestas (ADR-005). Si es publico, su existencia ya se puede
     * comprobar con un GET, y 403 es lo honesto, igual que {@code PERFIL_AJENO}.</p>
     */
    public static ExcepcionNegocio rechazoPara(Proyecto proyecto) {
        if (!proyecto.estadoPublicacion().esVisiblePublicamente()) {
            return new ExcepcionNegocio(CodigoErrorProyecto.PROYECTO_NO_ENCONTRADO);
        }
        return new ExcepcionNegocio(
                CodigoErrorProyecto.PROYECTO_AJENO,
                CodigoErrorProyecto.PROYECTO_AJENO.mensajePorDefecto(),
                List.of(DetalleError.de("proyectoId", "no_eres_el_autor")));
    }
}
