package com.codefactory.devnet.project.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;

public interface TecnologiaJpaRepository extends JpaRepository<TecnologiaEntity, Short> {

    /**
     * Cuantas de las solicitadas existen <b>y</b> estan aprobadas.
     *
     * <p>Comparar este numero con el tamano del conjunto pedido detecta de una sola
     * consulta tanto identificadores inexistentes como tecnologias aun sin aprobar.</p>
     */
    long countByIdInAndAprobadaTrue(Set<Short> ids);

    List<TecnologiaEntity> findByIdInAndAprobadaTrue(Set<Short> ids);
}