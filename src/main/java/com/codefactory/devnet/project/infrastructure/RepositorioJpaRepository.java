package com.codefactory.devnet.project.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RepositorioJpaRepository extends JpaRepository<RepositorioEntity, UUID> {

    /** Respalda PROYECTO_REPOSITORIO_DUPLICADO con un 409 legible en vez del error de la restriccion UNIQUE. */
    boolean existsByUrlIgnoreCase(String url);

    List<RepositorioEntity> findByPublicacionProyectoId(UUID publicacionProyectoId);

    /**
     * HU-08: la misma comprobacion que {@link #existsByUrlIgnoreCase}, excluyendo los
     * repositorios del proyecto que se edita. Conservar el propio enlace no es un
     * duplicado.
     */
    boolean existsByUrlIgnoreCaseAndPublicacionProyectoIdNot(String url, UUID publicacionProyectoId);
}