package com.codefactory.eap02.auth.repository;

import com.codefactory.eap02.auth.domain.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {
}
