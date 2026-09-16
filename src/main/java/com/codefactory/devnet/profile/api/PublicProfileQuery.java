package com.codefactory.devnet.profile.api;

import com.codefactory.devnet.profile.api.dto.PublicProfileResponse;

/** Contrato publico de Profile: otros modulos pueden consultar, pero no editar. */
public interface PublicProfileQuery {
    PublicProfileResponse getPublicProfile(Long profileId);
}
