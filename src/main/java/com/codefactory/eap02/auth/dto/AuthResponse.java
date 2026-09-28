package com.codefactory.eap02.auth.dto;

public record AuthResponse(String token, UsuarioResponse usuario) {}