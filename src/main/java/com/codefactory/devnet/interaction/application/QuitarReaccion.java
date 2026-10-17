package com.codefactory.devnet.interaction.application;

import com.codefactory.devnet.identity.api.UsuarioDirectorio;
import com.codefactory.devnet.interaction.domain.RepositorioReacciones;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * HU-14, criterio 2: al quitar la reaccion, el contador baja en uno.
 *
 * <p>Idempotente: quitar una reaccion que no existe no es un error ni resta nada (el
 * contador solo baja si de verdad se borro una fila).</p>
 */
@Service
public class QuitarReaccion {

    private final RepositorioReacciones reacciones;
    private final UsuarioDirectorio directorio;

    public QuitarReaccion(RepositorioReacciones reacciones, UsuarioDirectorio directorio) {
        this.reacciones = reacciones;
        this.directorio = directorio;
    }

    @Transactional
    public int ejecutar(UUID publicacionId) {
        UsuarioDirectorio.UsuarioResumen usuario = directorio.autenticado()
                .orElseThrow(() -> ExcepcionNegocio.accesoDenegado("sin_sesion"));

        if (!reacciones.publicacionReaccionable(publicacionId)) {
            throw ExcepcionNegocio.noEncontrado("publicacion", publicacionId);
        }

        reacciones.quitar(usuario.id(), publicacionId);
        return reacciones.total(publicacionId);
    }
}
