package com.codefactory.devnet.project.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PublicacionProyectoJpaRepository
        extends JpaRepository<PublicacionProyectoEntity, UUID> {
}