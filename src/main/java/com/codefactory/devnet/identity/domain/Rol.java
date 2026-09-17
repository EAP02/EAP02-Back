package com.codefactory.devnet.identity.domain;

import java.util.Set;

/**
 * Rol con sus permisos, visto desde el dominio.
 *
 * <p>Inmutable y sin anotaciones de persistencia: los cuatro roles son datos de
 * referencia que se cargan en V1 y no cambian en ejecucion.</p>
 *
 * @param codigo   VISITANTE, DESARROLLADOR, MODERADOR o ADMIN
 * @param exigeMfa si ejercer este rol requiere segundo factor (lineamiento 3.4)
 * @param permisos codigos {@code recurso:accion} que concede
 */
public record Rol(String codigo, boolean exigeMfa, Set<String> permisos) {

    public Rol {
        permisos = permisos == null ? Set.of() : Set.copyOf(permisos);
    }
}