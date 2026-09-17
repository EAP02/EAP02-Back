package com.codefactory.devnet.profile.application;

import com.codefactory.devnet.identity.api.UsuarioRegistrado;
import com.codefactory.devnet.profile.domain.RepositorioPerfiles;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CrearPerfilAlRegistrarUsuario {
    private final RepositorioPerfiles perfiles;

    public CrearPerfilAlRegistrarUsuario(RepositorioPerfiles perfiles) {
        this.perfiles = perfiles;
    }

    @EventListener
    public void alRegistrar(UsuarioRegistrado evento) {
        perfiles.crearInicial(evento.usuarioId(), evento.nombreUsuario());
    }
}
