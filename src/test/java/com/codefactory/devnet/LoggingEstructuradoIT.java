package com.codefactory.devnet;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-21 · AB#122 — Logging estructurado con traceId, extremo a extremo.
 *
 * <p>Se lee la salida real de la consola con {@link OutputCaptureExtension}: es lo
 * mismo que Render recoge en produccion, con la misma configuracion de
 * {@code application.yml}. Nada de appenders de prueba que podrian formatear distinto.</p>
 *
 * <p>Cubre los cuatro criterios:</p>
 * <ol>
 *   <li>Los logs del backend se generan en formato JSON.</li>
 *   <li>Cada peticion tiene un traceId unico y todos sus logs lo comparten.</li>
 *   <li>Las respuestas de error incluyen el traceId para poder rastrearlas.</li>
 *   <li>Los logs no contienen contrasenas, tokens ni datos personales innecesarios.</li>
 * </ol>
 *
 * <p>Tambien es la "prueba sobre el appender de logs" que ADR-005 promete en su
 * seccion de verificacion.</p>
 */
@PruebaIntegracion
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("HU-21 Logging estructurado con traceId (integracion)")
class LoggingEstructuradoIT {

    private static final String LOGIN = "/api/v1/auth/inicio-sesion";
    private static final String RUTA_MODERACION = "/api/v1/proyectos/%s/ocultamiento";
    private static final String CUERPO_MOTIVO =
            "{\"motivo\": \"Contenido duplicado de otro proyecto ya publicado en la plataforma.\"}";
    private static final String COOKIE_REFRESCO = "devnet_refresco";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SoporteDatosPrueba datos;

    // ==================================================================
    // Criterio 1: formato JSON
    // ==================================================================

    @Test
    @DisplayName("cada linea de log de una peticion es un JSON con los campos de ADR-005")
    void los_logs_salen_en_json(CapturedOutput salida) throws Exception {
        // Act
        String traceId = "0a1b2c3d4e5f6a7b";
        mvc.perform(get("/api/v1/tecnologias").header("X-Trace-Id", traceId))
                .andExpect(status().isOk());

        // Assert: toda la salida de logging es JSON, no solo la de esta peticion
        List<String> lineas = lineasNoVacias(salida);
        assertThat(lineas).isNotEmpty();
        assertThat(lineas).allSatisfy(linea ->
                assertThat(linea).as("linea de log no JSON: %s", linea).startsWith("{"));

        // La linea de cierre trae los campos de primer nivel que promete ADR-005
        Map<String, Object> cierre = lineaDeCierre(lineas, traceId);
        assertThat(cierre)
                .containsEntry("traceId", traceId)
                .containsEntry("metodo", "GET")
                .containsEntry("ruta", "/api/v1/tecnologias")
                .containsEntry("estadoHttp", 200)
                .containsKeys("@timestamp", "level", "logger_name", "duracionMs");
    }

    // ==================================================================
    // Criterios 2 y 3: un traceId por peticion, el mismo en logs y respuesta
    // ==================================================================

    @Test
    @DisplayName("todas las lineas de una peticion fallida comparten el traceId de su respuesta de error")
    void la_respuesta_de_error_y_sus_logs_comparten_trace_id(CapturedOutput salida) throws Exception {
        // Arrange: un desarrollador sin permiso de moderar provoca un 403
        var dev = datos.crear("DESARROLLADOR", false);
        String token = JsonPath.read(iniciarSesion(dev.correo(), SoporteDatosPrueba.CLAVE)
                .getResponse().getContentAsString(), "$.token");

        // Act: sin cabecera X-Trace-Id, el backend genera el identificador
        MvcResult resultado = mvc.perform(post(RUTA_MODERACION.formatted(UUID.randomUUID()))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_MOTIVO))
                .andExpect(status().isForbidden())
                .andExpect(header().exists("X-Trace-Id"))
                .andReturn();

        // Assert criterio 3: el cuerpo de error y la cabecera llevan el mismo traceId
        String traceId = resultado.getResponse().getHeader("X-Trace-Id");
        String traceIdCuerpo = JsonPath.read(resultado.getResponse().getContentAsString(), "$.traceId");
        assertThat(traceIdCuerpo).isEqualTo(traceId);

        // Assert criterio 2: con ese valor se encuentran en el log TODAS las lineas de
        // la peticion: el aviso del manejador global y la linea de cierre del filtro.
        List<Map<String, Object>> deLaPeticion = lineasDe(lineasNoVacias(salida), traceId);
        assertThat(deLaPeticion)
                .extracting(linea -> linea.get("message"))
                .anySatisfy(m -> assertThat((String) m).startsWith("Acceso denegado"))
                .anySatisfy(m -> assertThat(m).isEqualTo("Peticion atendida"));
        assertThat(deLaPeticion)
                .allSatisfy(linea -> assertThat(linea).containsEntry("metodo", "POST"));

