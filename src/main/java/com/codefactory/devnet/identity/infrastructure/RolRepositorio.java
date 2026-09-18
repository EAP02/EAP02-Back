package com.codefactory.devnet.identity.infrastructure;

import com.codefactory.devnet.shared.cache.CachesDevNet;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RolRepositorio extends JpaRepository<RolEntity, Short> {

    boolean existsByCodigo(String codigo);

    /**
     * Los cuatro roles y sus permisos son datos de referencia: se leen en cada
     * autenticacion y cambian casi nunca. Es el caso de uso exacto para Caffeine.
     */
    @Cacheable(CachesDevNet.PERMISOS_POR_ROL)
    Optional<RolEntity> findByCodigo(String codigo);
}
