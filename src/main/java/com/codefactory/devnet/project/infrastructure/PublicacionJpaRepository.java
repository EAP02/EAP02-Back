package com.codefactory.devnet.project.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PublicacionJpaRepository extends JpaRepository<PublicacionEntity, UUID> {

    /**
     * Proyectos publicados de un autor, paginados por cursor.
     *
     * <p>Ataca {@code ix_publicacion_feed}, que es un indice <b>parcial</b> sobre
     * {@code estado = 'PUBLICADO'}. La comparacion de tuplas
     * {@code (publicado_en, id) < (:cursorFecha, :cursorId)} permite recorrerlo en una
     * sola pasada descendente. Con {@code OFFSET}, la pagina 50 costaria 50 veces la
     * pagina 1 y las filas se desplazarian al publicar alguien mientras se pagina.</p>
     *
     * <p>Consulta nativa porque JPQL no admite comparacion de tuplas.</p>
     *
     * <p>Se pide {@code limite + 1} fila para saber si hay pagina siguiente sin
     * ejecutar un {@code COUNT} sobre toda la coleccion.</p>
     */
    @Query(value = """
            SELECT p.* FROM publicacion p
            WHERE p.autor_id = :autorId
              AND p.tipo = 'PROYECTO'
              AND p.estado = 'PUBLICADO'
              AND (CAST(:cursorFecha AS timestamptz) IS NULL
                   OR (p.publicado_en, p.id) < (CAST(:cursorFecha AS timestamptz), CAST(:cursorId AS uuid)))
            ORDER BY p.publicado_en DESC, p.id DESC
            LIMIT :limite
            """, nativeQuery = true)
    List<PublicacionEntity> deAutorPaginado(@Param("autorId") UUID autorId,
                                            @Param("cursorFecha") Instant cursorFecha,
                                            @Param("cursorId") UUID cursorId,
                                            @Param("limite") int limite);
}