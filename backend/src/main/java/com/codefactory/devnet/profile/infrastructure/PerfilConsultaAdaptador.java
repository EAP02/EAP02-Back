package com.codefactory.devnet.profile.infrastructure;

import com.codefactory.devnet.shared.integration.IPerfilConsulta;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Implementacion de {@link IPerfilConsulta} que provee el modulo {@code profile}.
 */
@Component
public class PerfilConsultaAdaptador implements IPerfilConsulta {

    private final PerfilJpaRepository perfiles;
    private final JdbcTemplate jdbc;

    public PerfilConsultaAdaptador(PerfilJpaRepository perfiles, JdbcTemplate jdbc) {
        this.perfiles = perfiles;
        this.jdbc = jdbc;
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

    @Override
    @Transactional(readOnly = true)
    public int reputacionDe(UUID usuarioId) {
        return perfiles.findById(usuarioId).map(PerfilEntity::getReputacion).orElse(0);
    }

    /**
     * Asienta el evento en el libro mayor y actualiza el saldo denormalizado.
     *
     * <p>Las dos escrituras van en la misma transaccion. Si se separaran, un fallo
     * entre ambas dejaria el saldo desincronizado del libro mayor y la consulta de
     * integridad I1 empezaria a devolver filas.</p>
     */
    @Override
    @Transactional
    public void acreditar(UUID usuarioId, EventoReputacion evento, UUID referencia) {
        jdbc.update("""
                INSERT INTO evento_reputacion (usuario_id, tipo_evento, puntos, referencia_tipo, referencia_id)
                VALUES (?, ?, ?, ?, ?)
                """, usuarioId, evento.name(), evento.puntos(), "PUBLICACION", referencia);

        jdbc.update("UPDATE perfil SET reputacion = reputacion + ? WHERE usuario_id = ?",
                evento.puntos(), usuarioId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean sigue(UUID seguidorId, UUID seguidoId) {
        return perfiles.sigue(seguidorId, seguidoId);
    }
}