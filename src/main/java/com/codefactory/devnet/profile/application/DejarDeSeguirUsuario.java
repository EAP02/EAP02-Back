package com.codefactory.devnet.profile.application;

import com.codefactory.devnet.identity.api.UsuarioDirectorio;
import com.codefactory.devnet.profile.domain.RepositorioSeguimientos;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * HU-15, criterio 2: al dejar de seguir, el usuario desaparece de la lista de seguidos.
 *
 * <p>Idempotente: dejar de seguir a quien no se seguia no es un error.</p>
 */
@Service
public class DejarDeSeguirUsuario {

    private final RepositorioSeguimientos seguimientos;
    private final UsuarioDirectorio directorio;

    public DejarDeSeguirUsuario(RepositorioSeguimientos seguimientos, UsuarioDirectorio directorio) {
        this.seguimientos = seguimientos;
        this.directorio = directorio;
    }

    @Transactional
    public void ejecutar(UUID seguidoId) {
        UsuarioDirectorio.UsuarioResumen seguidor = directorio.autenticado()
                .orElseThrow(() -> ExcepcionNegocio.accesoDenegado("sin_sesion"));

        seguimientos.dejarDeSeguir(seguidor.id(), seguidoId);
    }
}
