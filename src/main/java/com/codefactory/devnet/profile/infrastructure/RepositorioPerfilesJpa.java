package com.codefactory.devnet.profile.infrastructure;

import com.codefactory.devnet.profile.domain.Perfil;
import com.codefactory.devnet.profile.domain.RepositorioPerfiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class RepositorioPerfilesJpa implements RepositorioPerfiles {

    private final PerfilJpaRepository jpa;

    /**
     * El catalogo {@code tecnologia} se consulta por SQL y no a traves del
     * repositorio JPA del modulo {@code project}.
     *
     * <p>Importar {@code project.infrastructure.TecnologiaJpaRepository} desde aqui
     * ataria los dos modulos por su infraestructura, que es justo lo que ADR-001
     * prohibe. El catalogo es una tabla de referencia compartida, no una entidad del
     * modulo de proyectos; leerla por SQL no crea acoplamiento de codigo.</p>
     *
     * <p>Con parametros con nombre porque las consultas usan {@code IN} de tamano
     * variable: {@code IN (:ids)} lo expande el propio Spring y el SQL queda
     * constante, sin construir marcadores a mano (lineamiento 5.3).</p>
     */
    private final NamedParameterJdbcTemplate jdbc;

    public RepositorioPerfilesJpa(PerfilJpaRepository jpa, JdbcTemplate jdbc) {
        this.jpa = jpa;
        this.jdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Perfil> porId(UUID id) {
        return jpa.findById(id).map(this::aDominio);
    }

    @Override
    @Transactional
    public Perfil guardar(Perfil perfil) {
        PerfilEntity entidad = jpa.findById(perfil.id())
                .orElseGet(() -> PerfilEntity.nuevo(perfil.id(), perfil.nombre(), null));
        entidad.actualizar(perfil.nombre(), perfil.biografia(), perfil.tecnologias(),
                perfil.githubUrl(), perfil.linkedinUrl());
        if (perfil.avatarUrl() != null) {
            entidad.actualizarAvatar(perfil.avatarUrl());
        }
        return aDominio(jpa.save(entidad));
    }

    /**
     * Perfil vacio al registrarse.
     *
     * <p>Sin tecnologias: el catalogo es curado y elegir una por el usuario seria
     * inventarse un dato. Antes se sembraba "Java" por defecto, lo que dejaba a todo
     * el mundo declarando una tecnologia que quiza no conoce.</p>
     */
    @Override
    @Transactional
    public void crearInicial(UUID id, String nombreUsuario) {
        if (!jpa.existsById(id)) {
            jpa.save(PerfilEntity.nuevo(id, nombreUsuario, "Perfil por completar"));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean tecnologiasValidas(Set<Short> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        // Una sola consulta detecta a la vez identificadores inexistentes y
        // tecnologias que existen pero aun no estan aprobadas.
        Integer encontradas = jdbc.queryForObject(
                "SELECT count(*) FROM tecnologia WHERE aprobada = true AND id IN (:ids)",
                Map.of("ids", ids),
                Integer.class);

        return encontradas != null && encontradas == ids.size();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Short, String> nombresDeTecnologias(Set<Short> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        Map<Short, String> nombres = new HashMap<>();
        List<Map<String, Object>> filas = jdbc.queryForList(
                "SELECT id, nombre FROM tecnologia WHERE id IN (:ids)",
                Map.of("ids", ids));

        for (Map<String, Object> fila : filas) {
            nombres.put(((Number) fila.get("id")).shortValue(), (String) fila.get("nombre"));
        }
        return nombres;
    }

    private Perfil aDominio(PerfilEntity e) {
        return new Perfil(e.getUsuarioId(), e.getNombreCompleto(), e.getBiografia(), e.getUrlAvatar(),
                e.getTecnologias(), e.getUrlGithub(), e.getUrlLinkedin());
    }
}