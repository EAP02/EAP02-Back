package com.codefactory.devnet.profile.infrastructure.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditAvatarRequest(@NotBlank(message = "La URL del avatar es obligatoria.") @Size(max = 500) String avatarUrl) { }
