package com.codefactory.eap02.auth.service;

import com.codefactory.eap02.auth.domain.LoginAttempt;
import com.codefactory.eap02.auth.domain.Usuario;
import com.codefactory.eap02.auth.repository.LoginAttemptRepository;
import com.codefactory.eap02.auth.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository repo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptRepository loginAttemptRepo;   // ← campo nuevo, junto a los demás

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    public Usuario register(String email, String rawPassword) {
        if (repo.existsByEmail(email)) {
            throw new EmailAlreadyExistsException("Ya existe una cuenta con ese correo");
        }
        Usuario u = new Usuario();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(rawPassword));
        return repo.save(u);
    }

    public LoginOutcome login(String email, String rawPassword) {
        Usuario u = repo.findByEmail(email).orElse(null);

        if (u == null) {
            registrarIntento(email, false, "INVALID_CREDENTIALS");
            return LoginOutcome.invalidCredentials();
        }

        if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(Instant.now())) {
            long secondsLeft = Duration.between(Instant.now(), u.getLockedUntil()).getSeconds();
            registrarIntento(email, false, "ACCOUNT_LOCKED");
            return LoginOutcome.locked(secondsLeft);
        }

        if (!passwordEncoder.matches(rawPassword, u.getPasswordHash())) {
            registrarIntentoFallido(u);
            registrarIntento(email, false, "INVALID_CREDENTIALS");
            return LoginOutcome.invalidCredentials();
        }

        u.setFailedLoginAttempts(0);
        u.setLockedUntil(null);
        repo.save(u);
        registrarIntento(email, true, "SUCCESS");
        return LoginOutcome.success(jwtService.generateToken(u), u);
    }

    private void registrarIntentoFallido(Usuario u) {
        u.setFailedLoginAttempts(u.getFailedLoginAttempts() + 1);
        if (u.getFailedLoginAttempts() >= MAX_ATTEMPTS) {
            u.setLockedUntil(Instant.now().plus(LOCK_DURATION));
        }
        repo.save(u);
    }

    private void registrarIntento(String email, boolean successful, String reason) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.setEmail(email);
        attempt.setSuccessful(successful);
        attempt.setReason(reason);
        loginAttemptRepo.save(attempt);
    }
}