package com.codefactory.devnet.discussion.infrastructure;

import com.codefactory.devnet.discussion.domain.CategoriaDiscusion;
import com.codefactory.devnet.discussion.domain.Discusion;
import com.codefactory.devnet.discussion.domain.RepositorioDiscusiones;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.LinkedHashSet;
import java.util.List;
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

    public RepositorioDiscusionesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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

    private Set<Short> etiquetasDe(UUID discusionId) {
        return new LinkedHashSet<>(jdbc.queryForList(
                "SELECT tecnologia_id FROM publicacion_tecnologia WHERE publicacion_id = ? ORDER BY tecnologia_id",
                Short.class, discusionId));
    }
}
