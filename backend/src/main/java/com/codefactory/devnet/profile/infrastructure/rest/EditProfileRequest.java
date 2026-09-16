package com.codefactory.devnet.profile.infrastructure.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record EditProfileRequest(
        @NotBlank(message = "El nombre visible es obligatorio.") @Size(max = 80) String displayName,
        @NotBlank(message = "La biografia es obligatoria.") @Size(max = 1000) String bio,
        @NotEmpty(message = "Debes registrar al menos una tecnologia o lenguaje.") List<@NotBlank(message = "La tecnologia no puede estar vacia.") @Size(max = 80) String> technologies,
        @Size(max = 300) String githubUrl,
        @Size(max = 300) String linkedinUrl) { }
