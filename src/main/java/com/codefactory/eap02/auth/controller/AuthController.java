package com.codefactory.eap02.auth.controller;

import com.codefactory.eap02.auth.dto.*;
import com.codefactory.eap02.auth.service.AuthService;
import com.codefactory.eap02.auth.service.EmailAlreadyExistsException;
import com.codefactory.eap02.auth.service.LoginOutcome;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<Object> register(@Valid @RequestBody RegisterRequest req) {
        try {
            var u = authService.register(req.email(), req.password());
            return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioResponse.from(u));
        } catch (EmailAlreadyExistsException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("EMAIL_ALREADY_EXISTS", e.getMessage(), null));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Object> login(@Valid @RequestBody LoginRequest req) {
        LoginOutcome outcome = authService.login(req.email(), req.password());

        if (outcome.locked()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ErrorResponse("ACCOUNT_LOCKED", "Cuenta bloqueada temporalmente", outcome.retryAfterSeconds()));
        }
        if (!outcome.success()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("INVALID_CREDENTIALS", "Credenciales inválidas", null));
        }
        return ResponseEntity.ok(new AuthResponse(outcome.token(), UsuarioResponse.from(outcome.usuario())));
    }
}