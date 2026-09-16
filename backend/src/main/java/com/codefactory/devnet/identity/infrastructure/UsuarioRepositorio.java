package com.codefactory.devnet.identity.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepositorio extends JpaRepository<UsuarioEntity, UUID> {

    /**
     * Busca por correo sin distinguir mayusculas.
     *
     * <p>Ataca el indice funcional {@code ux_usuario_correo}, que esta definido
     * sobre {@code lower(correo)}. Comparar con {@code =} directo haria un recorrido
     * secuencial y ademas dejaria registrarse a {@code Ana@x.com} y {@code ana@x.com}
     * como cuentas distintas.</p>
     */
    @Query("SELECT u FROM UsuarioEntity u WHERE lower(u.correo) = lower(:correo)")
    Optional<UsuarioEntity> buscarPorCorreo(String correo);

    @Query("SELECT u FROM UsuarioEntity u WHERE lower(u.nombreUsuario) = lower(:nombreUsuario)")
    Optional<UsuarioEntity> buscarPorNombreUsuario(String nombreUsuario);

    boolean existsByCorreoIgnoreCase(String correo);
}