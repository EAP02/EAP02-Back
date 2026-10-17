package com.codefactory.devnet.profile.domain;

import com.codefactory.devnet.shared.api.ExcepcionNegocio;

import java.time.Instant;
import java.util.UUID;

/**
 * HU-15: "seguidor sigue a seguido". Relacion NO simetrica: que A siga a B no implica
 * que B siga a A.
 *
 * @param seguidorId quien sigue
 * @param seguidoId  a quien sigue
 * @param creadoEn   desde cuando
 */
public record Seguimiento(UUID seguidorId, UUID seguidoId, Instant creadoEn) {

    /**
     * Criterio 3: un usuario no puede seguirse a si mismo. La base lo impide tambien
     * ({@code ck_seguimiento_no_reflexivo}); esta es la regla que la API aplica primero,
     * para responder con un error claro y no con una violacion de CHECK.
     */
    public static Seguimiento nuevo(UUID seguidorId, UUID seguidoId) {
        if (seguidorId.equals(seguidoId)) {
            throw new ExcepcionNegocio(CodigoErrorPerfil.SEGUIMIENTO_PROPIO);
        }
        return new Seguimiento(seguidorId, seguidoId, Instant.now());
    }
}
