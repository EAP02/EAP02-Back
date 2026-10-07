package com.codefactory.devnet;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-004: rotacion del token de refresco y deteccion de reuso, extremo a extremo.
 *
 * <p>Lo que se verifica aqui y no se puede verificar con dobles: que el
 * {@code UPDATE ... WHERE consumido_en IS NULL} realmente impide la segunda rotacion, y
 * que la revocacion de familia <b>sobrevive</b> al rollback de la peticion que la
 * dispara.</p>
 *
 * <p><b>Sin {@code @Transactional}.</b> La anotacion {@code @PruebaIntegracion} lo trae,
 * y aqui estorba: {@code revocarFamilia} confirma en su propia transaccion
 * ({@code REQUIRES_NEW}) y sobrevive al rollback, mientras que el resto no. Con la
 * prueba envuelta en una transaccion, la mitad del estado se revierte y la otra mitad no,
 * y las comprobaciones dejan de significar nada. Se limpia a mano al final.</p>
 *
 * <p><b>Requiere Docker.</b> La ejecuta el {@code maven-failsafe-plugin} en
 * {@code ./mvnw verify}; {@code ./mvnw test} no, porque Surefire solo recoge {@code *Test}.</p>
 */
@PruebaIntegracion
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("ADR-004 Refresco de sesion (integracion)")
class RefrescoIT {

    private static final String LOGIN = "/api/v1/auth/inicio-sesion";
    private static final String REFRESCO = "/api/v1/auth/refresco";
    private static final String CIERRE = "/api/v1/auth/cierre-sesion";
    private static final String COOKIE = "devnet_refresco";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SoporteDatosPrueba datos;

    @Autowired
    private JdbcTemplate jdbc;

    // ==================================================================
    // El camino normal
    // ==================================================================

    @Test
    @DisplayName("el login entrega la cookie con los atributos del ADR-004")
    void el_login_entrega_la_cookie() throws Exception {
        var usuario = datos.crear("DESARROLLADOR", false);
        try {
            // Act
            MvcResult login = iniciarSesion(usuario.correo());

            // Assert
            String cabecera = login.getResponse().getHeader("Set-Cookie");
            assertThat(cabecera)
                    .contains(COOKIE + "=")
                    .contains("HttpOnly")
                    .contains("Secure")
                    .contains("SameSite=Strict")
                    // Acotada a /api/v1/auth: no viaja en el resto de la sesion.
                    .contains("Path=/api/v1/auth")
                    .contains("Max-Age=604800");

            // El refresco NUNCA sale en el cuerpo: eso anularia el HttpOnly.
            assertThat(login.getResponse().getContentAsString())
                    .doesNotContain(valorDe(login));
        } finally {
            limpiar(usuario.id());
        }
    }

