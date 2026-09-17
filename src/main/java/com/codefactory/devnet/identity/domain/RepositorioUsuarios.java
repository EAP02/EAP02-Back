package com.codefactory.devnet.identity.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia de usuarios.
 *
 * <p>Lo declara el dominio y lo implementa {@code infrastructure}. Es la inversion de
 * dependencia que permite que {@code application} no sepa que hay JPA detras.</p>
 */
public interface RepositorioUsuarios {

    /** Busca sin distinguir mayusculas. Vacio si no existe. */
    Optional<Usuario> porCorreo(String correo);

    Optional<Usuario> porId(UUID id);

    boolean existeCorreo(String correo);

    boolean existeNombreUsuario(String nombreUsuario);

    Usuario crear(String correo, String nombreUsuario, String claveHash);

    /** Persiste los cambios de estado de acceso: intentos, bloqueo, ultimo acceso. */
    void guardarEstadoAcceso(Usuario usuario);
}
