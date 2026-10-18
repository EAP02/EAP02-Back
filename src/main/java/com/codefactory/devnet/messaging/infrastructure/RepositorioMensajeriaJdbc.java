package com.codefactory.devnet.messaging.infrastructure;

import com.codefactory.devnet.messaging.domain.Mensaje;
import com.codefactory.devnet.messaging.domain.RepositorioMensajeria;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class RepositorioMensajeriaJdbc implements RepositorioMensajeria {
    private static final RowMapper<Mensaje> MENSAJE = (rs, i) -> new Mensaje(
            rs.getObject("id", UUID.class), rs.getObject("conversacion_id", UUID.class),
            rs.getObject("remitente_id", UUID.class), rs.getString("contenido"),
            rs.getTimestamp("enviado_en").toInstant());

    private final JdbcTemplate jdbc;

    public RepositorioMensajeriaJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID obtenerOCrearDirecta(UUID remitenteId, UUID destinatarioId) {
        String primero = remitenteId.toString();
        String segundo = destinatarioId.toString();
        String clave = primero.compareTo(segundo) < 0 ? primero + ":" + segundo : segundo + ":" + primero;
        // El indice unico arbitra envios concurrentes de la misma pareja.
        UUID conversacionId = jdbc.queryForObject("""
                INSERT INTO conversacion (creada_por, clave_directa)
                VALUES (?, ?)
                ON CONFLICT (clave_directa) WHERE clave_directa IS NOT NULL
                DO UPDATE SET clave_directa = EXCLUDED.clave_directa
                RETURNING id
                """, UUID.class, remitenteId, clave);
        jdbc.update("""
                INSERT INTO participante_conversacion (conversacion_id, usuario_id)
                VALUES (?, ?), (?, ?)
                ON CONFLICT (conversacion_id, usuario_id)
                DO UPDATE SET abandonada_en = NULL
                """, conversacionId, remitenteId, conversacionId, destinatarioId);
        return conversacionId;
    }

    @Override
    public Mensaje guardar(UUID conversacionId, UUID remitenteId, String contenido) {
        Mensaje mensaje = jdbc.query("""
                INSERT INTO mensaje (conversacion_id, remitente_id, contenido, enviado_en)
                VALUES (?, ?, ?, clock_timestamp())
                RETURNING id, conversacion_id, remitente_id, contenido, enviado_en
                """, MENSAJE, conversacionId, remitenteId, contenido).get(0);
        jdbc.update("""
                UPDATE conversacion
                   SET ultimo_mensaje_en = GREATEST(COALESCE(ultimo_mensaje_en, ?), ?)
                 WHERE id = ?
                """, mensaje.enviadoEn(), mensaje.enviadoEn(), conversacionId);
        return mensaje;
    }

    @Override
    public boolean participa(UUID conversacionId, UUID usuarioId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM participante_conversacion p
                    JOIN conversacion c ON c.id = p.conversacion_id
                    WHERE p.conversacion_id = ? AND p.usuario_id = ?
                      AND p.abandonada_en IS NULL AND c.clave_directa IS NOT NULL)
                """, Boolean.class, conversacionId, usuarioId));
    }

    @Override
    public List<ConversacionResumen> bandeja(UUID usuarioId) {
        return jdbc.query("""
                SELECT c.id AS conversacion_id, otro.usuario_id AS otro_participante_id,
                       c.ultimo_mensaje_en
                  FROM participante_conversacion propia
                  JOIN conversacion c ON c.id = propia.conversacion_id
                  JOIN participante_conversacion otro ON otro.conversacion_id = c.id
                       AND otro.usuario_id <> propia.usuario_id
                 WHERE propia.usuario_id = ? AND propia.abandonada_en IS NULL
                   AND c.clave_directa IS NOT NULL AND c.ultimo_mensaje_en IS NOT NULL
                 ORDER BY c.ultimo_mensaje_en DESC, c.id DESC
                """, (rs, i) -> new ConversacionResumen(
                rs.getObject("conversacion_id", UUID.class),
                rs.getObject("otro_participante_id", UUID.class),
                rs.getTimestamp("ultimo_mensaje_en").toInstant()), usuarioId);
    }

    @Override
    public List<Mensaje> historial(UUID conversacionId) {
        return jdbc.query("""
                SELECT id, conversacion_id, remitente_id, contenido, enviado_en
                  FROM mensaje
                 WHERE conversacion_id = ? AND eliminado_en IS NULL
                 ORDER BY enviado_en ASC, id ASC
                """, MENSAJE, conversacionId);
    }
}
