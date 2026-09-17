package com.codefactory.devnet.identity.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PermisoRepositorio extends JpaRepository<PermisoEntity, Short> {
    Optional<PermisoEntity> findByCodigo(String codigo);
}
