package com.codefactory.devnet.identity.infrastructure;

import com.codefactory.devnet.identity.domain.RepositorioUsuarios;
import com.codefactory.devnet.identity.domain.Usuario;
import com.codefactory.devnet.shared.integration.IUsuarioDirectorio;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementacion de {@link IUsuarioDirectorio} que provee el modulo {@code identity}.
 *
 * <p>Es la unica puerta por la que el resto de modulos ve la identidad. Ninguno
 * conoce {@link UsuarioEntity} ni sabe que hay JPA detras.</p>
 */
@Component
public class DirectorioUsuarioAdaptador implements IUsuarioDirectorio {

    private final RepositorioUsuarios usuarios;

    public DirectorioUsuarioAdaptador(RepositorioUsuarios usuarios) {
        this.usuarios = usuarios;
    }

    @Override
    public Optional<UsuarioResumen> buscar(UUID usuarioId) {
        return usuarios.porId(usuarioId).map(this::aResumen);
    }

    @Override
    public boolean estaActivo(UUID usuarioId) {
        return usuarios.porId(usuarioId).map(Usuario::puedeEscribir).orElse(false);
    }

    /**
     * Usuario de la peticion en curso, reconstruido desde el token.
     *
     * <p>No consulta la base: los permisos ya viajan firmados en el JWT. Es lo que
     * hace que autorizar no cueste una consulta por endpoint.</p>
     *
     * <p>El precio conocido es la latencia de revocacion: un cambio de rol no surte
     * efecto hasta que el token de acceso vence. Por eso vive 15 minutos (ADR-004).</p>
     */
    @Override
    public Optional<UsuarioResumen> autenticado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }

        Set<String> permisos = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());

        return Optional.of(new UsuarioResumen(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString(ServicioJwt.CLAIM_NOMBRE_USUARIO),
                true,
                permisos));
    }

    private UsuarioResumen aResumen(Usuario u) {
        return new UsuarioResumen(u.id(), u.nombreUsuario(), u.puedeEscribir(), u.permisosEfectivos());
    }
}