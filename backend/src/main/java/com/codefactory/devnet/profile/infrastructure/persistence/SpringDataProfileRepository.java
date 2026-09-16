package com.codefactory.devnet.profile.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataProfileRepository extends JpaRepository<ProfileJpaEntity, Long> { }
