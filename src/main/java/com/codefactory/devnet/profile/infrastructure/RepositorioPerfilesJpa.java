package com.codefactory.devnet.profile.infrastructure;

import com.codefactory.devnet.profile.domain.Perfil;
import com.codefactory.devnet.profile.domain.RepositorioPerfiles;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public class RepositorioPerfilesJpa implements RepositorioPerfiles {
    private final PerfilJpaRepository jpa;

    public RepositorioPerfilesJpa(PerfilJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Perfil> porId(UUID id) {
        return jpa.findById(id).map(this::aDominio);
    }

    @Override
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

    @Override
    @Transactional
    public void crearInicial(UUID id, String nombreUsuario) {
        if (!jpa.existsById(id)) {
            PerfilEntity entidad = PerfilEntity.nuevo(id, nombreUsuario, "Perfil tecnico");
            entidad.actualizar(nombreUsuario, "Perfil por completar", java.util.List.of("Java"), null, null);
            jpa.save(entidad);
        }
    }

    private Perfil aDominio(PerfilEntity e) {
        return new Perfil(e.getUsuarioId(), e.getNombreCompleto(), e.getBiografia(), e.getUrlAvatar(),
                e.getHabilidades(), e.getUrlGithub(), e.getUrlLinkedin());
    }
}