        // Y ninguna linea de otra peticion (el login previo) se mezcla con esta
        assertThat(lineasDe(lineasNoVacias(salida), traceId))
                .noneSatisfy(linea -> assertThat(linea).containsEntry("ruta", LOGIN));
    }

    @Test
    @DisplayName("dos peticiones distintas quedan con traceId distintos en el log")
    void cada_peticion_tiene_su_trace_id(CapturedOutput salida) throws Exception {
        // Act
        String primero = mvc.perform(get("/api/v1/tecnologias"))
                .andReturn().getResponse().getHeader("X-Trace-Id");
        String segundo = mvc.perform(get("/api/v1/tecnologias"))
                .andReturn().getResponse().getHeader("X-Trace-Id");

        // Assert
        assertThat(primero).isNotEqualTo(segundo);
        List<String> lineas = lineasNoVacias(salida);
        assertThat(lineaDeCierre(lineas, primero)).containsEntry("traceId", primero);
        assertThat(lineaDeCierre(lineas, segundo)).containsEntry("traceId", segundo);
    }

    // ==================================================================
    // Criterio 4: sin contrasenas, tokens ni datos personales
    // ==================================================================

    @Test
    @DisplayName("ni la contrasena, ni los tokens, ni el correo llegan al log")
    void el_log_no_contiene_secretos_ni_datos_personales(CapturedOutput salida) throws Exception {
        // Arrange
        var dev = datos.crear("DESARROLLADOR", false);
        String claveErronea = "ClaveQueNoDebeVerse-2026!";

        // Act: un login correcto, uno fallido y una peticion autenticada que falla
        MvcResult login = iniciarSesion(dev.correo(), SoporteDatosPrueba.CLAVE);
        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.token");
        Cookie refresco = login.getResponse().getCookie(COOKIE_REFRESCO);

        mvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoLogin(dev.correo(), claveErronea)))
                .andExpect(status().is4xxClientError());

        mvc.perform(post(RUTA_MODERACION.formatted(UUID.randomUUID()))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_MOTIVO))
                .andExpect(status().isForbidden());

        // Assert: se revisa la salida completa, no solo las lineas de una peticion
        String todo = salida.getAll();
        assertThat(todo)
                .as("hubo logs que revisar")
                .contains("Peticion atendida")
                .doesNotContain(SoporteDatosPrueba.CLAVE)
                .doesNotContain(claveErronea)
                .doesNotContain(token)
                .doesNotContain(dev.correo());
        assertThat(refresco).isNotNull();
        assertThat(todo).doesNotContain(refresco.getValue());
    }

    @Test
    @DisplayName("la ruta del log no incluye la query string (terminos de busqueda)")
    void la_ruta_no_incluye_la_query_string(CapturedOutput salida) throws Exception {
        // Act
        String termino = "terminoPrivadoDeBusqueda";
        String traceId = mvc.perform(get("/api/v1/proyectos").param("q", termino))
                .andReturn().getResponse().getHeader("X-Trace-Id");

        // Assert
        assertThat(lineaDeCierre(lineasNoVacias(salida), traceId))
                .containsEntry("ruta", "/api/v1/proyectos");
        assertThat(salida.getAll()).doesNotContain(termino);
    }

    // ------------------------------------------------------------------

    private MvcResult iniciarSesion(String correo, String clave) throws Exception {
        return mvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoLogin(correo, clave)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private static String cuerpoLogin(String correo, String clave) {
        return """
               {"correo": "%s", "clave": "%s"}
               """.formatted(correo, clave);
    }

    private static List<String> lineasNoVacias(CapturedOutput salida) {
        return Arrays.stream(salida.getOut().split("\\R"))
                .filter(linea -> !linea.isBlank())
                .toList();
    }

    /** Las lineas JSON cuyo campo {@code traceId} coincide con el dado. */
    private static List<Map<String, Object>> lineasDe(List<String> lineas, String traceId) {
        return lineas.stream()
                .filter(linea -> linea.startsWith("{"))
                .map(linea -> JsonPath.parse(linea).<Map<String, Object>>read("$"))
                .filter(json -> traceId.equals(json.get("traceId")))
                .toList();
    }

    private static Map<String, Object> lineaDeCierre(List<String> lineas, String traceId) {
        return lineasDe(lineas, traceId).stream()
                .filter(json -> "Peticion atendida".equals(json.get("message")))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no hay linea de cierre para el traceId " + traceId));
    }
}
