package com.codefactory.devnet;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Errores de forma de la peticion, con el cuerpo uniforme de ADR-005.
 *
 * <p>Son los que puede provocar cualquier cliente sin tocar ninguna regla de negocio:
 * JSON roto, un identificador mal escrito, una ruta o un metodo que no existen. Lo
 * importante es que salgan con {@code errorCode} y {@code traceId} como el resto, y no
 * con la pagina de error por defecto de Spring.</p>
 *
 * <p><b>Requiere Docker.</b> Lo ejecuta Failsafe en {@code ./mvnw verify}.</p>
 */
@PruebaIntegracion
@DisplayName("Errores de forma de la peticion (integracion)")
class ErroresApiIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SoporteDatosPrueba datos;

    @Test
    @DisplayName("400 CUERPO_ILEGIBLE con un JSON mal formado")
    void cuerpo_ilegible() throws Exception {
        mvc.perform(post("/api/v1/auth/inicio-sesion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CUERPO_ILEGIBLE"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    @DisplayName("400 PARAMETRO_INVALIDO con un identificador que no es UUID")
    void parametro_invalido() throws Exception {
        mvc.perform(get("/api/v1/perfiles/no-es-un-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.details[0].campo").value("id"))
                .andExpect(jsonPath("$.details[0].valor").value("no-es-un-uuid"));
    }

    @Test
    @DisplayName("404 RECURSO_NO_ENCONTRADO con una ruta que no existe")
    void ruta_inexistente() throws Exception {
        mvc.perform(get("/api/v1/perfiles/" + UUID.randomUUID() + "/inexistente"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    @DisplayName("405 METODO_NO_PERMITIDO con un metodo que la ruta no admite")
    void metodo_no_permitido() throws Exception {
        // Arrange: autenticado, para que la cadena de seguridad deje pasar el DELETE
        var ana = datos.crear("DESARROLLADOR", false);

        // Act + Assert
        mvc.perform(delete("/api/v1/perfiles/" + ana.id())
                        .header("Authorization", "Bearer " + autenticar(ana.correo())))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errorCode").value("METODO_NO_PERMITIDO"))
                .andExpect(jsonPath("$.details[0].valor").value("DELETE"));
    }

    private String autenticar(String correo) throws Exception {
        MvcResult resultado = mvc.perform(post("/api/v1/auth/inicio-sesion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\": \"" + correo
                                + "\", \"clave\": \"" + SoporteDatosPrueba.CLAVE + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(resultado.getResponse().getContentAsString(), "$.token");
    }
}
