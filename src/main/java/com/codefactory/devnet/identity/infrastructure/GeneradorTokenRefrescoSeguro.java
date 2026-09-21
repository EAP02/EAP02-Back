package com.codefactory.devnet.identity.infrastructure;

import com.codefactory.devnet.identity.domain.GeneradorTokenRefresco;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Adaptador de {@link GeneradorTokenRefresco}: valor aleatorio de 256 bits, hash
 * SHA-256.
 *
 * <h2>Por que SHA-256 y no Argon2id, si el proyecto usa Argon2id para contrasenas</h2>
 *
 * <p>Tres razones, y las tres importan:</p>
 *
 * <ol>
 *   <li><b>Debe ser determinista.</b> El token entrante se busca por su hash contra el
 *       indice unico de {@code token_hash}. Argon2 lleva sal aleatoria: el mismo valor
 *       produce hashes distintos, lo que obligaria a recorrer la tabla verificando fila
 *       por fila.</li>
 *   <li><b>No hay diccionario que atacar.</b> Argon2 es lento a proposito porque una
 *       contrasena humana tiene poca entropia y hay que encarecer cada intento. Esto son
 *       256 bits de {@link SecureRandom}: adivinarlo por fuerza bruta no es viable por
 *       mucho que el hash sea barato.</li>
 *   <li><b>Esta en la ruta critica.</b> Argon2 con los parametros de OWASP cuesta del
 *       orden de 100 ms. Pagarlos en cada renovacion de token, cada quince minutos y por
 *       usuario, no compra nada.</li>
 * </ol>
 *
 * <p>Lo que si aporta el hash es que un volcado de la tabla no sirve para suplantar a
 * nadie: quien lo lea tiene el hash, y el token que viaja en la cookie es el valor.</p>
 */
@Component
public class GeneradorTokenRefrescoSeguro implements GeneradorTokenRefresco {

    /** 256 bits. El mismo tamano que la salida del hash: no tiene sentido pedir menos. */
    private static final int BYTES_TOKEN = 32;

    private static final String ALGORITMO_HASH = "SHA-256";

    private final SecureRandom aleatorio = new SecureRandom();

    @Override
    public Generado generar() {
        byte[] bytes = new byte[BYTES_TOKEN];
        aleatorio.nextBytes(bytes);

        // Sin relleno: el '=' final es legal en una cookie pero obliga a citarla en
        // algunos clientes. Sin el son 43 caracteres de alfabeto url-safe.
        String valor = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        return new Generado(valor, hashDe(valor));
    }

    @Override
    public String hashDe(String valorEnClaro) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITMO_HASH);
            byte[] hash = digest.digest(valorEnClaro.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 es obligatorio en toda implementacion de la plataforma Java. Si
            // falta, el problema no es este metodo.
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", ex);
        }
    }
}