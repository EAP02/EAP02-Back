package com.codefactory.devnet.administration.infrastructure;

import com.codefactory.devnet.administration.domain.Agrupacion;
import com.codefactory.devnet.administration.domain.NuevosPorPeriodo;
import com.codefactory.devnet.administration.domain.RangoFechas;
import com.codefactory.devnet.administration.domain.RepositorioMetricas;
import com.codefactory.devnet.administration.domain.Totales;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Lee {@code metricas_diarias} y llama a los objetos de V11 y V13 con JDBC.
 *
 * <p>El reporte no agrega en vivo sobre las tablas transaccionales (P13-P15 de
 * {@code docs/bd/01-consultas-clave.md}): lee la tabla de hechos. La unica consulta en vivo es
 * {@code fn_usuarios_activos}, que no se puede obtener de una tabla por dia, y su rango esta
 * acotado a {@link RangoFechas#DIAS_MAXIMOS_REPORTE} dias.</p>
 *
 * <p>Todas las sentencias son estaticas. La unidad del periodo ({@code week} o {@code month})
 * viaja como parametro de {@code date_trunc}, no se concatena.</p>
 */
@Repository
public class RepositorioMetricasJdbc implements RepositorioMetricas {

    private static final String CONSOLIDAR =
            "CALL sp_consolidar_metricas_pendientes(CAST(? AS date), CAST(? AS date))";

    private static final String TOTALES = """
            SELECT COALESCE(sum(proyectos_nuevos), 0)   AS proyectos,
                   COALESCE(sum(discusiones_nuevas), 0) AS discusiones,
                   COALESCE(sum(comentarios_nuevos), 0) AS comentarios,
                   COALESCE(sum(usuarios_nuevos), 0)    AS usuarios_nuevos
              FROM metricas_diarias
             WHERE fecha BETWEEN CAST(? AS date) AND CAST(? AS date)
            """;

    private static final String ACTIVOS =
            "SELECT fn_usuarios_activos(CAST(? AS date), CAST(? AS date))";

    private static final String NUEVOS_POR_PERIODO = """
            SELECT CAST(date_trunc(CAST(? AS text), CAST(fecha AS timestamp)) AS date) AS periodo,
                   sum(usuarios_nuevos) AS nuevos
              FROM metricas_diarias
             WHERE fecha BETWEEN CAST(? AS date) AND CAST(? AS date)
             GROUP BY 1
             ORDER BY 1
            """;

    private final JdbcTemplate jdbc;

    public RepositorioMetricasJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void consolidarPendientes(RangoFechas rango) {
        jdbc.update(CONSOLIDAR, rango.desde(), rango.hasta());
    }

    @Override
    public Totales totales(RangoFechas rango) {
        return jdbc.queryForObject(TOTALES, (rs, n) -> new Totales(
                rs.getLong("proyectos"),
                rs.getLong("discusiones"),
                rs.getLong("comentarios"),
                rs.getLong("usuarios_nuevos")), rango.desde(), rango.hasta());
    }

    @Override
    public int usuariosActivos(RangoFechas rango) {
        Integer total = jdbc.queryForObject(ACTIVOS, Integer.class, rango.desde(), rango.hasta());
        return total == null ? 0 : total;
    }

    @Override
    public List<NuevosPorPeriodo> nuevosPorPeriodo(RangoFechas rango, Agrupacion agrupacion) {
        String unidad = agrupacion == Agrupacion.MES ? "month" : "week";
        return jdbc.query(NUEVOS_POR_PERIODO, (rs, n) -> new NuevosPorPeriodo(
                rs.getObject("periodo", LocalDate.class),
                rs.getLong("nuevos")), unidad, rango.desde(), rango.hasta());
    }
}
