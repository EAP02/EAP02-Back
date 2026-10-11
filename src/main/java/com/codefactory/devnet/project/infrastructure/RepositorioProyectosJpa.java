package com.codefactory.devnet.project.infrastructure;

import com.codefactory.devnet.project.domain.EstadoProyecto;
import com.codefactory.devnet.project.domain.EstadoPublicacion;
import com.codefactory.devnet.project.domain.Proyecto;
import com.codefactory.devnet.project.domain.RepositorioProyectos;
import com.codefactory.devnet.project.api.PaginaKeyset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adaptador JPA del puerto {@link RepositorioProyectos}.
 */
@Component
public class RepositorioProyectosJpa implements RepositorioProyectos {

    private final PublicacionJpaRepository publicaciones;
    private final PublicacionProyectoJpaRepository proyectos;
    private final RepositorioJpaRepository repositorios;
    private final TecnologiaJpaRepository tecnologias;
    private final JdbcTemplate jdbc;

    public RepositorioProyectosJpa(PublicacionJpaRepository publicaciones,
                                   PublicacionProyectoJpaRepository proyectos,
                                   RepositorioJpaRepository repositorios,
                                   TecnologiaJpaRepository tecnologias,
                                   JdbcTemplate jdbc) {
        this.publicaciones = publicaciones;
        this.proyectos = proyectos;
        this.repositorios = repositorios;
        this.tecnologias = tecnologias;
        this.jdbc = jdbc;
    }

