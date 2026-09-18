package com.codefactory.devnet.profile.application;

import com.codefactory.devnet.identity.api.UsuarioDirectorio;
import com.codefactory.devnet.profile.domain.Perfil;
import com.codefactory.devnet.profile.domain.PerfilNoEncontradoException;
import com.codefactory.devnet.profile.domain.PoliticaPerfil;
import com.codefactory.devnet.profile.domain.RepositorioPerfiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class EditarAvatar {

    private final RepositorioPerfiles perfiles;
    private final UsuarioDirectorio directorio;

    public EditarAvatar(RepositorioPerfiles perfiles, UsuarioDirectorio directorio) {
        this.perfiles = perfiles;
        this.directorio = directorio;
    }

    @Transactional
    public PerfilDetalle ejecutar(UUID id, String avatarUrl) {

        // Misma regla que en EditarPerfil: el avatar es parte del perfil y solo lo
        // cambia su titular.
        UUID autenticado = directorio.autenticado()
                .map(UsuarioDirectorio.UsuarioResumen::id)
                .orElse(null);
        PoliticaPerfil.exigirTitular(id, autenticado);

        Perfil perfil = perfiles.porId(id).orElseThrow(() -> new PerfilNoEncontradoException(id));
        perfil.actualizarAvatar(avatarUrl);

        Perfil guardado = perfiles.guardar(perfil);
        return PerfilDetalle.de(guardado, perfiles.nombresDeTecnologias(guardado.idsDeTecnologias()));
    }
}