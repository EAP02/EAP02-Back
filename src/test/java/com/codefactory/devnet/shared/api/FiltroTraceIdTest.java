package com.codefactory.devnet.shared.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU-21 · AB#122: el identificador por peticion, sin levantar Spring.
 *
 * <p>Lo que la prueba de integracion no ve con facilidad: que el valor entrante se
 * valida antes de llegar al log y que el MDC queda limpio al terminar, que es lo que
 * impide que una peticion herede el {@code traceId} de la anterior en el mismo hilo
 * del pool (criterio 2, "cada peticion tiene un traceId unico").</p>
 */
@DisplayName("HU-21 FiltroTraceId")
class FiltroTraceIdTest {

    private final FiltroTraceId filtro = new FiltroTraceId();

    @Test
    @DisplayName("respeta un traceId entrante valido y lo devuelve en la cabecera")
    void respeta_el_trace_id_entrante() throws Exception {
        // Arrange
        var peticion = new MockHttpServletRequest("GET", "/api/v1/proyectos");
        peticion.addHeader(FiltroTraceId.CABECERA, "ABCDEF0123456789");
        var respuesta = new MockHttpServletResponse();
        var visto = new AtomicReference<String>();

        // Act
        filtro.doFilter(peticion, respuesta,
                new MockFilterChain() {
                    @Override
                    public void doFilter(jakarta.servlet.ServletRequest req,
                                         jakarta.servlet.ServletResponse res) {
                        visto.set(MDC.get(FiltroTraceId.MDC_TRACE_ID));
                    }
                });

        // Assert: normalizado a minusculas, igual en el MDC y en la respuesta
        assertThat(visto.get()).isEqualTo("abcdef0123456789");
        assertThat(respuesta.getHeader(FiltroTraceId.CABECERA)).isEqualTo("abcdef0123456789");
    }

    @Test
    @DisplayName("descarta un traceId con saltos de linea y genera uno propio")
    void descarta_un_trace_id_que_falsificaria_el_log() throws Exception {
        // Arrange: un cliente intenta inyectar una linea de log falsa
        var peticion = new MockHttpServletRequest("GET", "/api/v1/proyectos");
        peticion.addHeader(FiltroTraceId.CABECERA, "abcd1234\n{\"level\":\"INFO\"}");
        var respuesta = new MockHttpServletResponse();

        // Act
        filtro.doFilter(peticion, respuesta, new MockFilterChain());

        // Assert
        assertThat(respuesta.getHeader(FiltroTraceId.CABECERA))
                .matches("^[0-9a-f]{1,16}$")
                .doesNotContain("\n");
    }

    @Test
    @DisplayName("dos peticiones sin cabecera reciben traceId distintos")
    void cada_peticion_recibe_su_propio_trace_id() throws Exception {
        // Arrange
        var primera = new MockHttpServletResponse();
        var segunda = new MockHttpServletResponse();

        // Act
        filtro.doFilter(new MockHttpServletRequest("GET", "/a"), primera, new MockFilterChain());
        filtro.doFilter(new MockHttpServletRequest("GET", "/b"), segunda, new MockFilterChain());

        // Assert
        assertThat(primera.getHeader(FiltroTraceId.CABECERA))
                .isNotBlank()
                .isNotEqualTo(segunda.getHeader(FiltroTraceId.CABECERA));
    }

    @Test
    @DisplayName("limpia el MDC al terminar, aunque la cadena lance una excepcion")
    void limpia_el_mdc_al_terminar() {
        // Arrange
        var peticion = new MockHttpServletRequest("POST", "/api/v1/proyectos");
        var respuesta = new MockHttpServletResponse();

        // Act
        try {
            filtro.doFilter(peticion, respuesta, new MockFilterChain() {
                @Override
                public void doFilter(jakarta.servlet.ServletRequest req,
                                     jakarta.servlet.ServletResponse res) {
                    throw new IllegalStateException("fallo simulado");
                }
            });
        } catch (Exception esperado) {
            // La excepcion no es lo que se prueba aqui
        }

        // Assert: el hilo vuelve al pool sin el contexto de esta peticion
        assertThat(MDC.get(FiltroTraceId.MDC_TRACE_ID)).isNull();
        assertThat(MDC.get(FiltroTraceId.MDC_RUTA)).isNull();
        assertThat(MDC.get(FiltroTraceId.MDC_METODO)).isNull();
        assertThat(FiltroTraceId.actual()).isEqualTo("sin-traza");
    }
}
