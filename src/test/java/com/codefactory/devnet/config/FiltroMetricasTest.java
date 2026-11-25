package com.codefactory.devnet.config;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-010: el endpoint de metricas solo responde con el token del scraper.
 *
 * <p>Unitaria y sin contexto de Spring: el filtro no depende de Actuator, asi que la
 * prueba tampoco.</p>
 */
@DisplayName("FiltroMetricas")
class FiltroMetricasTest {

    private static final String TOKEN = "token-de-prueba";

    private MockHttpServletResponse ejecutar(FiltroMetricas filtro, String ruta, String cabecera)
            throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", ruta);
        peticion.setRequestURI(ruta);
        if (cabecera != null) {
            peticion.addHeader("Authorization", cabecera);
        }
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        filtro.doFilter(peticion, respuesta, new MockFilterChain());
        return respuesta;
    }

    @Test
    @DisplayName("deja pasar la peticion con el token correcto")
    void token_correcto() throws Exception {
        var respuesta = ejecutar(new FiltroMetricas(TOKEN), "/actuator/prometheus", "Bearer " + TOKEN);

        assertThat(respuesta.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    @DisplayName("401 sin cabecera Authorization")
    void sin_token() throws Exception {
        var respuesta = ejecutar(new FiltroMetricas(TOKEN), "/actuator/prometheus", null);

        assertThat(respuesta.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
        assertThat(respuesta.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
    }

    @Test
    @DisplayName("401 con un token distinto")
    void token_incorrecto() throws Exception {
        var respuesta = ejecutar(new FiltroMetricas(TOKEN), "/actuator/prometheus", "Bearer otro-token");

        assertThat(respuesta.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    @DisplayName("401 con un esquema que no es Bearer")
    void esquema_incorrecto() throws Exception {
        var respuesta = ejecutar(new FiltroMetricas(TOKEN), "/actuator/prometheus", "Basic " + TOKEN);

        assertThat(respuesta.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    @DisplayName("falla cerrado: con el token configurado vacio rechaza incluso un Bearer vacio")
    void token_configurado_vacio() throws Exception {
        var respuesta = ejecutar(new FiltroMetricas(""), "/actuator/prometheus", "Bearer ");

        assertThat(respuesta.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    @DisplayName("no interviene en otras rutas")
    void otras_rutas() throws Exception {
        var respuesta = ejecutar(new FiltroMetricas(TOKEN), "/api/v1/tecnologias", null);

        assertThat(respuesta.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }
}
