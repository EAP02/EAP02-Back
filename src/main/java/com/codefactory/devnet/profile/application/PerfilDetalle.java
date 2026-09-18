package com.codefactory.devnet.profile.application;

import com.codefactory.devnet.profile.domain.Perfil;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Vista de un perfil lista para devolver al cliente.
 *
 * <p>El dominio trabaja con identificadores de tecnologia; el cliente necesita
 * nombres. Resolverlos aqui, en la capa de aplicacion, evita que el controlador
 * tenga que consultar el catalogo por su cuenta y que el dominio cargue con un dato
 * que no usa para ninguna regla.</p>
 */
public record PerfilDetalle(
        UUID id,
        String nombre,
        String biografia,
        String avatarUrl,
        List<TecnologiaVista> tecnologias,
        String githubUrl,
        String linkedinUrl
) {

    /**
     * @param nombre nombre del catalogo. Nulo solo si la tecnologia se retiro
     *               despues de declararse, algo que no deberia ocurrir porque el
     *               catalogo no borra, marca como no aprobada.
     */
    public record TecnologiaVista(short id, String nombre, String nivel, Short anios) { }

    public static PerfilDetalle de(Perfil perfil, Map<Short, String> nombres) {
        List<TecnologiaVista> vistas = perfil.tecnologias().stream()
                .map(t -> new TecnologiaVista(
                        t.tecnologiaId(),
                        nombres.get(t.tecnologiaId()),
                        t.nivel().name(),
                        t.anios()))
                .toList();

        return new PerfilDetalle(
                perfil.id(),
                perfil.nombre(),
                perfil.biografia(),
                perfil.avatarUrl(),
                vistas,
                perfil.githubUrl(),
                perfil.linkedinUrl());
    }
}