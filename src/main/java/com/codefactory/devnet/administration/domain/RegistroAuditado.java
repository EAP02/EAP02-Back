package com.codefactory.devnet.administration.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Una fila del registro de auditoria, tal como la lee un administrador.
 *
 * @param operacion  que paso (INSERT, DELETE, INICIO_SESION_FALLIDO...)
 * @param tabla      sobre que objeto
 * @param registroId clave del objeto; en un inicio de sesion fallido, el correo intentado
 * @param actorId    quien. Nulo si la peticion era anonima o el cambio vino de consola
 * @param usuarioBd  rol de base de datos que origino el evento (V10); nulo si es anterior
 */
public record RegistroAuditado(
        long id,
        Instant creadoEn,
        String operacion,
        String tabla,
        String registroId,
        UUID actorId,
        String datosAntes,
        String datosDespues,
        String traceId,
        String ip,
        String usuarioBd
) {
}
