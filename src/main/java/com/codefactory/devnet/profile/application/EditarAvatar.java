package com.codefactory.devnet.profile.application;

import com.codefactory.devnet.profile.domain.Perfil;
import com.codefactory.devnet.profile.domain.PerfilNoEncontradoException;
import com.codefactory.devnet.profile.domain.RepositorioPerfiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class EditarAvatar {
    private final RepositorioPerfiles perfiles;

    public EditarAvatar(RepositorioPerfiles perfiles) {
        this.perfiles = perfiles;
    }

    @Transactional
    public Perfil ejecutar(UUID id, String avatarUrl) {
        Perfil perfil = perfiles.porId(id).orElseThrow(() -> new PerfilNoEncontradoException(id));
        perfil.actualizarAvatar(avatarUrl);
        return perfiles.guardar(perfil);
    }
}
