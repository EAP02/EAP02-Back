package com.codefactory.devnet.profile.infrastructure.rest;

import com.codefactory.devnet.profile.domain.Profile;
import java.util.List;

/** Respuesta del perfil propio. Tampoco expone correo: eso es responsabilidad de Identity. */
public record ProfileResponse(Long id, String displayName, String bio, String avatarUrl, List<String> technologies,
                              String githubUrl, String linkedinUrl) {
    public static ProfileResponse from(Profile profile) {
        return new ProfileResponse(profile.getId(), profile.getDisplayName(), profile.getBio(), profile.getAvatarUrl(),
                profile.getTechnologies(), profile.getGithubUrl(), profile.getLinkedinUrl());
    }
}
