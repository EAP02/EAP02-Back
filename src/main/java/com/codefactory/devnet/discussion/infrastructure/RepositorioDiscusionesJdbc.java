package com.codefactory.devnet.discussion.infrastructure;

import com.codefactory.devnet.discussion.domain.CategoriaDiscusion;
import com.codefactory.devnet.discussion.domain.Discusion;
import com.codefactory.devnet.discussion.domain.RepositorioDiscusiones;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Adaptador JDBC del puerto {@link RepositorioDiscusiones}.
 *
 * <p><b>JDBC y no JPA, a proposito.</b> La tabla {@code publicacion} es el supertipo de
 * proyectos y discusiones, y su entidad JPA ({@code PublicacionEntity}) vive en
 * {@code project.infrastructure}, que este modulo no puede importar (regla 1 de
 * {@code FronterasModularesTest}). Mapear la misma tabla con una segunda entidad aqui
 * dejaria dos definiciones de {@code publicacion} que tendrian que evolucionar a la vez.
 * Con SQL explicito, este modulo escribe solo las columnas que le corresponden y la
 * tabla tiene una unica entidad. Es el mismo criterio que {@code RegistroAuditoriaJdbc}
 * y {@code RepositorioProyectosJpa.transicionarEstado}.</p>
 *
 * <p>Todas las consultas filtran por {@code tipo = 'DISCUSION'}: este adaptador nunca
 * lee ni toca un proyecto, aunque compartan tabla.</p>
 */
@Component
public class RepositorioDiscusionesJdbc implements RepositorioDiscusiones {

    private static final String TIPO = "DISCUSION";
    private static final String ESTADO_PUBLICADO = "PUBLICADO";

    private final JdbcTemplate jdbc;

    /** Para los {@code IN} de tamano variable: Spring expande {@code :ids} y el SQL queda constante. */
    private final NamedParameterJdbcTemplate jdbcConNombres;

