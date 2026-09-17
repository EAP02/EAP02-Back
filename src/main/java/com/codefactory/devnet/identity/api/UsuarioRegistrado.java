package com.codefactory.devnet.identity.api;

import java.util.UUID;

/** Evento publico emitido por identity; profile lo consume sin acceder a sus tablas. */
public record UsuarioRegistrado(UUID usuarioId, String nombreUsuario) { }
