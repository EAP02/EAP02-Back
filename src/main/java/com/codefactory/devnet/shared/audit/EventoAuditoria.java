package com.codefactory.devnet.shared.audit;

import java.util.UUID;

/**
 * Un hecho que debe quedar registrado.
 *
 * @param tabla     tabla afectada, o el modulo cuando es un evento de seguridad
 * @param operacion ver {@link Operacion}
 * @param registroId clave del registro; en eventos de seguridad, la ruta que se
 *                   intento alcanzar
 * @param actorId   quien lo provoco. Nulo si la peticion era anonima.
 * @param datosAntes  JSON del estado previo, o null
 * @param datosDespues JSON del estado posterior, o null
 * @param ip        origen de la peticion. Dato personal: ver la clasificacion en
 *                  {@code docs/bd/03-diccionario-datos.md}.
 */
public record EventoAuditoria(
        String tabla,
        Operacion operacion,
        String registroId,
        UUID actorId,
        String datosAntes,
        String datosDespues,
        String ip
) {

    /** Debe coincidir con la restriccion ck_auditoria_operacion (V5). */
    public enum Operacion {
        INSERT, UPDATE, DELETE,
        ACCESO_DENEGADO, INICIO_SESION, INICIO_SESION_FALLIDO, CUENTA_BLOQUEADA,
        REFRESCO_ROTADO, REFRESCO_REUSADO, CIERRE_SESION
    }

    /**
     * Intento de acceso rechazado por falta de permiso.
     *
     * <p>HU-06, criterio 1. Se registra el recurso y el actor, nunca el token ni
     * las credenciales (lineamiento 6.2).</p>
     */
    public static EventoAuditoria accesoDenegado(String ruta, UUID actorId, String ip) {
        return new EventoAuditoria("seguridad", Operacion.ACCESO_DENEGADO, ruta, actorId, null, null, ip);
    }

    public static EventoAuditoria inicioSesion(UUID usuarioId, String ip) {
        return new EventoAuditoria("usuario", Operacion.INICIO_SESION,
                usuarioId.toString(), usuarioId, null, null, ip);
    }

    /**
     * Intento de inicio de sesion fallido.
     *
     * <p>{@code actorId} va nulo a proposito aunque se conozca el usuario: si el
     * correo no existe, no hay identidad que registrar, y distinguir ambos casos en
     * la tabla permitiria enumerar cuentas leyendo la auditoria.</p>
     */
    public static EventoAuditoria inicioSesionFallido(String correo, String ip) {
        return new EventoAuditoria("usuario", Operacion.INICIO_SESION_FALLIDO, correo, null, null, null, ip);
    }

    public static EventoAuditoria cuentaBloqueada(UUID usuarioId, String ip) {
        return new EventoAuditoria("usuario", Operacion.CUENTA_BLOQUEADA,
                usuarioId.toString(), usuarioId, null, null, ip);
    }

    /**
     * Renovacion normal del token de acceso.
     *
     * <p>Es el camino feliz y se registra igualmente: la secuencia de rotaciones de una
     * familia es lo que permite reconstruir que paso cuando alguna resulta ser un
     * reuso.</p>
     *
     * <p>{@code registroId} lleva la familia y no el identificador del token: los tokens
     * son efimeros por diseno y lo que interesa seguir es la sesion.</p>
     */
    public static EventoAuditoria refrescoRotado(UUID usuarioId, UUID familia, String ip) {
        return new EventoAuditoria("token_refresco", Operacion.REFRESCO_ROTADO,
                familia.toString(), usuarioId, null, null, ip);
    }

    /**
     * Llego un token de refresco ya consumido y se revoco la familia entera.
     *
     * <p>El evento de seguridad mas relevante que emite el sistema. O el titular repitio
     * una peticion, o alguien copio su cookie; no hay manera de distinguirlo, asi que se
     * asume lo segundo.</p>
     */
    public static EventoAuditoria refrescoReutilizado(UUID usuarioId, UUID familia, String ip) {
        return new EventoAuditoria("token_refresco", Operacion.REFRESCO_REUSADO,
                familia.toString(), usuarioId, null, null, ip);
    }

    public static EventoAuditoria cierreSesion(UUID usuarioId, UUID familia, String ip) {
        return new EventoAuditoria("token_refresco", Operacion.CIERRE_SESION,
                familia.toString(), usuarioId, null, null, ip);
    }
}