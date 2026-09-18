package com.codefactory.devnet.identity.infrastructure;

import com.codefactory.devnet.identity.domain.TokenRefresco;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;

/**
 * Fila de {@code token_refresco}. Las reglas viven en {@link TokenRefresco}.
 *
 * <p>Ni {@code usuario_id} ni {@code reemplazado_por} son {@code @ManyToOne}. Renovar un
 * token es la operacion mas frecuente de la sesion y no necesita cargar el usuario ni el
 * sucesor: con el identificador basta. La autorreferencia solo sirve para reconstruir la
 * cadena si hay que investigar un reuso, y eso se hace con una consulta, no con el
 * mapeo.</p>
 */
@Entity
@Table(name = "token_refresco")
public class TokenRefrescoEntity {

    /** Maximo que se guarda del User-Agent. La columna es {@code text} y no tiene tope. */
    static final int MAXIMO_AGENTE = 512;

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    /** SHA-256 en hexadecimal. El valor en claro no se guarda en ninguna parte. */
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    /** Agrupa la cadena de rotaciones nacida de un mismo inicio de sesion. */
    @Column(name = "familia", nullable = false)
    private UUID familia;

    @Column(name = "emitido_en", nullable = false)
    private Instant emitidoEn;

    @Column(name = "expira_en", nullable = false)
    private Instant expiraEn;

    @Column(name = "consumido_en")
    private Instant consumidoEn;

    @Column(name = "revocado_en")
    private Instant revocadoEn;

    @Column(name = "reemplazado_por")
    private UUID reemplazadoPor;

    /**
     * Columna {@code inet} de PostgreSQL.
     *
     * <p>Con {@code ddl-auto: validate}, si este mapeo no cuadra la aplicacion no
     * arranca. Es el comportamiento buscado: un fallo ruidoso en el arranque, no una
     * columna que se queda en blanco sin que nadie se entere.</p>
     */
    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_origen")
    private InetAddress ipOrigen;

    @Column(name = "agente_usuario")
    private String agenteUsuario;

    protected TokenRefrescoEntity() {
        // JPA
    }

    static TokenRefrescoEntity nuevo(UUID usuarioId,
                                     UUID familia,
                                     String tokenHash,
                                     Instant emitidoEn,
                                     Instant expiraEn,
                                     InetAddress ipOrigen,
                                     String agenteUsuario) {
        TokenRefrescoEntity entidad = new TokenRefrescoEntity();
        entidad.id = UUID.randomUUID();
        entidad.usuarioId = usuarioId;
        entidad.familia = familia;
        entidad.tokenHash = tokenHash;
        entidad.emitidoEn = emitidoEn;
        entidad.expiraEn = expiraEn;
        entidad.ipOrigen = ipOrigen;
        entidad.agenteUsuario = recortar(agenteUsuario);
        return entidad;
    }

    /**
     * El identificador se asigna en Java y no se delega en {@code gen_random_uuid()}.
     *
     * <p>La rotacion necesita conocer el id del sucesor para escribirlo en
     * {@code reemplazado_por} del anterior, dentro de la misma transaccion. Si lo
     * generara la base de datos habria que leerlo de vuelta antes de poder enlazarlos.</p>
     */
    UUID id() {
        return id;
    }

    TokenRefresco aDominio() {
        return new TokenRefresco(
                id, usuarioId, familia,
                emitidoEn, expiraEn,
                consumidoEn, revocadoEn, reemplazadoPor);
    }

    private static String recortar(String agente) {
        if (agente == null) {
            return null;
        }
        return agente.length() <= MAXIMO_AGENTE ? agente : agente.substring(0, MAXIMO_AGENTE);
    }
}