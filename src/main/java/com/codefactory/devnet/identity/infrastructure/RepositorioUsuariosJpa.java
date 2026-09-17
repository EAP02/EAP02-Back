package com.codefactory.devnet.identity.infrastructure;

import com.codefactory.devnet.identity.domain.RepositorioUsuarios;
import com.codefactory.devnet.identity.domain.Usuario;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Adaptador JPA del puerto {@link RepositorioUsuarios}.
 *
 * <p>Traduce entre {@link UsuarioEntity} (fila) y {@link Usuario} (reglas). Ninguna
 * entidad JPA cruza hacia {@code application}: eso es lo que verifica la regla 3 de
 * {@code FronterasModularesTest}.</p>
 */
@Component
public class RepositorioUsuariosJpa implements RepositorioUsuarios {

    private final UsuarioRepositorio jpa;
    private final RolRepositorio roles;

    public RepositorioUsuariosJpa(UsuarioRepositorio jpa, RolRepositorio roles) {
        this.jpa = jpa;
        this.roles = roles;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Usuario> porCorreo(String correo) {
        return jpa.buscarPorCorreo(correo).map(UsuarioEntity::aDominio);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Usuario> porId(UUID id) {
        return jpa.findById(id).map(UsuarioEntity::aDominio);
    }

    @Override
    public boolean existeCorreo(String correo) {
        return jpa.existsByCorreoIgnoreCase(correo);
    }

    @Override
    public boolean existeNombreUsuario(String nombreUsuario) {
        return jpa.existsByNombreUsuarioIgnoreCase(nombreUsuario);
    }

    @Override
    @Transactional
    public Usuario crear(String correo, String nombreUsuario, String claveHash) {
        UsuarioEntity entidad = UsuarioEntity.nueva(correo, nombreUsuario, claveHash);
        roles.findByCodigo("DESARROLLADOR").ifPresent(entidad::asignarRol);
        return jpa.save(entidad).aDominio();
    }

    /**
     * Persiste intentos fallidos, bloqueo y ultimo acceso.
     *
     * <p>{@code REQUIRES_NEW} no aplica aqui, pero si un detalle importante: al
     * autenticar mal, el caso de uso lanza una excepcion y la transaccion de la
     * peticion revierte. Por eso este metodo se invoca <b>antes</b> de lanzar y con
     * el contador ya actualizado; de lo contrario, un atacante podria intentar
     * indefinidamente sin que el contador subiera nunca.</p>
     */
    @Override
    @Transactional
    public void guardarEstadoAcceso(Usuario usuario) {
        jpa.findById(usuario.id()).ifPresent(entidad -> {
            entidad.aplicarEstadoAcceso(usuario);
            jpa.save(entidad);
        });
    }
}
