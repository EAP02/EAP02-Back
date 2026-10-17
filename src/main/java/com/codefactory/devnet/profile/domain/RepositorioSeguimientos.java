package com.codefactory.devnet.profile.domain;

import java.util.List;
import java.util.UUID;

/** Puerto de persistencia del seguimiento entre usuarios (HU-15). */
public interface RepositorioSeguimientos {

    /**
     * Deja a lo mas UN seguimiento de {@code seguidor} hacia {@code seguido}. Si ya
     * existia no hace nada. Atomico: dos peticiones simultaneas no duplican.
     *
     * @return {@code true} si lo creo, {@code false} si ya existia
     */
    boolean seguir(Seguimiento seguimiento);

    /** @return {@code true} si habia un seguimiento y lo borro */
    boolean dejarDeSeguir(UUID seguidorId, UUID seguidoId);

    /** A quienes sigue {@code usuarioId}, del mas reciente al mas antiguo. */
    List<Seguimiento> seguidosPor(UUID usuarioId);

    /** Quienes siguen a {@code usuarioId}, del mas reciente al mas antiguo. */
    List<Seguimiento> seguidoresDe(UUID usuarioId);
}
