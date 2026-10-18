package com.codefactory.devnet;

import com.jayway.jsonpath.JsonPath;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@PruebaIntegracion
class MensajeriaIT {
    @Autowired private MockMvc mvc;
    @Autowired private SoporteDatosPrueba datos;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void envio_bandeja_historial_y_deduplicacion() throws Exception {
        var ana = datos.crear("DESARROLLADOR", false);
        var luis = datos.crear("DESARROLLADOR", false);
        var eva = datos.crear("DESARROLLADOR", false);
        String tokenAna = autenticar(ana.correo());
        String tokenLuis = autenticar(luis.correo());
        String tokenEva = autenticar(eva.correo());

        mvc.perform(get("/api/v1/conversaciones").header("Authorization", "Bearer " + tokenAna))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());

        String primero = enviar(luis.id(), "Hola", tokenAna);
        UUID conversacion = UUID.fromString(valor(primero, "$.conversacionId"));
        assertThat(valor(primero, "$.remitenteId")).isEqualTo(ana.id().toString());
        assertThat(valor(primero, "$.contenido")).isEqualTo("Hola");

        String segundo = enviar(ana.id(), "Respuesta", tokenLuis);
        assertThat(valor(segundo, "$.conversacionId")).isEqualTo(conversacion.toString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM conversacion WHERE id = ?", Integer.class, conversacion))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM participante_conversacion WHERE conversacion_id = ?",
                Integer.class, conversacion)).isEqualTo(2);

        mvc.perform(get("/api/v1/conversaciones/" + conversacion + "/mensajes")
                        .header("Authorization", "Bearer " + tokenLuis))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].contenido").value("Hola"))
                .andExpect(jsonPath("$[1].contenido").value("Respuesta"));

        String conEva = enviar(eva.id(), "Reciente", tokenAna);
        mvc.perform(get("/api/v1/conversaciones").header("Authorization", "Bearer " + tokenAna))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].conversacionId").value(valor(conEva, "$.conversacionId")))
                .andExpect(jsonPath("$[1].conversacionId").value(conversacion.toString()));
        mvc.perform(get("/api/v1/conversaciones").header("Authorization", "Bearer " + tokenLuis))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].conversacionId").value(conversacion.toString()))
                .andExpect(jsonPath("$[1]").doesNotExist());
        mvc.perform(get("/api/v1/conversaciones").header("Authorization", "Bearer " + tokenEva))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].conversacionId").value(valor(conEva, "$.conversacionId")))
                .andExpect(jsonPath("$[1]").doesNotExist());
    }

    @Test
    void rechaza_vacios_anonimos_suplantacion_y_acceso_ajeno() throws Exception {
        var ana = datos.crear("DESARROLLADOR", false);
        var luis = datos.crear("DESARROLLADOR", false);
        var tercero = datos.crear("DESARROLLADOR", false);
        String tokenAna = autenticar(ana.correo());
        String tokenTercero = autenticar(tercero.correo());

        mvc.perform(post("/api/v1/mensajes").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(luis.id(), "   ")).header("Authorization", "Bearer " + tokenAna))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MENSAJE_CONTENIDO_INVALIDO"));
        mvc.perform(post("/api/v1/mensajes").contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(luis.id(), ""))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/conversaciones")).andExpect(status().isUnauthorized());

        String peticionSuplantada = "{\"destinatarioId\":\"" + luis.id()
                + "\",\"contenido\":\"Privado\",\"remitenteId\":\"" + tercero.id() + "\"}";
        MvcResult resultadoSuplantacion = mvc.perform(post("/api/v1/mensajes")
                        .contentType(MediaType.APPLICATION_JSON).content(peticionSuplantada)
                        .header("Authorization", "Bearer " + tokenAna))
                .andReturn();
        assertThat(resultadoSuplantacion.getResponse().getStatus()).isIn(201, 400);
        String mensaje = resultadoSuplantacion.getResponse().getStatus() == 201
                ? resultadoSuplantacion.getResponse().getContentAsString()
                : enviar(luis.id(), "Privado", tokenAna);
        assertThat(valor(mensaje, "$.remitenteId")).isEqualTo(ana.id().toString());
        UUID conversacion = UUID.fromString(valor(mensaje, "$.conversacionId"));
        assertThat(jdbc.queryForObject("SELECT remitente_id FROM mensaje WHERE id = ?", UUID.class,
                UUID.fromString(valor(mensaje, "$.id")))).isEqualTo(ana.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mensaje WHERE conversacion_id = ?",
                Integer.class, conversacion)).isEqualTo(1);

        mvc.perform(get("/api/v1/conversaciones/" + conversacion + "/mensajes")
                        .header("Authorization", "Bearer " + tokenTercero))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RECURSO_NO_ENCONTRADO"));
        mvc.perform(get("/api/v1/conversaciones").header("Authorization", "Bearer " + tokenTercero))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        assertThat(datos.contarEventosAuditoria("ACCESO_DENEGADO", tercero.id())).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void mensaje_sobrevive_a_una_nueva_transaccion_y_se_audita_sin_contenido() throws Exception {
        var ana = datos.crear("DESARROLLADOR", false);
        var luis = datos.crear("DESARROLLADOR", false);
        String token = autenticar(ana.correo());
        String mensaje = enviar(luis.id(), "Texto persistente", token);
        UUID id = UUID.fromString(valor(mensaje, "$.id"));
        UUID conversacion = UUID.fromString(valor(mensaje, "$.conversacionId"));

        assertThat(jdbc.queryForObject("SELECT contenido FROM mensaje WHERE id = ?", String.class, id))
                .isEqualTo("Texto persistente");
        mvc.perform(get("/api/v1/conversaciones/" + conversacion + "/mensajes")
                        .header("Authorization", "Bearer " + autenticar(luis.correo())))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].contenido").value("Texto persistente"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE operacion = 'MENSAJE_ENVIADO' "
                + "AND registro_id = ? AND datos_despues IS NULL AND datos_antes IS NULL", Integer.class,
                id.toString())).isEqualTo(1);
    }

    private String enviar(UUID destinatarioId, String contenido, String token) throws Exception {
        MvcResult resultado = mvc.perform(post("/api/v1/mensajes")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo(destinatarioId, contenido))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andReturn();
        return resultado.getResponse().getContentAsString();
    }

    private String cuerpo(UUID destinatarioId, String contenido) throws Exception {
        return "{\"destinatarioId\":\"" + destinatarioId + "\",\"contenido\":\"" + contenido + "\"}";
    }

    private String autenticar(String correo) throws Exception {
        MvcResult resultado = mvc.perform(post("/api/v1/auth/inicio-sesion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo + "\",\"clave\":\""
                                + SoporteDatosPrueba.CLAVE + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return valor(resultado.getResponse().getContentAsString(), "$.token");
    }

    private static String valor(String respuesta, String ruta) {
        return JsonPath.read(respuesta, ruta);
    }
}
