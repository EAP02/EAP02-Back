package com.codefactory.devnet.profile.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface PerfilJpaRepository extends JpaRepository<PerfilEntity, UUID> {

    /**
     * Cuantas tecnologias declaro el usuario.
     *
     * <p>Publicar exige al menos una (regla R1). Se cuenta en la base en vez de cargar
     * la coleccion: solo interesa el numero.</p>
     */
    @Query(value = "SELECT count(*) FROM perfil_tecnologia WHERE usuario_id = :usuarioId",
           nativeQuery = true)
    int contarTecnologias(@Param("usuarioId") UUID usuarioId);

    /**
     * Si tiene identidad de GitHub vinculada.
     *
     * <p>Es lo que marca el perfil como verificado; sustituye por completo a la
     * verificacion por correo (ADR-004).</p>
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM identidad_externa
                WHERE usuario_id = :usuarioId AND proveedor = 'GITHUB'
            )
            """, nativeQuery = true)
    boolean estaVerificado(@Param("usuarioId") UUID usuarioId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM seguimiento
                WHERE seguidor_id = :seguidorId AND seguido_id = :seguidoId
            )
            """, nativeQuery = true)
    boolean sigue(@Param("seguidorId") UUID seguidorId, @Param("seguidoId") UUID seguidoId);
}