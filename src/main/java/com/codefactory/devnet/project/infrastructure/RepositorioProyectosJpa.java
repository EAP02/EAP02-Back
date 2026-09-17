package com.codefactory.devnet.project.infrastructure;

import com.codefactory.devnet.project.domain.Proyecto;
import com.codefactory.devnet.project.domain.RepositorioProyectos;
import com.codefactory.devnet.shared.api.PaginaKeyset;
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
     * invariante del discriminador (consulta de integridad I2 en
     * {@code docs/bd/05-consultas-no-triviales.sql}).</p>
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