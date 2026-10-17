package com.codefactory.devnet.interaction.infrastructure;

import com.codefactory.devnet.interaction.domain.RepositorioReacciones;
import com.codefactory.devnet.interaction.domain.TipoReaccion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JDBC del puerto {@link RepositorioReacciones}.
 *
 * <p>JDBC por la misma razon que {@code RepositorioComentariosJdbc}: {@code publicacion}
 * tiene su entidad JPA en otro modulo. La unicidad "una reaccion por usuario y
 * publicacion" la impone la clave primaria compuesta de {@code reaccion_publicacion};
 * este adaptador solo aprovecha esa garantia con {@code ON CONFLICT}.</p>
 *
 * <p>El contador {@code publicacion.contador_reacciones} lo mantiene este adaptador en la
 * misma transaccion que la fila (la base aun no tiene triggers de contadores).
 * Solo cambia cuando la fila REALMENTE se crea o se borra, por eso nunca se desfasa ni
 * baja de cero.</p>
 */
@Component
public class RepositorioReaccionesJdbc implements RepositorioReacciones {

    private final JdbcTemplate jdbc;

    public RepositorioReaccionesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean publicacionReaccionable(UUID publicacionId) {
        Boolean existe = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM publicacion
                                WHERE id = ? AND estado = 'PUBLICADO')
                """, Boolean.class, publicacionId);
        return Boolean.TRUE.equals(existe);
    }

    @Override
    @Transactional
    public Efecto reaccionar(UUID usuarioId, UUID publicacionId, TipoReaccion tipo) {
        int insertadas = jdbc.update("""
                INSERT INTO reaccion_publicacion (usuario_id, publicacion_id, tipo)
                VALUES (?, ?, ?)
                ON CONFLICT (usuario_id, publicacion_id) DO NOTHING
                """, usuarioId, publicacionId, tipo.name());

        if (insertadas == 1) {
            jdbc.update("UPDATE publicacion SET contador_reacciones = contador_reacciones + 1 WHERE id = ?",
                    publicacionId);
            return Efecto.CREADA;
        }

        // Ya tenia una: si es de otro tipo se cambia (UPDATE, nunca una segunda fila).
        int cambiadas = jdbc.update("""
                UPDATE reaccion_publicacion SET tipo = ?
                 WHERE usuario_id = ? AND publicacion_id = ? AND tipo <> ?
                """, tipo.name(), usuarioId, publicacionId, tipo.name());
        return cambiadas == 1 ? Efecto.CAMBIADA : Efecto.SIN_CAMBIO;
    }

    @Override
    @Transactional
    public boolean quitar(UUID usuarioId, UUID publicacionId) {
        int borradas = jdbc.update(
                "DELETE FROM reaccion_publicacion WHERE usuario_id = ? AND publicacion_id = ?",
                usuarioId, publicacionId);
        if (borradas == 1) {
            jdbc.update("""
                    UPDATE publicacion SET contador_reacciones = GREATEST(contador_reacciones - 1, 0)
                     WHERE id = ?
                    """, publicacionId);
            return true;
        }
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public int total(UUID publicacionId) {
        Integer total = jdbc.queryForObject(
                "SELECT contador_reacciones FROM publicacion WHERE id = ?", Integer.class, publicacionId);
        return total == null ? 0 : total;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<TipoReaccion, Integer> porTipo(UUID publicacionId) {
        Map<TipoReaccion, Integer> resultado = new EnumMap<>(TipoReaccion.class);
        jdbc.query("""
                SELECT tipo, count(*) AS total FROM reaccion_publicacion
                 WHERE publicacion_id = ? GROUP BY tipo
                """, rs -> {
            resultado.put(TipoReaccion.valueOf(rs.getString("tipo")), rs.getInt("total"));
        }, publicacionId);
        return resultado;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TipoReaccion> deUsuario(UUID usuarioId, UUID publicacionId) {
        return jdbc.query("""
                SELECT tipo FROM reaccion_publicacion WHERE usuario_id = ? AND publicacion_id = ?
                """, (rs, i) -> TipoReaccion.valueOf(rs.getString("tipo")),
                usuarioId, publicacionId).stream().findFirst();
    }
}
