package com.codefactory.devnet.messaging.application;

import com.codefactory.devnet.identity.api.UsuarioDirectorio;
import com.codefactory.devnet.messaging.domain.CodigoErrorMensajeria;
import com.codefactory.devnet.messaging.domain.Mensaje;
import com.codefactory.devnet.messaging.domain.RepositorioMensajeria;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import com.codefactory.devnet.shared.audit.EventoAuditoria;
import com.codefactory.devnet.shared.audit.RegistroAuditoria;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Service
public class EnviarMensaje {
    private final RepositorioMensajeria mensajes;
    private final UsuarioDirectorio directorio;
    private final RegistroAuditoria auditoria;

    public EnviarMensaje(RepositorioMensajeria mensajes, UsuarioDirectorio directorio,
                         RegistroAuditoria auditoria) {
        this.mensajes = mensajes;
        this.directorio = directorio;
        this.auditoria = auditoria;
    }

    @Transactional
    public Mensaje ejecutar(UUID destinatarioId, String contenido) {
        UUID remitenteId = directorio.autenticado()
                .orElseThrow(() -> ExcepcionNegocio.accesoDenegado("sin_sesion")).id();
        if (destinatarioId == null) {
            throw new ExcepcionNegocio(CodigoErrorMensajeria.MENSAJE_DESTINATARIO_INVALIDO);
        }
        if (remitenteId.equals(destinatarioId)) {
            throw new ExcepcionNegocio(CodigoErrorMensajeria.MENSAJE_PROPIO);
        }
        String texto = Mensaje.validarContenido(contenido);
        if (!directorio.estaActivo(remitenteId)) {
            throw new ExcepcionNegocio(CodigoErrorMensajeria.MENSAJE_REMITENTE_INACTIVO);
        }
        if (!directorio.estaActivo(destinatarioId)) {
            throw ExcepcionNegocio.noEncontrado("usuario", destinatarioId);
        }

        UUID conversacionId = mensajes.obtenerOCrearDirecta(remitenteId, destinatarioId);
        Mensaje guardado = mensajes.guardar(conversacionId, remitenteId, texto);
        // La auditoria usa otra transaccion: registrar solo tras confirmar el mensaje.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                auditoria.registrar(EventoAuditoria.mensajeEnviado(guardado.id(), remitenteId));
            }
        });
        return guardado;
    }
}
