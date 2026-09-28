package com.codefactory.eap02.auth.dto;

import com.codefactory.eap02.auth.domain.Usuario;
import java.time.Instant;
import java.util.UUID;

public record UsuarioResponse(UUID id, String email, Instant createdAt) {
    public static UsuarioResponse from(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getEmail(), u.getCreatedAt());
    }
}
