package com.codefactory.devnet.profile.domain;

import java.util.UUID;

import com.codefactory.devnet.shared.api.DetalleError;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import java.util.List;

public class PerfilNoEncontradoException extends ExcepcionNegocio {
    public PerfilNoEncontradoException(UUID id) {
        super(CodigoErrorPerfil.PERFIL_NO_ENCONTRADO, "No existe un perfil con id " + id + ".",
                List.of(DetalleError.de("perfilId", id, "no_encontrado")));
    }
}