    /**
     * Escribe supertipo, subtipo, etiquetas y repositorio en una sola transaccion.
     *
     * <p>El orden importa: {@code publicacion} antes que {@code publicacion_proyecto}
     * por la clave foranea, y el repositorio al final porque depende del subtipo.
     * Si algo falla, no queda una publicacion sin su especializacion, que romperia el
     * invariante del discriminador.</p>
     */
    @Override
    @Transactional
    public Proyecto guardar(Proyecto proyecto) {
        PublicacionEntity publicacion = PublicacionEntity.proyectoPublicado(
                proyecto.id(),
                proyecto.autorId(),
                proyecto.titulo(),
                proyecto.descripcion(),
                proyecto.publicadoEn());

        tecnologias.findByIdInAndAprobadaTrue(proyecto.tecnologias())
                .forEach(publicacion::agregarTecnologia);

        publicaciones.saveAndFlush(publicacion);

        PublicacionProyectoEntity subtipo = PublicacionProyectoEntity.de(
                publicacion,
                proyecto.resumen(),
                proyecto.estadoProyecto().name(),
                proyecto.licencia(),
                proyecto.urlDemo(),
                proyecto.buscaColaboradores());

        proyectos.saveAndFlush(subtipo);

        if (proyecto.urlRepositorio() != null) {
            repositorios.save(RepositorioEntity.de(publicacion.getId(), proyecto.urlRepositorio()));
        }

        return proyecto;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProyectoResumen> porId(UUID proyectoId) {
        return proyectos.findById(proyectoId).map(this::aResumen);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaKeyset<ProyectoResumen> deAutor(UUID autorId, String cursor, int limite) {
        Cursor decodificado = Cursor.decodificar(cursor);

        // Se pide una fila de mas: es como se sabe si hay pagina siguiente sin contar
        // toda la coleccion.
        List<PublicacionEntity> filas = publicaciones.deAutorPaginado(
                autorId, decodificado.fecha(), decodificado.id(), limite + 1);

        List<ProyectoResumen> resumenes = filas.stream()
                .map(p -> proyectos.findById(p.getId()).map(this::aResumen).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();

        return PaginaKeyset.de(resumenes, limite,
                r -> Cursor.codificar(r.publicadoEn(), r.id()));
    }

    /**
     * HU-09: busqueda por texto completo y filtro por tecnologia.
     *
     * <p><b>SQL construido segun los criterios presentes</b>, en vez de una consulta
     * fija con {@code (:texto IS NULL OR ...)}. Con la forma fija, PostgreSQL acaba
     * usando un plan generico que tiene que servir para ambos casos, y ese plan no puede
     * apoyarse en el indice GIN. Aqui cada condicion solo aparece si aplica, y cada una
     * tiene su indice:</p>
     * <ul>
     *   <li>texto: {@code busqueda_tsv @@ websearch_to_tsquery(...)} sobre
     *       {@code ix_publicacion_busqueda} (GIN), la consulta P04 de
     *       {@code docs/bd};</li>
     *   <li>tecnologia: {@code EXISTS} sobre {@code ix_publicacion_tecnologia_inverso};</li>
     *   <li>orden y cursor: la misma comparacion de tuplas que {@link #deAutor}.</li>
     * </ul>
     *
     * <p>{@code websearch_to_tsquery} y no {@code to_tsquery}: acepta lo que una persona
     * escribe en un buscador (comillas, {@code -excluir}, {@code or}) sin fallar con un
     * error de sintaxis. Con la configuracion {@code spanish}, "plantilla" encuentra
     * "plantillas". No busca por prefijo: "Jav" no encuentra "Java".</p>
     *
     * <p>Solo los valores van como parametros; el texto SQL lo arma este metodo con
     * fragmentos constantes, asi que no hay via de inyeccion.</p>
     */
    @Override
    @Transactional(readOnly = true)
    public PaginaKeyset<ProyectoResumen> buscar(String texto, Short tecnologiaId, String cursor, int limite) {
        Cursor decodificado = Cursor.decodificar(cursor);

        StringBuilder sql = new StringBuilder("""
                SELECT p.id FROM publicacion p
                WHERE p.tipo = 'PROYECTO'
                  AND p.estado = 'PUBLICADO'
                """);
        List<Object> parametros = new java.util.ArrayList<>();

        if (texto != null) {
            sql.append(" AND p.busqueda_tsv @@ websearch_to_tsquery('spanish', ?)");
            parametros.add(texto);
        }
        if (tecnologiaId != null) {
            sql.append("""
                     AND EXISTS (SELECT 1 FROM publicacion_tecnologia pt
                                 WHERE pt.publicacion_id = p.id AND pt.tecnologia_id = ?)
                    """);
            parametros.add(tecnologiaId);
        }
        if (decodificado.fecha() != null) {
            sql.append(" AND (p.publicado_en, p.id) < (?, ?)");
            parametros.add(java.sql.Timestamp.from(decodificado.fecha()));
            parametros.add(decodificado.id());
        }

        // Una fila de mas para saber si hay pagina siguiente sin contar la coleccion.
        sql.append(" ORDER BY p.publicado_en DESC, p.id DESC LIMIT ?");
        parametros.add(limite + 1);

        List<UUID> ids = jdbc.queryForList(sql.toString(), UUID.class, parametros.toArray());

        List<ProyectoResumen> resumenes = ids.stream()
                .map(id -> proyectos.findById(id).map(this::aResumen).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();

        return PaginaKeyset.de(resumenes, limite,
                r -> Cursor.codificar(r.publicadoEn(), r.id()));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean tecnologiasValidas(Set<Short> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        // Una sola consulta detecta a la vez identificadores inexistentes y
        // tecnologias que existen pero aun no estan aprobadas.
        return tecnologias.countByIdInAndAprobadaTrue(ids) == ids.size();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existeRepositorioConUrl(String url) {
        return url != null && repositorios.existsByUrlIgnoreCase(url);
    }

    @Override
    @Transactional
    public boolean transicionarEstado(UUID publicacionId, String estadoNuevo, UUID actorId, String motivo) {
        List<String> estadoActual = jdbc.queryForList(
                "SELECT estado FROM publicacion WHERE id = ?", String.class, publicacionId);

        if (estadoActual.isEmpty()) {
            return false;
        }

        jdbc.update("UPDATE publicacion SET estado = ?, actualizado_en = now() WHERE id = ?",
                estadoNuevo, publicacionId);

        // Regla R7: toda transicion deja rastro, en la misma transaccion.
        jdbc.update("""
                INSERT INTO historial_estado_publicacion
                    (publicacion_id, estado_anterior, estado_nuevo, actor_id, motivo)
                VALUES (?, ?, ?, ?, ?)
                """, publicacionId, estadoActual.get(0), estadoNuevo, actorId, motivo);

        return true;
    }

    // ------------------------------------------------------------------
    // HU-08: edicion
    // ------------------------------------------------------------------

    /**
     * Sin {@code readOnly} a proposito: el caso de uso lee y despues escribe en la
     * misma transaccion. En una de solo lectura, Hibernate marcaria la entidad como
     * inmutable y {@link #actualizar} no escribiria nada, sin dar ningun error.
     */
    @Override
    @Transactional
    public Optional<Proyecto> paraEditar(UUID proyectoId) {
        return proyectos.findById(proyectoId).map(this::aDominio);
    }

    /**
     * Vuelca el agregado editado sobre las entidades ya cargadas.
     *
     * <p>No se insertan filas nuevas de {@code publicacion} ni del subtipo: se
     * modifican las existentes, y Hibernate las escribe al confirmar. El
     * {@code flush} final adelanta esa escritura para que un conflicto de
     * concurrencia ({@code @Version}) salga aqui y no al cerrar la transaccion.</p>
     */
    @Override
    @Transactional
    public void actualizar(Proyecto proyecto) {
        PublicacionProyectoEntity subtipo = proyectos.findById(proyecto.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Se intento actualizar un proyecto inexistente: " + proyecto.id()));

        // El supertipo se pide a su propio repositorio y no via subtipo.getPublicacion().
        // guardar() lo inserta con merge, porque el id se asigna a mano, y el subtipo
        // queda apuntando a la copia desacoplada, no a la gestionada. Si ambas
        // operaciones comparten sesion, modificar esa copia no escribiria nada. findById
        // devuelve siempre la instancia gestionada.
        PublicacionEntity publicacion = publicaciones.findById(proyecto.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Proyecto sin su fila de publicacion: " + proyecto.id()));

        publicacion.actualizarContenido(proyecto.titulo(), proyecto.descripcion(), Instant.now());
        publicacion.reemplazarTecnologias(
                tecnologias.findByIdInAndAprobadaTrue(proyecto.tecnologias()));

        subtipo.actualizar(
                proyecto.resumen(),
                proyecto.estadoProyecto().name(),
                proyecto.licencia(),
                proyecto.urlDemo(),
                proyecto.buscaColaboradores());

        sincronizarRepositorio(proyecto.id(), proyecto.urlRepositorio());

        publicaciones.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existeRepositorioConUrlEnOtroProyecto(String url, UUID proyectoId) {
        return url != null
                && repositorios.existsByUrlIgnoreCaseAndPublicacionProyectoIdNot(url, proyectoId);
    }

    /**
     * Deja el proyecto enlazado exactamente al repositorio indicado, o a ninguno.
     *
     * <p>La API maneja un solo enlace por proyecto, aunque la tabla admita varios. Si
     * el enlace no cambia, la fila se conserva tal cual, con sus estrellas y su ultima
     * sincronizacion; si cambia, se sustituye.</p>
     *
     * <p>El {@code flush} entre borrar e insertar no es decorativo: Hibernate ejecuta
     * las inserciones antes que los borrados, y sin el la restriccion {@code UNIQUE}
     * de {@code url} podria saltar aunque la fila antigua ya estuviera condenada.</p>
     */
    private void sincronizarRepositorio(UUID proyectoId, String urlNueva) {
        List<RepositorioEntity> actuales = repositorios.findByPublicacionProyectoId(proyectoId);

        List<RepositorioEntity> obsoletos = actuales.stream()
                .filter(r -> !r.getUrl().equals(urlNueva))
                .toList();
        boolean yaEnlazado = actuales.size() > obsoletos.size();

        if (!obsoletos.isEmpty()) {
            repositorios.deleteAll(obsoletos);
            repositorios.flush();
        }
        if (urlNueva != null && !yaEnlazado) {
            repositorios.save(RepositorioEntity.de(proyectoId, urlNueva));
        }
    }

    private Proyecto aDominio(PublicacionProyectoEntity subtipo) {
        // Mismo motivo que en actualizar(): la instancia gestionada, no la del subtipo.
        PublicacionEntity p = publicaciones.findById(subtipo.getPublicacionId())
                .orElseThrow(() -> new IllegalStateException(
                        "Proyecto sin su fila de publicacion: " + subtipo.getPublicacionId()));

        String urlRepo = repositorios.findByPublicacionProyectoId(subtipo.getPublicacionId())
                .stream()
                .findFirst()
                .map(RepositorioEntity::getUrl)
                .orElse(null);

        Set<Short> stack = p.getTecnologias().stream()
                .map(TecnologiaEntity::getId)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        return Proyecto.rehidratar(
                p.getId(),
                p.getAutorId(),
                p.getTitulo(),
                p.getContenido(),
                subtipo.getResumen(),
                stack,
                urlRepo,
                EstadoProyecto.valueOf(subtipo.getEstadoProyecto()),
                subtipo.getLicencia(),
                subtipo.getUrlDemo(),
                subtipo.isBuscaColaboradores(),
                p.getPublicadoEn(),
                EstadoPublicacion.valueOf(p.getEstado()));
    }

    // ------------------------------------------------------------------

    private ProyectoResumen aResumen(PublicacionProyectoEntity subtipo) {
        PublicacionEntity p = subtipo.getPublicacion();

        String urlRepo = repositorios.findByPublicacionProyectoId(subtipo.getPublicacionId())
                .stream()
                .findFirst()
                .map(RepositorioEntity::getUrl)
                .orElse(null);

        Set<String> stack = p.getTecnologias().stream()
                .map(TecnologiaEntity::getNombre)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));

        return new ProyectoResumen(
                p.getId(),
                p.getAutorId(),
                p.getTitulo(),
                subtipo.getResumen(),
                subtipo.getEstadoProyecto(),
                urlRepo,
                subtipo.isBuscaColaboradores(),
                stack,
                p.getPublicadoEn());
    }

    /**
     * Cursor de paginacion: {@code base64("instante|uuid")}.
     *
     * <p>Se codifica para que sea opaco. No es seguridad, es contrato: si el cliente
     * puede leerlo, tarde o temprano lo construye a mano y queda atado a la forma
     * interna del indice.</p>
     */
    private record Cursor(Instant fecha, UUID id) {

        static final Cursor VACIO = new Cursor(null, null);

        static String codificar(Instant fecha, UUID id) {
            String plano = fecha.toString() + "|" + id;
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(plano.getBytes(StandardCharsets.UTF_8));
        }

        static Cursor decodificar(String cursor) {
            if (cursor == null || cursor.isBlank()) {
                return VACIO;
            }
            try {
                String plano = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                String[] partes = plano.split("\\|", 2);
                return new Cursor(Instant.parse(partes[0]), UUID.fromString(partes[1]));
            } catch (RuntimeException ex) {
                // Un cursor corrupto devuelve la primera pagina en vez de un error:
                // es un parametro de navegacion, no una instruccion del usuario.
                return VACIO;
            }
        }
    }
}
