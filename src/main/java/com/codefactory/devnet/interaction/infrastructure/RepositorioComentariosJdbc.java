package com.codefactory.devnet.interaction.infrastructure;

import com.codefactory.devnet.interaction.domain.Comentario;
import com.codefactory.devnet.interaction.domain.RepositorioComentarios;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JDBC del puerto {@link RepositorioComentarios}.
 *
 * <p><b>JDBC y no JPA, a proposito</b>, por la misma razon que
 * {@code RepositorioDiscusionesJdbc}: la tabla {@code publicacion} tiene su unica
 * entidad JPA en {@code project.infrastructure}, que este modulo no puede importar
 * (regla 1 de {@code FronterasModularesTest}). Este adaptador solo <i>lee</i>
 * {@code publicacion} para comprobar existencia y estado, y escribe unicamente en
 * {@code comentario}.</p>
 */
@Component
public class RepositorioComentariosJdbc implements RepositorioComentarios {

    private static final RowMapper<Comentario> MAPEADOR = (rs, i) -> new Comentario(
            rs.getObject("id", UUID.class),
            rs.getObject("publicacion_id", UUID.class),
            rs.getObject("autor_id", UUID.class),
            rs.getString("contenido"),
            Comentario.Estado.valueOf(rs.getString("estado")),
            rs.getTimestamp("creado_en").toInstant());

    private final JdbcTemplate jdbc;

    public RepositorioComentariosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean publicacionComentable(UUID publicacionId) {
        Boolean existe = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM publicacion
                                WHERE id = ? AND estado = 'PUBLICADO')
                """, Boolean.class, publicacionId);
        return Boolean.TRUE.equals(existe);
    }

    /**
     * {@code profundidad} y {@code comentario_padre_id} quedan en sus valores por
     * defecto (0 y NULL): ck_comentario_raiz exige que un comentario sin padre tenga
     * profundidad 0. Las respuestas anidadas no estan en esta HU.
     */
    @Override
    @Transactional
    public void guardar(Comentario c) {
        jdbc.update("""
                INSERT INTO comentario (id, publicacion_id, autor_id, contenido, estado, creado_en)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                c.id(), c.publicacionId(), c.autorId(), c.contenido(),
                c.estado().name(), Timestamp.from(c.creadoEn()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Comentario> porId(UUID comentarioId) {
        return jdbc.query("""
                SELECT id, publicacion_id, autor_id, contenido, estado, creado_en
                  FROM comentario WHERE id = ?
                """, MAPEADOR, comentarioId).stream().findFirst();
    }

    @Override
    @Transactional
    public void marcarEliminado(UUID comentarioId) {
        jdbc.update("UPDATE comentario SET estado = 'ELIMINADO' WHERE id = ?", comentarioId);
    }

    /** Usa ix_comentario_publicacion_padre (publicacion_id, comentario_padre_id, creado_en). */
    @Override
    @Transactional(readOnly = true)
    public List<Comentario> visiblesDe(UUID publicacionId) {
        return jdbc.query("""
                SELECT id, publicacion_id, autor_id, contenido, estado, creado_en
                  FROM comentario
                 WHERE publicacion_id = ? AND estado = 'PUBLICADO'
                 ORDER BY creado_en, id
                """, MAPEADOR, publicacionId);
    }
}
