package com.codefactory.devnet.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

/**
 * Firma y verificacion de los JWT de acceso.
 *
 * <p>RS256 y no HS256: con clave asimetrica, verificar un token no exige poder
 * emitirlo. Si manana un servicio de solo lectura necesita validar tokens, recibe la
 * publica y nunca ve la privada (ADR-004).</p>
 *
 * <p><b>Sin claves configuradas se genera un par efimero</b> y se avisa por log. Eso
 * hace que el proyecto arranque recien clonado, sin ceremonia previa, a costa de que
 * los tokens no sobrevivan a un reinicio: aceptable en local y en pruebas, inadmisible
 * en produccion. El perfil {@code prod} exige {@code JWT_CLAVE_PRIVADA} y
 * {@code JWT_CLAVE_PUBLICA} y no arranca sin ellas.</p>
 */
@Configuration
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    @Value("${devnet.seguridad.jwt.clave-privada:}")
    private String clavePrivadaPem;

    @Value("${devnet.seguridad.jwt.clave-publica:}")
    private String clavePublicaPem;

    @Bean
    public RSAKey claveRsa() throws Exception {
        if (clavePrivadaPem.isBlank() || clavePublicaPem.isBlank()) {
            log.warn("""
                    No hay claves JWT configuradas: se genera un par RSA EFIMERO.
                    Los tokens emitidos dejaran de ser validos al reiniciar la aplicacion.
                    Aceptable en local y en pruebas. En produccion define JWT_CLAVE_PRIVADA
                    y JWT_CLAVE_PUBLICA (ver .env.example).""");
            return generarEfimera();
        }
        return RSAKey.parseFromPEMEncodedObjects(clavePublicaPem + "\n" + clavePrivadaPem)
                .toRSAKey();
    }

    @Bean
    public JwtEncoder codificadorJwt(RSAKey clave) {
        JWKSource<SecurityContext> fuente = new ImmutableJWKSet<>(new JWKSet(clave));
        return new NimbusJwtEncoder(fuente);
    }

    @Bean
    public JwtDecoder decodificadorJwt(RSAKey clave) throws Exception {
        return NimbusJwtDecoder.withPublicKey(clave.toRSAPublicKey()).build();
    }

    private RSAKey generarEfimera() throws Exception {
        KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
        generador.initialize(2048);
        KeyPair par = generador.generateKeyPair();
        return new RSAKey.Builder((RSAPublicKey) par.getPublic())
                .privateKey((RSAPrivateKey) par.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
    }
}