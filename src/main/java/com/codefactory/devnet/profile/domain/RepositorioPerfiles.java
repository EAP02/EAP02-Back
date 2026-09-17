package com.codefactory.devnet.profile.domain;

import java.util.Optional;
import java.util.UUID;

public interface RepositorioPerfiles {
    Optional<Perfil> porId(UUID id);
    Perfil guardar(Perfil perfil);
    void crearInicial(UUID id, String nombreUsuario);
}
