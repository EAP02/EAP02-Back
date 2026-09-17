package com.codefactory.devnet.identity.api;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Servicio publico ofrecido por identity al resto del monolito.
 * No expone entidades JPA ni credenciales.
 */
public interface UsuarioDirectorio {

    record UsuarioResumen(UUID id, String nombreUsuario, boolean activo, Set<String> permisos) {
        public UsuarioResumen {
            permisos = permisos == null ? Set.of() : Set.copyOf(permisos);
        }

        public boolean tienePermiso(String codigo) {
            return permisos.contains(codigo);
        }
    }

    Optional<UsuarioResumen> buscar(UUID usuarioId);

    boolean estaActivo(UUID usuarioId);

    Optional<UsuarioResumen> autenticado();
}
