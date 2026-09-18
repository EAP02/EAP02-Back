package com.codefactory.devnet.identity.domain;

/**
 * Puerto de generacion de tokens de refresco.
 *
 * <p>Separado de {@link EmisorTokens} a proposito. Aquel emite JWT firmados con RS256 y
 * su contrato lo dice; un refresco es un valor opaco sin estructura ni firma, y no
 * comparte con el ni la tecnologia ni el ritmo al que cambia. Rotar el par RSA y cambiar
 * el algoritmo de hash del refresco son decisiones independientes.</p>
 */
public interface GeneradorTokenRefresco {

    /**
     * @param valor el token en claro. Viaja al titular y no se persiste.
     * @param hash  lo unico que se guarda. Un volcado de la tabla no permite
     *              suplantar a nadie.
     */
    record Generado(String valor, String hash) { }

    Generado generar();

    /**
     * Hash del valor recibido del cliente, para buscarlo en la tabla.
     *
     * <p><b>Determinista.</b> Es lo que permite atacar el indice unico de
     * {@code token_hash} con una sola lectura. Un hash con sal aleatoria —Argon2, el que
     * usa este proyecto para contrasenas— obligaria a recorrer la tabla verificando fila
     * por fila.</p>
     */
    String hashDe(String valorEnClaro);
}