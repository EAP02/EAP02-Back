package com.codefactory.devnet.shared.integration;

import java.util.Optional;
import java.util.UUID;

/**
 * Contrato que <b>provee el modulo {@code profile}</b>.
 *
 * <p>Lo consumen {@code project} y {@code discussion} para validar quien puede
 * publicar, {@code interaction} para acreditar reputacion, y {@code messaging} para
 * decidir si alguien puede escribirle a un desconocido.</p>
 *
 * <p>Agrupa perfil, reputacion y seguimiento en un solo contrato porque los tres
 * viven en el mismo modulo y casi siempre se consultan juntos. Partirlos en tres
 * interfaces habria multiplicado las inyecciones sin separar nada real.</p>
 */
public interface IPerfilConsulta {

    /**
     * @param usuarioId      duena del perfil
     * @param nombreCompleto nombre para mostrar
     * @param urlAvatar      puede ser nulo
     * @param reputacion     saldo actual. Es la denormalizacion de
     *                       {@code perfil.reputacion}, reconstruible sumando
     *                       {@code evento_reputacion}.
     * @param verificado     tiene identidad de GitHub vinculada. Es requisito para
     *                       publicar y sustituye por completo a la verificacion por
     *                       correo (ADR-004).
     * @param tecnologias    cuantas tecnologias declaro. Publicar exige al menos una.
     */
    record PerfilResumen(
            UUID usuarioId,
            String nombreCompleto,
            String urlAvatar,
            int reputacion,
            boolean verificado,
            int tecnologias
    ) {
        /** Condiciones de perfil para publicar. No incluye suspension ni reputacion. */
        public boolean puedePublicar() {
            return verificado && tecnologias > 0;
        }
    }

    /** Tipos de evento que acreditan o descuentan reputacion. */
    enum EventoReputacion {
        REACCION_EN_PUBLICACION(2),
        REACCION_EN_COMENTARIO(1),
        COMENTARIO_ACEPTADO(15),
        PROYECTO_CON_REPOSITORIO(5),
        PUBLICACION_OCULTADA(-20),
        SUSPENSION_APLICADA(-50);

        private final int puntos;

        EventoReputacion(int puntos) {
            this.puntos = puntos;
        }

        public int puntos() {
            return puntos;
        }
    }

    Optional<PerfilResumen> buscar(UUID usuarioId);

    /** Saldo de reputacion. Cero si el perfil no existe. */
    int reputacionDe(UUID usuarioId);

    /**
     * Asienta un evento en el libro mayor de reputacion y actualiza el saldo.
     *
     * <p>Es la unica escritura de este contrato. El libro mayor es append-only: un
     * evento nunca se modifica ni se borra, de modo que el saldo siempre se puede
     * reconstruir y cualquier descuento queda auditable.</p>
     *
     * @param referencia identificador de lo que provoco el evento (publicacion,
     *                   comentario), para poder rastrear el origen de cada punto
     */
    void acreditar(UUID usuarioId, EventoReputacion evento, UUID referencia);

    /**
     * {@code true} si {@code seguidorId} sigue a {@code seguidoId}.
     *
     * <p>La relacion no es simetrica: seguir no implica ser seguido.</p>
     */
    boolean sigue(UUID seguidorId, UUID seguidoId);
}