package com.codefactory.devnet.profile.domain;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface RepositorioPerfiles {

    Optional<Perfil> porId(UUID id);

    Perfil guardar(Perfil perfil);

    /**
     * Crea el perfil vacio al registrarse el usuario.
     *
     * <p>Sin tecnologias: el catalogo es curado y elegir una por el usuario seria
     * inventarse un dato. El perfil nace incompleto y se completa al editarlo, que es
     * la primera accion natural de alguien que acaba de registrarse.</p>
     */
    void crearInicial(UUID id, String nombreUsuario);

    /**
     * {@code true} si todos los identificadores existen en el catalogo <b>y</b> estan
     * aprobados.
     *
     * <p>Una sola consulta detecta a la vez identificadores inexistentes y
     * tecnologias pendientes de aprobacion.</p>
     */
    boolean tecnologiasValidas(Set<Short> ids);

    /**
     * Nombres del catalogo para los identificadores dados.
     *
     * <p>El dominio trabaja con identificadores; la respuesta al cliente necesita
     * nombres legibles. Se resuelve de una consulta para toda la coleccion, no una
     * por tecnologia.</p>
     */
    Map<Short, String> nombresDeTecnologias(Set<Short> ids);
}