/**
 * Mensajeria: conversaciones privadas, envio de mensajes y conteo de no leidos.
 *
 * <p>Capas de este modulo (ADR-001):</p>
 * <ul>
 *   <li>{@code api}</li>
 *   <li>{@code application}</li>
 *   <li>{@code domain}</li>
 *   <li>{@code infrastructure}</li>
 * </ul>
 *
 * <p>Este modulo NO importa ningun otro modulo de negocio. Los contratos
 * entre modulos viven en {@code com.codefactory.devnet.shared.integration}.</p>
 */
package com.codefactory.devnet.messaging;
