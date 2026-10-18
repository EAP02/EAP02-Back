package com.codefactory.devnet.messaging.application;

import com.codefactory.devnet.identity.api.UsuarioDirectorio;
import com.codefactory.devnet.messaging.domain.Mensaje;
import com.codefactory.devnet.messaging.domain.RepositorioMensajeria;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import com.codefactory.devnet.shared.audit.EventoAuditoria;
import com.codefactory.devnet.shared.audit.RegistroAuditoria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ConsultarMensajeria {
    private final RepositorioMensajeria mensajes;
    private final UsuarioDirectorio directorio;
    private final RegistroAuditoria auditoria;

    public ConsultarMensajeria(RepositorioMensajeria mensajes, UsuarioDirectorio directorio,
                               RegistroAuditoria auditoria) {
        this.mensajes = mensajes;
        this.directorio = directorio;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<RepositorioMensajeria.ConversacionResumen> bandeja() {
        UUID usuarioId = directorio.autenticado()
                .orElseThrow(() -> ExcepcionNegocio.accesoDenegado("sin_sesion")).id();
        return mensajes.bandeja(usuarioId);
    }

    @Transactional(readOnly = true)
    public List<Mensaje> historial(UUID conversacionId, String ip) {
        UUID usuarioId = directorio.autenticado()
                .orElseThrow(() -> ExcepcionNegocio.accesoDenegado("sin_sesion")).id();
        if (!mensajes.participa(conversacionId, usuarioId)) {
            auditoria.registrar(EventoAuditoria.accesoDenegado(
                    "GET /api/v1/conversaciones/{id}/mensajes", usuarioId, ip));
            // ADR-005: misma respuesta para recurso inexistente y recurso privado ajeno.
            throw ExcepcionNegocio.noEncontrado("conversacion", conversacionId);
        }
        return mensajes.historial(conversacionId);
    }
}
