package com.codefactory.devnet.profile.api;

import java.util.Optional;
import java.util.UUID;

/** Servicio publico de lectura ofrecido directamente por el modulo profile. */
public interface PerfilConsulta {

    record PerfilResumen(
            UUID usuarioId,
            String nombreCompleto,
            String urlAvatar,
            int reputacion,
            boolean verificado,
            int tecnologias
    ) { }

    Optional<PerfilResumen> buscar(UUID usuarioId);
}
