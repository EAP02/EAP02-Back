package com.codefactory.devnet.messaging.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RepositorioMensajeria {
    UUID obtenerOCrearDirecta(UUID remitenteId, UUID destinatarioId);
    Mensaje guardar(UUID conversacionId, UUID remitenteId, String contenido);
    boolean participa(UUID conversacionId, UUID usuarioId);
    List<ConversacionResumen> bandeja(UUID usuarioId);
    List<Mensaje> historial(UUID conversacionId);

    record ConversacionResumen(UUID conversacionId, UUID otroParticipanteId, Instant ultimoMensajeEn) { }
}
