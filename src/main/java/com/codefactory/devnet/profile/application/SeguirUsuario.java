package com.codefactory.devnet.profile.application;

import com.codefactory.devnet.identity.api.UsuarioDirectorio;
import com.codefactory.devnet.profile.domain.CodigoErrorPerfil;
import com.codefactory.devnet.profile.domain.RepositorioSeguimientos;
import com.codefactory.devnet.profile.domain.Seguimiento;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * HU-15, criterios 1 y 3: seguir a otro miembro.
 *
 * <ol>
 *   <li>al seguirlo, aparece en la lista de seguidos;</li>
 *   <li>un usuario no puede seguirse a si mismo.</li>
 * </ol>
 *
 * <p>Quien sigue sale SIEMPRE del token. Seguir a quien ya se sigue no es un error ni
 * duplica nada (idempotente).</p>
 */
@Service
public class SeguirUsuario {

    private final RepositorioSeguimientos seguimientos;
    private final UsuarioDirectorio directorio;

    public SeguirUsuario(RepositorioSeguimientos seguimientos, UsuarioDirectorio directorio) {
        this.seguimientos = seguimientos;
        this.directorio = directorio;
    }

    @Transactional
    public void ejecutar(UUID seguidoId) {
        UsuarioDirectorio.UsuarioResumen seguidor = directorio.autenticado()
                .orElseThrow(() -> ExcepcionNegocio.accesoDenegado("sin_sesion"));

        if (!directorio.estaActivo(seguidor.id())) {
            throw new ExcepcionNegocio(CodigoErrorPerfil.SEGUIMIENTO_SIN_PERMISO_DE_ESCRITURA);
        }

        // Primero la regla (422), despues la existencia (404): lo barato antes que la base.
        Seguimiento nuevo = Seguimiento.nuevo(seguidor.id(), seguidoId);

        if (!directorio.estaActivo(seguidoId)) {
            throw ExcepcionNegocio.noEncontrado("usuario", seguidoId);
        }

        seguimientos.seguir(nuevo);
    }
}
