package com.codefactory.devnet.profile.infrastructure;

import com.codefactory.devnet.profile.api.PerfilConsulta;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Implementacion de {@link PerfilConsulta} que provee el modulo {@code profile}.
 */
@Component
public class PerfilConsultaAdaptador implements PerfilConsulta {

    private final PerfilJpaRepository perfiles;
    public PerfilConsultaAdaptador(PerfilJpaRepository perfiles) {
        this.perfiles = perfiles;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PerfilResumen> buscar(UUID usuarioId) {
        return perfiles.findById(usuarioId).map(p -> new PerfilResumen(
                p.getUsuarioId(),
                p.getNombreCompleto(),
                p.getUrlAvatar(),
                p.getReputacion(),
                perfiles.estaVerificado(usuarioId),
                perfiles.contarTecnologias(usuarioId)));
    }

}
