package com.codefactory.devnet.profile.domain;

import com.codefactory.devnet.shared.api.DetalleError;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import java.util.List;

public class PerfilInvalidoException extends ExcepcionNegocio {
    private final String campo;

    public PerfilInvalidoException(String campo, String mensaje) {
        super(CodigoErrorPerfil.PERFIL_INVALIDO, mensaje,
                List.of(DetalleError.de(campo, null, mensaje)));
        this.campo = campo;
    }

    public String campo() {
        return campo;
    }
}
