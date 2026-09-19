package com.codefactory.devnet.identity.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TokenRefrescoRepositorio extends JpaRepository<TokenRefrescoEntity, UUID> {

    /** Una sola lectura por el indice unico de {@code token_hash}. */
    Optional<TokenRefrescoEntity> findByTokenHash(String tokenHash);

    /**
     * Marca el token como consumido <b>solo si nadie se adelanto</b>.
     *
     * <p>El {@code WHERE} es la deteccion de reuso bajo concurrencia: si devuelve cero
     * filas, o bien el token ya estaba consumido, o bien otra peticion simultanea lo
     * consumio primero. En los dos casos la respuesta correcta es la misma.</p>
     *
     * <p>{@code flushAutomatically} y {@code clearAutomatically} son necesarios: la
     * entidad se acaba de leer en esta misma transaccion, y sin limpiar el contexto de
     * persistencia quedaria con valores viejos.</p>
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
           UPDATE TokenRefrescoEntity t
              SET t.consumidoEn = :momento
            WHERE t.id = :id
              AND t.consumidoEn IS NULL
              AND t.revocadoEn IS NULL
           """)
    int consumirSiSigueVigente(@Param("id") UUID id, @Param("momento") Instant momento);

    /**
     * Enlaza el token consumido con su sucesor.
     *
     * <p>Va aparte del consumo y despues de insertar el sucesor porque
     * {@code reemplazado_por} tiene clave foranea contra la propia tabla: apuntar a una
     * fila que aun no existe violaria la restriccion. Solo sirve para reconstruir la
     * cadena si hay que investigar un reuso.</p>
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
           UPDATE TokenRefrescoEntity t
              SET t.reemplazadoPor = :sucesor
            WHERE t.id = :id
           """)
    int enlazarSucesor(@Param("id") UUID id, @Param("sucesor") UUID sucesor);

    /**
     * Revoca la familia entera. Ataca {@code ix_token_familia}, que es parcial sobre
     * {@code revocado_en IS NULL}.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
           UPDATE TokenRefrescoEntity t
              SET t.revocadoEn = :momento
            WHERE t.familia = :familia
              AND t.revocadoEn IS NULL
           """)
    int revocarFamilia(@Param("familia") UUID familia, @Param("momento") Instant momento);

    /** Todas las sesiones de un usuario. Ataca {@code ix_token_usuario_vigente}. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
           UPDATE TokenRefrescoEntity t
              SET t.revocadoEn = :momento
            WHERE t.usuarioId = :usuarioId
              AND t.revocadoEn IS NULL
           """)
    int revocarDeUsuario(@Param("usuarioId") UUID usuarioId, @Param("momento") Instant momento);
}