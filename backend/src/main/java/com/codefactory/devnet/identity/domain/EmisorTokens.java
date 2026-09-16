package com.codefactory.devnet.identity.domain;

import java.time.Instant;

/**
 * Puerto de emision de tokens de acceso.
 *
 * <p>El dominio decide <i>que</i> va dentro (el usuario y sus permisos efectivos) y
 * deja el <i>como</i> a infraestructura: el formato JWT, el algoritmo RS256 y la
 * libreria de firma son decisiones tecnicas que no pertenecen aqui.</p>
 */
public interface EmisorTokens {

    TokenAcceso emitir(Usuario usuario);

    /**
     * @param valor            token firmado
     * @param expiraEn         momento de vencimiento
     * @param vigenciaSegundos segundos de vida, para que el cliente sepa cuando renovar
     */
    record TokenAcceso(String valor, Instant expiraEn, long vigenciaSegundos) { }
}