package com.codefactory.devnet.profile.infrastructure;

import com.codefactory.devnet.profile.domain.RepositorioSeguimientos;
import com.codefactory.devnet.profile.domain.Seguimiento;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * Adaptador JDBC del puerto {@link RepositorioSeguimientos}.
 *
 * <p>JDBC y no JPA: {@code seguimiento} no tiene entidad y relaciona dos usuarios, cuya
 * entidad pertenece a {@code identity} (que este modulo no puede importar). La unicidad
 * "un seguimiento por pareja" la impone la clave primaria compuesta; este adaptador solo
 * la aprovecha con {@code ON CONFLICT}.</p>
 */
@Component
public class RepositorioSeguimientosJdbc implements RepositorioSeguimientos {

    private static final RowMapper<Seguimiento> MAPEADOR = (rs, i) -> new Seguimiento(
            rs.getObject("seguidor_id", UUID.class),
            rs.getObject("seguido_id", UUID.class),
            rs.getTimestamp("creado_en").toInstant());

    private final JdbcTemplate jdbc;

    public RepositorioSeguimientosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public boolean seguir(Seguimiento s) {
        return jdbc.update("""
                INSERT INTO seguimiento (seguidor_id, seguido_id, creado_en)
                VALUES (?, ?, ?)
                ON CONFLICT (seguidor_id, seguido_id) DO NOTHING
                """, s.seguidorId(), s.seguidoId(), Timestamp.from(s.creadoEn())) == 1;
    }

    @Override
    @Transactional
    public boolean dejarDeSeguir(UUID seguidorId, UUID seguidoId) {
        return jdbc.update("DELETE FROM seguimiento WHERE seguidor_id = ? AND seguido_id = ?",
                seguidorId, seguidoId) == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Seguimiento> seguidosPor(UUID usuarioId) {
        return jdbc.query("""
                SELECT seguidor_id, seguido_id, creado_en FROM seguimiento
                 WHERE seguidor_id = ? ORDER BY creado_en DESC, seguido_id
                """, MAPEADOR, usuarioId);
    }

    /** Usa ix_seguimiento_seguido (seguido_id, seguidor_id). */
    @Override
    @Transactional(readOnly = true)
    public List<Seguimiento> seguidoresDe(UUID usuarioId) {
        return jdbc.query("""
                SELECT seguidor_id, seguido_id, creado_en FROM seguimiento
                 WHERE seguido_id = ? ORDER BY creado_en DESC, seguidor_id
                """, MAPEADOR, usuarioId);
    }
}