    @Test
    @DisplayName("refrescar entrega token nuevo y consume el anterior")
    void refrescar_rota_el_token() throws Exception {
        var usuario = datos.crear("DESARROLLADOR", false);
        try {
            MvcResult login = iniciarSesion(usuario.correo());
            String primero = valorDe(login);

            // Act
            MvcResult renovado = mvc.perform(post(REFRESCO).cookie(new Cookie(COOKIE, primero)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andReturn();

            // Assert
            String segundo = valorDe(renovado);
            assertThat(segundo).isNotEqualTo(primero);

            assertThat(vivos(usuario.id())).isEqualTo(1);
            assertThat(consumidos(usuario.id())).isEqualTo(1);

            // El anterior quedo enlazado con su sucesor: es lo que permite
            // reconstruir la cadena si mas tarde hay que investigar un reuso.
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM token_refresco
                     WHERE usuario_id = ? AND reemplazado_por IS NOT NULL
                    """, Integer.class, usuario.id())).isEqualTo(1);
        } finally {
            limpiar(usuario.id());
        }
    }

    // ==================================================================
    // El escenario que justifica el diseno entero
    // ==================================================================

    @Test
    @DisplayName("usar dos veces el mismo refresco revoca la familia COMPLETA")
    void el_reuso_tumba_la_familia() throws Exception {
        var usuario = datos.crear("DESARROLLADOR", false);
        try {
            MvcResult login = iniciarSesion(usuario.correo());
            String robado = valorDe(login);

            // El titular rota una vez con normalidad.
            MvcResult legitimo = mvc.perform(post(REFRESCO).cookie(new Cookie(COOKIE, robado)))
                    .andExpect(status().isOk())
                    .andReturn();
            String vigente = valorDe(legitimo);

            // Act: alguien mas presenta la copia que ya se uso.
            mvc.perform(post(REFRESCO).cookie(new Cookie(COOKIE, robado)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("AUTH_REFRESCO_REUSADO"));

            // Assert: no queda ni uno vivo en la familia. Incluido el del titular, que
            // era legitimo: no hay forma de saber cual de los dos es el impostor, asi
            // que caen los dos y ambos vuelven a autenticarse.
            assertThat(vivos(usuario.id())).isZero();

            mvc.perform(post(REFRESCO).cookie(new Cookie(COOKIE, vigente)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("AUTH_REFRESCO_REVOCADO"));

            // Y queda constancia, que es lo que V5 hizo posible.
            assertThat(datos.contarEventosAuditoria("REFRESCO_REUSADO", usuario.id()))
                    .isEqualTo(1);
        } finally {
            limpiar(usuario.id());
        }
    }

    // ==================================================================
    // Cierre de sesion
    // ==================================================================

    @Test
    @DisplayName("cerrar sesion revoca la familia y borra la cookie")
    void cerrar_sesion_revoca() throws Exception {
        var usuario = datos.crear("DESARROLLADOR", false);
        try {
            String refresco = valorDe(iniciarSesion(usuario.correo()));

            // Act
            MvcResult cierre = mvc.perform(post(CIERRE).cookie(new Cookie(COOKIE, refresco)))
                    .andExpect(status().isNoContent())
                    .andReturn();

            // Assert
            assertThat(cierre.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");
            assertThat(vivos(usuario.id())).isZero();

            mvc.perform(post(REFRESCO).cookie(new Cookie(COOKIE, refresco)))
                    .andExpect(status().isUnauthorized());
        } finally {
            limpiar(usuario.id());
        }
    }

    @Test
    @DisplayName("cerrar sesion sin cookie tambien responde 204")
    void cerrar_sesion_es_idempotente() throws Exception {
        // Responder distinto segun si el token existe permitiria sondear la tabla.
        mvc.perform(post(CIERRE)).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("refrescar sin cookie: 401 AUTH_REFRESCO_AUSENTE")
    void sin_cookie_no_hay_renovacion() throws Exception {
        mvc.perform(post(REFRESCO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REFRESCO_AUSENTE"));
    }

    // ------------------------------------------------------------------

    private MvcResult iniciarSesion(String correo) throws Exception {
        return mvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"correo": "%s", "clave": "%s"}
                                 """.formatted(correo, SoporteDatosPrueba.CLAVE)))
                .andExpect(status().isOk())
                .andReturn();
    }

    /** El valor de la cookie de refresco de una respuesta. */
    private String valorDe(MvcResult resultado) {
        Cookie cookie = resultado.getResponse().getCookie(COOKIE);
        assertThat(cookie).as("la respuesta debe traer la cookie de refresco").isNotNull();
        return cookie.getValue();
    }

    private int vivos(UUID usuarioId) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM token_refresco
                 WHERE usuario_id = ? AND revocado_en IS NULL AND consumido_en IS NULL
                """, Integer.class, usuarioId);
    }

    private int consumidos(UUID usuarioId) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM token_refresco
                 WHERE usuario_id = ? AND consumido_en IS NOT NULL
                """, Integer.class, usuarioId);
    }

    /** Sin transaccion que revierta, cada prueba recoge lo suyo. */
    private void limpiar(UUID usuarioId) {
        jdbc.update("DELETE FROM token_refresco WHERE usuario_id = ?", usuarioId);
    }
}