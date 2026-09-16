package com.codefactory.eap02.auth.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "login_attempts")
@Getter @Setter
public class LoginAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private boolean successful;

    @Column(nullable = false)
    private String reason; // "SUCCESS", "INVALID_CREDENTIALS", "ACCOUNT_LOCKED"

    @Column(nullable = false)
    private Instant attemptedAt = Instant.now();
}
