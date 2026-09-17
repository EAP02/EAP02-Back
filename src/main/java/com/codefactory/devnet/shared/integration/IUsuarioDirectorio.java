package com.codefactory.devnet.shared.integration;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Contrato que <b>provee el modulo {@code identity}</b>.
 *
 * <p>Permite al resto de modulos preguntar por la identidad y los permisos de un
 * usuario sin conocer como se autentica, donde se guardan sus credenciales ni que
 * proveedor federado uso.</p>
 *
 * <p>Deliberadamente no expone nada que permita actuar sobre la cuenta: no hay
 * crear, suspender ni cambiar roles. La escritura sobre identidad es competencia
 * exclusiva de su propio modulo.</p>
 */
public interface IUsuarioDirectorio {

    /**
     * Datos minimos de un usuario para mostrarlo o autorizarlo.
     *
     * @param id            identificador estable
     * @param nombreUsuario handle publico, unico e insensible a mayusculas
     * @param activo        {@code false} si la cuenta esta suspendida o desactivada
     * @param permisos      codigos en formato {@code recurso:accion}, resueltos desde
     *                      los roles. Se exponen permisos y no nombres de rol para
     *                      que reasignar capacidades no obligue a tocar codigo.
     */
    record UsuarioResumen(
            UUID id,
            String nombreUsuario,
            boolean activo,
            Set<String> permisos
    ) {
        public UsuarioResumen {
            permisos = permisos == null ? Set.of() : Set.copyOf(permisos);
        }

        public boolean tienePermiso(String codigo) {
            return permisos.contains(codigo);
        }
    }

    /** Vacio si el usuario no existe. */
    Optional<UsuarioResumen> buscar(UUID usuarioId);

    /** {@code true} solo si la cuenta existe y puede operar ahora mismo. */
    boolean estaActivo(UUID usuarioId);

    /**
     * Usuario de la peticion en curso.
     *
     * <p>Vacio cuando la peticion es anonima. Los modulos lo usan para resolver
     * reglas ABAC de propiedad sin inyectar el {@code SecurityContext} de Spring
     * en su dominio.</p>
     */
    Optional<UsuarioResumen> autenticado();
}