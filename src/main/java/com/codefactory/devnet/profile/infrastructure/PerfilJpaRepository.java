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
     * Si tiene identidad de GitHub <b>vinculada por OAuth</b>.
     *
     * <p>Es lo que marca el perfil como verificado, y sustituye por completo a la
     * verificacion por correo (ADR-004).</p>
     *
     * <p>Consulta {@code identidad_externa}, <b>no</b> {@code perfil.url_github}. Son
     * dos cosas distintas y confundirlas vaciaba la verificacion de sentido:
     * {@code url_github} es un texto que el usuario escribe y puede apuntar a
     * cualquiera, mientras que una fila en {@code identidad_externa} solo existe si
     * GitHub confirmo la identidad en el flujo OAuth.</p>
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM identidad_externa
                WHERE usuario_id = :usuarioId AND proveedor = 'GITHUB'
            )
            """, nativeQuery = true)
    boolean estaVerificado(@Param("usuarioId") UUID usuarioId);
}
