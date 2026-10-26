package com.codefactory.devnet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registro de cuentas, verificado extremo a extremo.
 *
 * <p>Lo que solo se ve con la base real: que el nombre de usuario que se guarda cumple
 * {@code ck_usuario_nombre_usuario_formato}, que la cuenta nace con su perfil vacio
 * (evento {@code UsuarioRegistrado} hacia el modulo de perfil) y que puede iniciar
 * sesion en cuanto existe.</p>
 *
 * <p><b>Requiere Docker.</b> Lo ejecuta Failsafe en {@code ./mvnw verify}.</p>
 */
@PruebaIntegracion
@DisplayName("Registro de cuentas (integracion)")
class RegistroIT {

    private static final String RUTA = "/api/v1/auth/registro";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SoporteDatosPrueba datos;

    // ==================================================================
    @Nested
    @DisplayName("Con datos validos")
    class Valido {

        @Test
        @DisplayName("201, normaliza el correo, deriva el nombre sin el punto y crea el perfil vacio")
        void registra_y_crea_el_perfil() throws Exception {
            // Arrange: un correo con punto, el caso que antes la base rechazaba
            String sufijo = sufijo();
            String correo = "Ana.Dev." + sufijo + "@Ejemplo.COM";

            // Act
            String respuesta = mvc.perform(registrar(correo, null))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.correo").value(correo.toLowerCase()))
                    .andExpect(jsonPath("$.nombreUsuario").value("anadev" + sufijo))
                    .andReturn().getResponse().getContentAsString();
            String id = com.jayway.jsonpath.JsonPath.read(respuesta, "$.id");

            // Assert: el perfil existe, sin tecnologias y con el nombre de usuario
            mvc.perform(get("/api/v1/perfiles/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nombre").value("anadev" + sufijo))
                    .andExpect(jsonPath("$.tecnologias", hasSize(0)));
        }

        @Test
        @DisplayName("la cuenta recien creada puede iniciar sesion, sin distinguir mayusculas en el correo")
        void puede_iniciar_sesion() throws Exception {
            // Arrange
            String correo = "luis-" + sufijo() + "@ejemplo.com";
            mvc.perform(registrar(correo, "luis_" + sufijo())).andExpect(status().isCreated());

            // Act + Assert
            mvc.perform(post("/api/v1/auth/inicio-sesion")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"correo\": \"" + correo.toUpperCase()
                                    + "\", \"clave\": \"" + SoporteDatosPrueba.CLAVE + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty());
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Con datos rechazados")
    class Rechazado {

        @Test
        @DisplayName("409 AUTH_CORREO_YA_REGISTRADO aunque cambien las mayusculas")
        void correo_duplicado() throws Exception {
            // Arrange
            var existente = datos.crear("DESARROLLADOR", false);

            // Act + Assert
            mvc.perform(registrar(existente.correo().toUpperCase(), null))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("AUTH_CORREO_YA_REGISTRADO"));
        }

        @Test
        @DisplayName("409 AUTH_NOMBRE_USUARIO_YA_REGISTRADO")
        void nombre_duplicado() throws Exception {
            // Arrange
            var existente = datos.crear("DESARROLLADOR", false);

            // Act + Assert
            mvc.perform(registrar("otra-" + sufijo() + "@ejemplo.com", existente.nombreUsuario()))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("AUTH_NOMBRE_USUARIO_YA_REGISTRADO"));
        }

        @Test
        @DisplayName("400 VALIDACION_FALLIDA si el nombre no cumple el formato, en vez de un 409 de la base")
        void nombre_con_formato_invalido() throws Exception {
            // Act + Assert
            mvc.perform(registrar("eva-" + sufijo() + "@ejemplo.com", "-eva"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDACION_FALLIDA"))
                    .andExpect(jsonPath("$.details[?(@.campo == 'nombreUsuario')]").exists());
        }

        @Test
        @DisplayName("400 VALIDACION_FALLIDA con una clave de menos de 8 caracteres")
        void clave_corta() throws Exception {
            // Act + Assert
            mvc.perform(post(RUTA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"correo\": \"eva-" + sufijo() + "@ejemplo.com\", \"clave\": \"corta\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDACION_FALLIDA"))
                    .andExpect(jsonPath("$.details[?(@.campo == 'clave')]").exists());
        }
    }

    // ------------------------------------------------------------------

    private static RequestBuilder registrar(String correo, String nombreUsuario) {
        String nombre = nombreUsuario == null ? "" : ", \"nombreUsuario\": \"" + nombreUsuario + "\"";
        return post(RUTA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\": \"" + correo + "\", \"clave\": \"" + SoporteDatosPrueba.CLAVE + "\""
                        + nombre + "}");
    }

    private static String sufijo() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
