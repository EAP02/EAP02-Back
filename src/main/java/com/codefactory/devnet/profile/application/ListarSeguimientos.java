package com.codefactory.devnet.profile.application;

import com.codefactory.devnet.identity.api.UsuarioDirectorio;
import com.codefactory.devnet.profile.domain.RepositorioSeguimientos;
import com.codefactory.devnet.profile.domain.Seguimiento;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * HU-15: las listas de seguidos y de seguidores de un usuario. Lectura publica.
 *
 * <p>El nombre de usuario pertenece a identity: se pide por el directorio, no se lee
 * su tabla. Una cuenta que ya no existe en el directorio no aparece.</p>
 */
@Service
public class ListarSeguimientos {

    private final RepositorioSeguimientos seguimientos;
    private final UsuarioDirectorio directorio;

    public ListarSeguimientos(RepositorioSeguimientos seguimientos, UsuarioDirectorio directorio) {
        this.seguimientos = seguimientos;
        this.directorio = directorio;
    }

    /** A quienes sigue {@code usuarioId}. */
    @Transactional(readOnly = true)
    public List<Miembro> seguidos(UUID usuarioId) {
        exigirExistente(usuarioId);
        return aMiembros(seguimientos.seguidosPor(usuarioId), Seguimiento::seguidoId);
    }

    /** Quienes siguen a {@code usuarioId}. */
    @Transactional(readOnly = true)
    public List<Miembro> seguidores(UUID usuarioId) {
        exigirExistente(usuarioId);
        return aMiembros(seguimientos.seguidoresDe(usuarioId), Seguimiento::seguidorId);
    }

    private void exigirExistente(UUID usuarioId) {
        if (directorio.buscar(usuarioId).isEmpty()) {
            throw ExcepcionNegocio.noEncontrado("usuario", usuarioId);
        }
    }

    private List<Miembro> aMiembros(List<Seguimiento> filas, Function<Seguimiento, UUID> cual) {
        return filas.stream()
                .map(s -> directorio.buscar(cual.apply(s))
                        .map(u -> new Miembro(u.id(), u.nombreUsuario(), s.creadoEn())))
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    public record Miembro(UUID usuarioId, String nombreUsuario, Instant desde) { }
}
