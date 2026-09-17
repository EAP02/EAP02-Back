package com.codefactory.devnet.profile.application;

import com.codefactory.devnet.profile.domain.Perfil;
import com.codefactory.devnet.profile.domain.PerfilNoEncontradoException;
import com.codefactory.devnet.profile.domain.RepositorioPerfiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class EditarPerfil {
    private final RepositorioPerfiles perfiles;

    public EditarPerfil(RepositorioPerfiles perfiles) {
        this.perfiles = perfiles;
    }

    @Transactional
    public Perfil ejecutar(UUID id, String nombre, String biografia, List<String> tecnologias,
                           String githubUrl, String linkedinUrl) {
        Perfil perfil = perfiles.porId(id).orElseThrow(() -> new PerfilNoEncontradoException(id));
        perfil.actualizar(nombre, biografia, tecnologias, githubUrl, linkedinUrl);
        return perfiles.guardar(perfil);
    }
}
