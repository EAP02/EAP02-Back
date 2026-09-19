package com.codefactory.devnet.identity.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Credencial de larga duracion que permite obtener un token de acceso nuevo sin volver
 * a pedir la contrasena.
 *
 * <p>El JWT de acceso dura 15 minutos y no se puede revocar: una vez firmado, vale
 * hasta que expira. La revocacion opera sobre este objeto, y esa es toda su razon de
 * ser (ADR-004).</p>
 *
 * <h2>Familia y deteccion de reuso</h2>
 *
 * <p>Cada inicio de sesion abre una <b>familia</b>. Al usar un refresco se marca
 * {@code consumidoEn} y se emite un sucesor <b>de la misma familia</b>, enlazado por
 * {@code reemplazadoPor}. Un refresco se usa exactamente una vez.</p>
 *
 * <p>De ahi sale la deteccion de reuso: si llega uno ya consumido, o bien el titular
 * repitio una peticion, o bien alguien copio la cookie. No hay forma de distinguirlos,
 * asi que se asume lo peor y <b>se revoca la familia entera</b>. El legitimo y el
 * ladron pierden el acceso a la vez; el legitimo vuelve a entrar con su contrasena, el
 * ladron no puede.</p>
 *
 * <p><b>Este agregado no lleva el valor del token ni su hash.</b> No los necesita para
 * decidir nada, y lo que no se tiene no se puede filtrar por un log ni por un
 * {@code toString()} descuidado. El hash vive solo en la fila y en el puerto.</p>
 */
public record TokenRefresco(
        UUID id,
        UUID usuarioId,
        UUID familia,
        Instant emitidoEn,
        Instant expiraEn,
        Instant consumidoEn,
        Instant revocadoEn,
        UUID reemplazadoPor
) {

    /** Ya se uso. Verlo de nuevo es la senal de reuso. */
    public boolean estaConsumido() {
        return consumidoEn != null;
    }

    /** Anulado antes de expirar: cierre de sesion, o su familia cayo por un reuso. */
    public boolean estaRevocado() {
        return revocadoEn != null;
    }

    /**
     * Vencido por tiempo.
     *
     * <p>El instante exacto de expiracion cuenta como vencido: {@code expiraEn} es el
     * primer momento en que ya no vale, no el ultimo en que vale.</p>
     */
    public boolean haExpirado(Instant ahora) {
        return !expiraEn.isAfter(ahora);
    }

    /**
     * Las tres condiciones a la vez.
     *
     * <p>El caso de uso no la llama: comprueba cada causa por separado para poder
     * distinguirlas en la respuesta y en la auditoria —un reuso no es lo mismo que una
     * sesion caducada—. Existe para las pruebas y para quien lea el agregado y quiera
     * la regla completa en una linea.</p>
     */
    public boolean esUtilizable(Instant ahora) {
        return !estaConsumido() && !estaRevocado() && !haExpirado(ahora);
    }
}