package com.codefactory.devnet.administration.infrastructure;

import com.codefactory.devnet.administration.domain.FiltroAuditoria;
import com.codefactory.devnet.administration.domain.Pagina;
import com.codefactory.devnet.administration.domain.Paginacion;
import com.codefactory.devnet.administration.domain.RegistroAuditado;
import com.codefactory.devnet.administration.domain.RepositorioAuditoria;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Lectura de {@code auditoria} con JDBC.
 *
 * <p>JDBC y no JPA por la misma razon que la escritura ({@code RegistroAuditoriaJdbc}):
 * {@code jsonb} e {@code inet}, y no es una entidad del dominio.</p>
 *
 * <p>Los filtros son opcionales y la sentencia es <b>estatica</b>: cada uno se escribe
 * {@code (CAST(? AS tipo) IS NULL OR columna = CAST(? AS tipo))}. Asi no se concatena
 * texto en el SQL ni de forma controlada (§5.3: "nunca concatenar entrada del usuario
 * en SQL"), y Sonar no marca la clase.</p>
 *
 * <p>Orden: {@code creado_en DESC, id DESC}. El desempate por id hace la paginacion
 * determinista y lo sostiene {@code ix_auditoria_creado} (V12).</p>
 */
@Repository
public class RepositorioAuditoriaJdbc implements RepositorioAuditoria {

    private static final String FILTRO = """
            WHERE (CAST(? AS text) IS NULL OR operacion = CAST(? AS text))
              AND (CAST(? AS uuid) IS NULL OR actor_id = CAST(? AS uuid))
              AND (CAST(? AS text) IS NULL OR tabla = CAST(? AS text))
              AND (CAST(? AS timestamptz) IS NULL OR creado_en >= CAST(? AS timestamptz))
              AND (CAST(? AS timestamptz) IS NULL OR creado_en <  CAST(? AS timestamptz))
            """;

    private static final String CONTAR = "SELECT count(*) FROM auditoria " + FILTRO;

    private static final String LISTAR = """
            SELECT id, creado_en, operacion, tabla, registro_id, actor_id,
                   datos_antes::text AS datos_antes, datos_despues::text AS datos_despues,
                   trace_id, host(ip) AS ip, usuario_bd
              FROM auditoria
            """ + FILTRO + """
             ORDER BY creado_en DESC, id DESC
             LIMIT ? OFFSET ?
            """;

    private final JdbcTemplate jdbc;

    public RepositorioAuditoriaJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Pagina<RegistroAuditado> buscar(FiltroAuditoria filtro, Paginacion paginacion) {
        Object[] criterios = criterios(filtro);

        Long total = jdbc.queryForObject(CONTAR, Long.class, criterios);

        Object[] conPagina = java.util.Arrays.copyOf(criterios, criterios.length + 2);
        conPagina[criterios.length] = paginacion.tamano();
        conPagina[criterios.length + 1] = paginacion.desplazamiento();

        List<RegistroAuditado> filas = jdbc.query(LISTAR, (rs, n) -> new RegistroAuditado(
                rs.getLong("id"),
                rs.getObject("creado_en", OffsetDateTime.class).toInstant(),
                rs.getString("operacion"),
                rs.getString("tabla"),
                rs.getString("registro_id"),
                rs.getObject("actor_id", UUID.class),
                rs.getString("datos_antes"),
                rs.getString("datos_despues"),
                rs.getString("trace_id"),
                rs.getString("ip"),
                rs.getString("usuario_bd")), conPagina);

        return new Pagina<>(filas, paginacion.pagina(), paginacion.tamano(), total == null ? 0 : total);
    }

    /** Cada criterio va dos veces: una para el {@code IS NULL} y otra para la comparacion. */
    private static Object[] criterios(FiltroAuditoria f) {
        UUID actor = f.actorId();
        Timestamp desde = aTimestamp(f.rango() == null ? null : f.rango().inicio());
        Timestamp hasta = aTimestamp(f.rango() == null ? null : f.rango().finExclusivo());
        return new Object[]{
                f.operacion(), f.operacion(),
                actor, actor,
                f.tabla(), f.tabla(),
                desde, desde,
                hasta, hasta
        };
    }

    private static Timestamp aTimestamp(Instant instante) {
        return instante == null ? null : Timestamp.from(instante);
    }
}