    public RepositorioDiscusionesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.jdbcConNombres = new NamedParameterJdbcTemplate(jdbc);
    }

    /**
     * Supertipo, subtipo y etiquetas, en ese orden por las claves foraneas, dentro de
     * una sola transaccion.
     *
     * <p>{@code publicado_en} se fija porque {@code ck_publicacion_publicado_en} exige
     * que todo estado distinto de BORRADOR lo tenga. {@code busqueda_tsv} no se escribe:
     * es una columna generada, y la discusion queda indexada para la busqueda de texto
     * completo sin hacer nada mas.</p>
     */
    @Override
    @Transactional
    public void guardar(Discusion discusion) {
        Timestamp publicadoEn = Timestamp.from(discusion.publicadoEn());

        jdbc.update("""
                INSERT INTO publicacion
                    (id, autor_id, tipo, titulo, contenido, estado, publicado_en, creado_en, actualizado_en)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                discusion.id(), discusion.autorId(), TIPO, discusion.titulo(), discusion.cuerpo(),
                ESTADO_PUBLICADO, publicadoEn, publicadoEn, publicadoEn);

        jdbc.update("INSERT INTO publicacion_discusion (publicacion_id, categoria) VALUES (?, ?)",
                discusion.id(), discusion.categoria().name());

        // Una sentencia por lote y no una por etiqueta: son a lo sumo cinco, pero no hay
        // motivo para hacer cinco viajes a la base.
        List<Object[]> etiquetas = discusion.tecnologias().stream()
                .map(tecnologiaId -> new Object[]{discusion.id(), tecnologiaId})
                .toList();
        jdbc.batchUpdate(
                "INSERT INTO publicacion_tecnologia (publicacion_id, tecnologia_id) VALUES (?, ?)",
                etiquetas);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<DiscusionPublicada> publicadaPorId(UUID discusionId) {
        List<DiscusionPublicada> filas = jdbc.query("""
                SELECT p.id, p.autor_id, p.titulo, p.contenido, p.publicado_en,
                       d.categoria, d.resuelta
                  FROM publicacion p
                  JOIN publicacion_discusion d ON d.publicacion_id = p.id
                 WHERE p.id = ? AND p.tipo = ? AND p.estado = ?
                """,
                (rs, i) -> new DiscusionPublicada(
                        rs.getObject("id", UUID.class),
                        rs.getObject("autor_id", UUID.class),
                        rs.getString("titulo"),
                        rs.getString("contenido"),
                        CategoriaDiscusion.valueOf(rs.getString("categoria")),
                        rs.getBoolean("resuelta"),
                        etiquetasDe(discusionId),
                        rs.getTimestamp("publicado_en").toInstant()),
                discusionId, TIPO, ESTADO_PUBLICADO);

        return filas.stream().findFirst();
    }

    /**
     * Agrupa por autor sobre {@code ix_comentario_publicacion_padre}, cuya primera
     * columna es {@code publicacion_id}. Una sola consulta, sea cual sea el tamano del
     * hilo.
     */
    @Override
    @Transactional(readOnly = true)
    public List<Participante> participantes(UUID discusionId) {
        return jdbc.query("""
                SELECT c.autor_id, count(*) AS comentarios, min(c.creado_en) AS primera
                  FROM comentario c
                 WHERE c.publicacion_id = ? AND c.estado = 'PUBLICADO'
                 GROUP BY c.autor_id
                 ORDER BY primera, c.autor_id
                """,
                (rs, i) -> new Participante(
                        rs.getObject("autor_id", UUID.class),
                        rs.getInt("comentarios"),
                        rs.getTimestamp("primera").toInstant()),
                discusionId);
    }

    /**
     * HU-12: listado paginado por keyset, con filtro opcional por etiqueta.
     *
     * <p>SQL armado segun el filtro presente, como la busqueda de proyectos: con una
     * consulta fija del tipo {@code (? IS NULL OR ...)}, PostgreSQL acaba con un plan
     * generico que no aprovecha los indices. Solo los valores van como parametros.</p>
     *
     * <p>Indices: {@code ix_publicacion_feed} (parcial sobre PUBLICADO, por
     * {@code publicado_en}) para el orden y el cursor, e
     * {@code ix_publicacion_tecnologia_inverso} para el {@code EXISTS} de la etiqueta.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public Pagina listar(Short tecnologiaId, String cursor, int limite) {
        Cursor decodificado = Cursor.decodificar(cursor);

        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.autor_id, p.titulo, p.contenido, p.publicado_en,
                       d.categoria, d.resuelta
                  FROM publicacion p
                  JOIN publicacion_discusion d ON d.publicacion_id = p.id
                 WHERE p.tipo = 'DISCUSION' AND p.estado = 'PUBLICADO'
                """);
        List<Object> parametros = new ArrayList<>();

        if (tecnologiaId != null) {
            sql.append("""
                     AND EXISTS (SELECT 1 FROM publicacion_tecnologia pt
                                 WHERE pt.publicacion_id = p.id AND pt.tecnologia_id = ?)
                    """);
            parametros.add(tecnologiaId);
        }
        if (decodificado != null) {
            sql.append(" AND (p.publicado_en, p.id) < (?, ?)");
            parametros.add(Timestamp.from(decodificado.fecha()));
            parametros.add(decodificado.id());
        }

        // Una fila de mas: es como se sabe si hay pagina siguiente sin contar todas.
        sql.append(" ORDER BY p.publicado_en DESC, p.id DESC LIMIT ?");
        parametros.add(limite + 1);

        List<FilaDiscusion> filas = jdbc.query(sql.toString(),
                (rs, i) -> new FilaDiscusion(
                        rs.getObject("id", UUID.class),
                        rs.getObject("autor_id", UUID.class),
                        rs.getString("titulo"),
                        rs.getString("contenido"),
                        CategoriaDiscusion.valueOf(rs.getString("categoria")),
                        rs.getBoolean("resuelta"),
                        rs.getTimestamp("publicado_en").toInstant()),
                parametros.toArray());

        boolean hayMas = filas.size() > limite;
        List<FilaDiscusion> pagina = hayMas ? filas.subList(0, limite) : filas;

        Map<UUID, Set<Short>> etiquetas = etiquetasDe(pagina.stream().map(FilaDiscusion::id).toList());
        List<DiscusionPublicada> contenido = pagina.stream()
                .map(f -> f.conEtiquetas(etiquetas.getOrDefault(f.id(), Set.of())))
                .toList();

        String siguiente = hayMas
                ? Cursor.codificar(pagina.get(pagina.size() - 1).publicadoEn(), pagina.get(pagina.size() - 1).id())
                : null;

        return new Pagina(contenido, siguiente, hayMas);
    }

    /**
     * Las etiquetas de toda la pagina en <b>una</b> consulta, en lugar de una por
     * discusion. Con paginas de hasta 50, la diferencia son 49 viajes a la base.
     */
    private Map<UUID, Set<Short>> etiquetasDe(List<UUID> discusiones) {
        Map<UUID, Set<Short>> porDiscusion = new HashMap<>();
        if (discusiones.isEmpty()) {
            return porDiscusion;
        }
        jdbcConNombres.query("""
                SELECT publicacion_id, tecnologia_id FROM publicacion_tecnologia
                 WHERE publicacion_id IN (:ids)
                 ORDER BY tecnologia_id
                """,
                Map.of("ids", discusiones),
                rs -> {
                    porDiscusion
                            .computeIfAbsent(rs.getObject("publicacion_id", UUID.class), k -> new LinkedHashSet<>())
                            .add(rs.getShort("tecnologia_id"));
                });
        return porDiscusion;
    }

    /** Una fila del listado antes de adjuntarle sus etiquetas. */
    private record FilaDiscusion(UUID id, UUID autorId, String titulo, String cuerpo,
                                 CategoriaDiscusion categoria, boolean resuelta, Instant publicadoEn) {

        DiscusionPublicada conEtiquetas(Set<Short> etiquetas) {
            return new DiscusionPublicada(id, autorId, titulo, cuerpo, categoria, resuelta, etiquetas, publicadoEn);
        }
    }

    /**
     * Cursor de paginacion: {@code base64("instante|uuid")}, el mismo formato que el de
     * proyectos.
     *
     * <p>Opaco a proposito: si el cliente pudiera leerlo, acabaria construyendolo a mano
     * y quedaria atado a la forma interna del indice. Un cursor corrupto devuelve la
     * primera pagina en vez de un error: es navegacion, no una orden del usuario.</p>
     *
     * <p>No se comparte con el de {@code project} porque aquel es privado de su
     * infraestructura, y que dos colecciones distintas paginen igual no las obliga a
     * compartir codigo.</p>
     */
    private record Cursor(Instant fecha, UUID id) {

        static String codificar(Instant fecha, UUID id) {
            String plano = fecha.toString() + "|" + id;
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(plano.getBytes(StandardCharsets.UTF_8));
        }

        /** @return {@code null} si no hay cursor o es ilegible: primera pagina */
        static Cursor decodificar(String cursor) {
            if (cursor == null || cursor.isBlank()) {
                return null;
            }
            try {
                String plano = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                String[] partes = plano.split("\\|", 2);
                return new Cursor(Instant.parse(partes[0]), UUID.fromString(partes[1]));
            } catch (RuntimeException ex) {
                return null;
            }
        }
    }

    private Set<Short> etiquetasDe(UUID discusionId) {
        return new LinkedHashSet<>(jdbc.queryForList(
                "SELECT tecnologia_id FROM publicacion_tecnologia WHERE publicacion_id = ? ORDER BY tecnologia_id",
                Short.class, discusionId));
    }
}
