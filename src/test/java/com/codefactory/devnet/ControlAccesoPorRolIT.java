package com.codefactory.devnet;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-06 (AB#15): control de acceso por rol, verificado extremo a extremo.
 *
 * <p>Cubre los tres criterios de aceptacion:</p>
 * <ol>
 *   <li>un usuario sin permiso recibe 403 y el intento queda registrado;</li>
 *   <li>un moderador puede ejecutar las acciones permitidas a su rol;</li>
 *   <li>la autorizacion se verifica en el servidor ante peticiones directas.</li>
 * </ol>
 *
 * <p>Se lee el JSON con {@code JsonPath} y no con un {@code ObjectMapper} inyectado:
 * Boot 4 autoconfigura Jackson 3, cuyo mapper vive en {@code tools.jackson}. JsonPath
 * viene con {@code spring-boot-starter-test} y no depende de esa version.</p>
 */
@PruebaIntegracion
@DisplayName("HU-06 Control de acceso por rol (integracion)")
class ControlAccesoPorRolIT {

    private static final String RUTA_MODERACION = "/api/v1/proyectos/%s/ocultamiento";
    private static final String MOTIVO_VALIDO =
            "Contenido duplicado de otro proyecto ya publicado en la plataforma.";
    private static final String CUERPO_MOTIVO = "{\"motivo\": \"" + MOTIVO_VALIDO + "\"}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SoporteDatosPrueba datos;

    // ==================================================================
    // Criterio 1: sin permiso -> 403 y queda registrado el intento
    // ==================================================================

    @Test
    @DisplayName("un desarrollador que intenta moderar recibe 403")
    void desarrollador_no_puede_moderar() throws Exception {
        // Arrange
        var dev = datos.crear("DESARROLLADOR", false);
        String token = autenticar(dev.correo());

        // Act + Assert
        mvc.perform(post(RUTA_MODERACION.formatted(UUID.randomUUID()))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_MOTIVO))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESO_DENEGADO"))
                // El cuerpo de error cumple el contrato de ADR-005
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(header().exists("X-Trace-Id"));
    }

    @Test
    @DisplayName("el intento denegado queda registrado en auditoria")
    void el_intento_denegado_se_audita() throws Exception {
        // Arrange
        var dev = datos.crear("DESARROLLADOR", false);
        String token = autenticar(dev.correo());
        int antes = datos.contarEventosAuditoria("ACCESO_DENEGADO", dev.id());

        // Act
        mvc.perform(post(RUTA_MODERACION.formatted(UUID.randomUUID()))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_MOTIVO))
                .andExpect(status().isForbidden());

        // Assert: criterio 1, "queda registrado el intento".
        // La auditoria se escribe en transaccion propia (REQUIRES_NEW), asi que
        // sobrevive al rollback de la peticion.
        assertThat(datos.contarEventosAuditoria("ACCESO_DENEGADO", dev.id()))
                .as("evento ACCESO_DENEGADO registrado para el actor")
                .isEqualTo(antes + 1);
    }

    // ==================================================================
    // Criterio 2: un moderador SI puede ejecutar lo suyo
    // ==================================================================

    @Test
    @DisplayName("un moderador con MFA inscrito recibe el permiso de moderar")
    void moderador_obtiene_permiso_de_moderar() throws Exception {
        // Arrange
        var moderador = datos.crear("MODERADOR", true);

        // Act
        String respuesta = iniciarSesion(moderador.correo());

        // Assert: criterio 2
        assertThat(JsonPath.<List<String>>read(respuesta, "$.permisos"))
                .contains("publicacion:moderar");
        assertThat(JsonPath.<Boolean>read(respuesta, "$.mfaPendiente")).isFalse();
        assertThat(JsonPath.<List<String>>read(respuesta, "$.roles")).contains("MODERADOR");
    }

    @Test
    @DisplayName("un moderador que modera un proyecto inexistente recibe 404, no 403")
    void moderador_pasa_la_autorizacion() throws Exception {
        // Arrange
        var moderador = datos.crear("MODERADOR", true);
        String token = autenticar(moderador.correo());

        // Act + Assert: el 404 demuestra que SI paso el control de acceso y fallo
        // despues, al no encontrar el recurso. Un 403 significaria lo contrario.
        mvc.perform(post(RUTA_MODERACION.formatted(UUID.randomUUID()))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_MOTIVO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PROYECTO_NO_ENCONTRADO"));
    }

    @Test
    @DisplayName("un moderador SIN MFA inscrito no ejerce ningun permiso")
    void moderador_sin_mfa_no_ejerce_permisos() throws Exception {
        // Arrange
        var moderador = datos.crear("MODERADOR", false);

        // Act
        String respuesta = iniciarSesion(moderador.correo());

        // Assert: lineamiento 3.4. Se autentica, pero el token sale sin permisos.
        assertThat(JsonPath.<Boolean>read(respuesta, "$.mfaPendiente")).isTrue();
        assertThat(JsonPath.<List<String>>read(respuesta, "$.permisos")).isEmpty();

        // Y en consecuencia, la accion de su rol le es denegada
        mvc.perform(post(RUTA_MODERACION.formatted(UUID.randomUUID()))
                        .header("Authorization", "Bearer " + JsonPath.<String>read(respuesta, "$.token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_MOTIVO))
                .andExpect(status().isForbidden());
    }

    // ==================================================================
    // Criterio 3: se verifica en el servidor, siempre
    // ==================================================================

    @Test
    @DisplayName("sin token, la peticion directa al endpoint recibe 401")
    void sin_token_no_hay_acceso() throws Exception {
        // Act + Assert
        mvc.perform(post(RUTA_MODERACION.formatted(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_MOTIVO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_REQUERIDA"));
    }

    @Test
    @DisplayName("un token manipulado se rechaza con 401")
    void token_manipulado_se_rechaza() throws Exception {
        // Arrange: token valido con la firma alterada
        var moderador = datos.crear("MODERADOR", true);
        String token = autenticar(moderador.correo());
        String manipulado = token.substring(0, token.length() - 6) + "AAAAAA";

        // Act + Assert: la firma RS256 es lo que hace imposible fabricar permisos
        mvc.perform(post(RUTA_MODERACION.formatted(UUID.randomUUID()))
                        .header("Authorization", "Bearer " + manipulado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_MOTIVO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("credenciales incorrectas no revelan si el correo existe")
    void no_se_puede_enumerar_cuentas() throws Exception {
        // Arrange
        var dev = datos.crear("DESARROLLADOR", false);

        // Act + Assert: correo existente con clave mala...
        mvc.perform(post("/api/v1/auth/inicio-sesion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\": \"" + dev.correo() + "\", \"clave\": \"ClaveIncorrecta1!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_CREDENCIALES_INVALIDAS"));

        // ...y correo inexistente devuelven EXACTAMENTE el mismo codigo
        mvc.perform(post("/api/v1/auth/inicio-sesion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\": \"nadie@prueba.test\", \"clave\": \"ClaveIncorrecta1!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_CREDENCIALES_INVALIDAS"));
    }

    // ==================================================================

    private String autenticar(String correo) throws Exception {
        return JsonPath.read(iniciarSesion(correo), "$.token");
    }

    private String iniciarSesion(String correo) throws Exception {
        MvcResult resultado = mvc.perform(post("/api/v1/auth/inicio-sesion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\": \"" + correo
                                + "\", \"clave\": \"" + SoporteDatosPrueba.CLAVE + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return resultado.getResponse().getContentAsString();
    }
}