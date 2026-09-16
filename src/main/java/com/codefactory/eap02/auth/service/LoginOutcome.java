package com.codefactory.eap02.auth.service;

import com.codefactory.eap02.auth.domain.Usuario;

public record LoginOutcome(boolean success, boolean locked, Long retryAfterSeconds, String token, Usuario usuario) {
    public static LoginOutcome success(String token, Usuario u) {
        return new LoginOutcome(true, false, null, token, u);
    }
    public static LoginOutcome invalidCredentials() {
        return new LoginOutcome(false, false, null, null, null);
    }
    public static LoginOutcome locked(long secondsLeft) {
        return new LoginOutcome(false, true, secondsLeft, null, null);
    }
}
