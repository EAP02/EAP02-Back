package com.codefactory.eap02.auth.service;

import com.codefactory.eap02.auth.domain.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitarios puros de JwtService (sin contexto de Spring).
 * Complementan la cobertura: en AuthServiceTest, JwtService esta mockeado, por lo
 * que su codigo real no se ejercita. Aqui se prueba directamente la generacion y
 * firma del token, llevando JwtService al 100 % con solo pruebas unitarias.
 */
class JwtServiceTest {

    private static final String SECRET = "clave-de-prueba-unitaria-jwtservice-1234567890";
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 60);
    }

    private Usuario usuario() {
        Usuario u = new Usuario();
        u.setId(UUID.randomUUID());
        u.setEmail("jwt01@correo.test");
        return u;
    }

    @Test
    void generaTokenConSubjectEmailYExpiracionValidos() {
        Usuario u = usuario();

        String token = jwtService.generateToken(u);

        assertNotNull(token);
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();

        assertEquals(u.getId().toString(), claims.getSubject());
        assertEquals("jwt01@correo.test", claims.get("email", String.class));
        assertNotNull(claims.getIssuedAt());
        assertTrue(claims.getExpiration().after(new Date()),
                "El token recien emitido no debe estar expirado");
    }

    @Test
    void tokenFirmadoNoSeValidaConOtraClave() {
        String token = jwtService.generateToken(usuario());
        SecretKey otraClave = Keys.hmacShaKeyFor("clave-totalmente-distinta-0000000000000000".getBytes());

        assertThrows(io.jsonwebtoken.security.SignatureException.class,
                () -> Jwts.parser().verifyWith(otraClave).build().parseSignedClaims(token),
                "Un token firmado con la clave real no debe validarse con otra clave");
    }
}
