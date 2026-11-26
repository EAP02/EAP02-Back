package com.codefactory.devnet;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sprint 3, aseguramiento mediante tokens (lineamientos 3.4 y 6.2).
 *
 * <p>Comprueba lo que un atacante o un cliente descuidado puede hacer con el token de
 * acceso: no enviarlo, enviarlo vencido, alterarlo, presentar uno sin firma
 * ({@code alg=none}) o usar uno legitimo sin el permiso. En todos los casos el servidor
 * responde con el cuerpo uniforme de ADR-005 y un {@code errorCode} que el frontend
 * puede interpretar: {@code AUTH_REQUERIDA} (iniciar sesion) o {@code AUTH_TOKEN_INVALIDO}
 * (renovar con {@code /auth/refresco}).</p>
 *
 * <p><b>Requiere Docker.</b> Lo ejecuta Failsafe en {@code ./mvnw verify}.</p>
 */
@PruebaIntegracion
@DisplayName("Aseguramiento con tokens (integracion)")
class SeguridadTokensIT {

    private static final String RUTA_PROTEGIDA = "/api/v1/conversaciones";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SoporteDatosPrueba datos;

    @Autowired
    private JwtEncoder codificador;

    @Test
    @DisplayName("401 AUTH_REQUERIDA sin token")
    void sin_token() throws Exception {
        mvc.perform(get(RUTA_PROTEGIDA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUERIDA"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("401 AUTH_TOKEN_INVALIDO con un texto que no es un JWT")
    void token_basura() throws Exception {
        mvc.perform(get(RUTA_PROTEGIDA).header("Authorization", "Bearer esto-no-es-un-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_TOKEN_INVALIDO"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("401 AUTH_TOKEN_INVALIDO con un token vencido, aunque la firma sea valida")
    void token_vencido() throws Exception {
        // Firmado con la clave real de la aplicacion: lo unico malo es la fecha.
        Instant haceUnaHora = Instant.now().minus(Duration.ofHours(1));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("devnet")
                .issuedAt(haceUnaHora)
                .expiresAt(haceUnaHora.plus(Duration.ofMinutes(15)))
                .subject(UUID.randomUUID().toString())
                .claim("permisos", java.util.List.of("mensaje:enviar"))
                .build();
        String vencido = codificador.encode(JwtEncoderParameters.from(claims)).getTokenValue();

        mvc.perform(get(RUTA_PROTEGIDA).header("Authorization", "Bearer " + vencido))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_TOKEN_INVALIDO"));
    }

    @Test
    @DisplayName("401 AUTH_TOKEN_INVALIDO con la firma alterada")
    void firma_alterada() throws Exception {
        var ana = datos.crear("DESARROLLADOR", false);
        String[] partes = iniciarSesion(ana.correo()).split("\\.");
        // El primer caracter de la firma aporta seis bits completos: cambiarlo siempre
        // cambia la firma (el ultimo podria solo tocar bits de relleno).
        char original = partes[2].charAt(0);
        char distinto = original == 'A' ? 'B' : 'A';
        String alterado = partes[0] + "." + partes[1] + "." + distinto + partes[2].substring(1);

        mvc.perform(get(RUTA_PROTEGIDA).header("Authorization", "Bearer " + alterado))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_TOKEN_INVALIDO"));
    }

    @Test
    @DisplayName("401 con un token sin firma (alg=none)")
    void algoritmo_none() throws Exception {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String cabecera = b64.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String cuerpo = b64.encodeToString((
                "{\"iss\":\"devnet\",\"sub\":\"" + UUID.randomUUID() + "\","
                        + "\"exp\":" + Instant.now().plus(Duration.ofHours(1)).getEpochSecond() + ","
                        + "\"permisos\":[\"auditoria:consultar\",\"metricas:consultar\"]}")
                .getBytes(StandardCharsets.UTF_8));

        mvc.perform(get("/api/v1/auditoria").header("Authorization", "Bearer " + cabecera + "." + cuerpo + "."))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("403 ACCESO_DENEGADO con un token valido pero sin el permiso")
    void token_valido_sin_permiso() throws Exception {
        var ana = datos.crear("DESARROLLADOR", false);

        mvc.perform(get("/api/v1/auditoria")
                        .header("Authorization", "Bearer " + iniciarSesion(ana.correo())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESO_DENEGADO"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("El token de acceso no es una credencial de refresco")
    void token_de_acceso_no_refresca() throws Exception {
        var ana = datos.crear("DESARROLLADOR", false);

        // /auth/refresco solo acepta la cookie de refresco (ADR-004): un Bearer no la reemplaza.
        mvc.perform(post("/api/v1/auth/refresco")
                        .header("Authorization", "Bearer " + iniciarSesion(ana.correo())))
                .andExpect(status().isUnauthorized());
    }

    private String iniciarSesion(String correo) throws Exception {
        MvcResult resultado = mvc.perform(post("/api/v1/auth/inicio-sesion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\": \"" + correo
                                + "\", \"clave\": \"" + SoporteDatosPrueba.CLAVE + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(resultado.getResponse().getContentAsString(), "$.token");
    }
}
