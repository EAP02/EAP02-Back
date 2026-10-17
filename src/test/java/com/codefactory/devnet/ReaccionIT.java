package com.codefactory.devnet;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-14: reaccionar (me gusta) a proyectos y discusiones, verificado extremo a extremo.
 *
 * <p>Lo que solo se ve con la base real: que el contador de la publicacion se mueve en
 * uno, que la clave primaria compuesta impide duplicar, y que el contador siempre
 * coincide con las filas de {@code reaccion_publicacion}.</p>
 *
 * <p><b>Requiere Docker.</b> Lo ejecuta Failsafe en {@code ./mvnw verify}.</p>
 */
@PruebaIntegracion
@DisplayName("HU-14 Reaccionar a proyectos y discusiones (integracion)")
class ReaccionIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SoporteDatosPrueba datos;

    @Autowired
    private JdbcTemplate jdbc;

    // ==================================================================
    @Nested
    @DisplayName("Criterio 1: al reaccionar, el contador sube en uno")
    class AlReaccionar {

        @Test
        @DisplayName("en una discusion: 200, contador 0 -> 1 y una fila a nombre del usuario del token")
        void reacciona_una_discusion() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var ana = datos.crear("DESARROLLADOR", false);
            UUID discusion = crearDiscusion(autenticar(autor.correo()));
            assertThat(contador(discusion)).isZero();

            // Act
            mvc.perform(reaccionar(discusion, autenticar(ana.correo()), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.tipo").value("ME_GUSTA"));

            // Assert: en la base
            assertThat(contador(discusion)).isEqualTo(1);
            assertThat(filas(discusion)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT tipo FROM reaccion_publicacion WHERE usuario_id = ? AND publicacion_id = ?",
                    String.class, ana.id(), discusion)).isEqualTo("ME_GUSTA");
        }

        @Test
        @DisplayName("en un proyecto: el mismo endpoint sirve, porque ambos son una publicacion")
        void reacciona_un_proyecto() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var ana = datos.crear("DESARROLLADOR", false);
            UUID proyecto = crearProyecto(autenticar(autor.correo()));

            // Act + Assert
            mvc.perform(reaccionar(proyecto, autenticar(ana.correo()), "UTIL"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.tipo").value("UTIL"));
        }

        @Test
        @DisplayName("dos usuarios distintos suman dos")
        void dos_usuarios_suman_dos() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var ana = datos.crear("DESARROLLADOR", false);
            var luis = datos.crear("DESARROLLADOR", false);
            UUID discusion = crearDiscusion(autenticar(autor.correo()));

            // Act
            mvc.perform(reaccionar(discusion, autenticar(ana.correo()), null)).andExpect(status().isOk());
            mvc.perform(reaccionar(discusion, autenticar(luis.correo()), null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(2));

            // Assert
            assertThat(contador(discusion)).isEqualTo(2);
            assertThat(filas(discusion)).isEqualTo(2);
        }

        @Test
        @DisplayName("la lectura publica (sin token) muestra el total y el desglose por tipo")
        void lectura_publica() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var ana = datos.crear("DESARROLLADOR", false);
            var luis = datos.crear("DESARROLLADOR", false);
            UUID discusion = crearDiscusion(autenticar(autor.correo()));
            mvc.perform(reaccionar(discusion, autenticar(ana.correo()), "UTIL")).andExpect(status().isOk());
            mvc.perform(reaccionar(discusion, autenticar(luis.correo()), "UTIL")).andExpect(status().isOk());

            // Act + Assert
            mvc.perform(get("/api/v1/publicaciones/" + discusion + "/reacciones"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(2))
                    .andExpect(jsonPath("$.porTipo.UTIL").value(2));
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Criterio 2: al quitar la reaccion, el contador baja en uno")
    class AlQuitar {

        @Test
        @DisplayName("quitar la propia reaccion: contador 1 -> 0 y la fila desaparece")
        void quita_la_reaccion() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var ana = datos.crear("DESARROLLADOR", false);
            String tokenAna = autenticar(ana.correo());
            UUID discusion = crearDiscusion(autenticar(autor.correo()));
            mvc.perform(reaccionar(discusion, tokenAna, null)).andExpect(status().isOk());
            assertThat(contador(discusion)).isEqualTo(1);

            // Act
            mvc.perform(delete(ruta(discusion)).header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(0));

            // Assert
            assertThat(contador(discusion)).isZero();
            assertThat(filas(discusion)).isZero();
        }

        @Test
        @DisplayName("quitar la reaccion de uno no toca la de otro")
        void no_toca_la_de_otro() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var ana = datos.crear("DESARROLLADOR", false);
            var luis = datos.crear("DESARROLLADOR", false);
            String tokenAna = autenticar(ana.correo());
            UUID discusion = crearDiscusion(autenticar(autor.correo()));
            mvc.perform(reaccionar(discusion, tokenAna, null)).andExpect(status().isOk());
            mvc.perform(reaccionar(discusion, autenticar(luis.correo()), null)).andExpect(status().isOk());

            // Act
            mvc.perform(delete(ruta(discusion)).header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isOk());

            // Assert: queda la de Luis
            assertThat(contador(discusion)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM reaccion_publicacion WHERE usuario_id = ? AND publicacion_id = ?",
                    Integer.class, luis.id(), discusion)).isEqualTo(1);
        }

        @Test
        @DisplayName("quitar sin haber reaccionado no es error y el contador no baja de cero")
        void quitar_sin_reaccion_es_idempotente() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var ana = datos.crear("DESARROLLADOR", false);
            UUID discusion = crearDiscusion(autenticar(autor.correo()));

            // Act + Assert
            mvc.perform(delete(ruta(discusion)).header("Authorization", "Bearer " + autenticar(ana.correo())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(0));
            assertThat(contador(discusion)).isZero();
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Criterio 3: solo una reaccion por usuario y publicacion (no se duplica)")
    class SinDuplicar {

        @Test
        @DisplayName("reaccionar dos veces lo mismo deja una sola fila y el contador en 1")
        void doble_clic_no_duplica() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var ana = datos.crear("DESARROLLADOR", false);
            String tokenAna = autenticar(ana.correo());
            UUID discusion = crearDiscusion(autenticar(autor.correo()));

            // Act
            mvc.perform(reaccionar(discusion, tokenAna, null)).andExpect(status().isOk());
            mvc.perform(reaccionar(discusion, tokenAna, null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(1));

            // Assert
            assertThat(filas(discusion)).isEqualTo(1);
            assertThat(contador(discusion)).isEqualTo(1);
        }

        @Test
        @DisplayName("cambiar de tipo reemplaza la reaccion: sigue siendo una fila y el contador no sube")
        void cambiar_de_tipo_reemplaza() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var ana = datos.crear("DESARROLLADOR", false);
            String tokenAna = autenticar(ana.correo());
            UUID discusion = crearDiscusion(autenticar(autor.correo()));
            mvc.perform(reaccionar(discusion, tokenAna, "ME_GUSTA")).andExpect(status().isOk());

            // Act
            mvc.perform(reaccionar(discusion, tokenAna, "INNOVADOR"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tipo").value("INNOVADOR"))
                    .andExpect(jsonPath("$.total").value(1));

            // Assert
            assertThat(filas(discusion)).isEqualTo(1);
            assertThat(contador(discusion)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "SELECT tipo FROM reaccion_publicacion WHERE usuario_id = ? AND publicacion_id = ?",
                    String.class, ana.id(), discusion)).isEqualTo("INNOVADOR");
        }

        @Test
        @DisplayName("reaccionar, quitar y volver a reaccionar deja el contador en 1")
        void reaccionar_quitar_reaccionar() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var ana = datos.crear("DESARROLLADOR", false);
            String tokenAna = autenticar(ana.correo());
            UUID discusion = crearDiscusion(autenticar(autor.correo()));

            // Act
            mvc.perform(reaccionar(discusion, tokenAna, null)).andExpect(status().isOk());
            mvc.perform(delete(ruta(discusion)).header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isOk());
            mvc.perform(reaccionar(discusion, tokenAna, null)).andExpect(status().isOk());

            // Assert: el contador siempre coincide con las filas
            assertThat(contador(discusion)).isEqualTo(1);
            assertThat(filas(discusion)).isEqualTo(1);
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Rechazos")
    class Rechazos {

        @Test
        @DisplayName("sin token: 401")
        void sin_token() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            UUID discusion = crearDiscusion(autenticar(autor.correo()));

            // Act + Assert
            mvc.perform(put(ruta(discusion))).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("un visitante no puede reaccionar: su rol no tiene reaccion:crear")
        void un_visitante_no_puede() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var visitante = datos.crear("VISITANTE", false);
            UUID discusion = crearDiscusion(autenticar(autor.correo()));

            // Act + Assert
            mvc.perform(reaccionar(discusion, autenticar(visitante.correo()), null))
                    .andExpect(status().isForbidden());
            assertThat(contador(discusion)).isZero();
        }

        @Test
        @DisplayName("un tipo desconocido responde 400 y no crea nada")
        void tipo_invalido() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var ana = datos.crear("DESARROLLADOR", false);
            UUID discusion = crearDiscusion(autenticar(autor.correo()));

            // Act + Assert
            mvc.perform(reaccionar(discusion, autenticar(ana.correo()), "ODIO"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("REACCION_TIPO_INVALIDO"));
            assertThat(filas(discusion)).isZero();
        }

        @Test
        @DisplayName("una publicacion que no existe responde 404")
        void publicacion_inexistente() throws Exception {
            // Arrange
            var ana = datos.crear("DESARROLLADOR", false);

            // Act + Assert
            mvc.perform(reaccionar(UUID.randomUUID(), autenticar(ana.correo()), null))
                    .andExpect(status().isNotFound());
        }
    }

    // ------------------------------------------------------------------
    // Ayudas
    // ------------------------------------------------------------------

    private static String ruta(UUID publicacion) {
        return "/api/v1/publicaciones/" + publicacion + "/reaccion";
    }

    private MockHttpServletRequestBuilder reaccionar(UUID publicacion, String token, String tipo) {
        MockHttpServletRequestBuilder peticion = put(ruta(publicacion))
                .header("Authorization", "Bearer " + token);
        if (tipo != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content("{\"tipo\": \"" + tipo + "\"}");
        }
        return peticion;
    }

    private int contador(UUID publicacion) {
        return jdbc.queryForObject(
                "SELECT contador_reacciones FROM publicacion WHERE id = ?", Integer.class, publicacion);
    }

    private int filas(UUID publicacion) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM reaccion_publicacion WHERE publicacion_id = ?", Integer.class, publicacion);
    }

    private UUID crearDiscusion(String token) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/discusiones")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo": "Spring Boot 4: vale la pena migrar ya?",
                                 "cuerpo": "Estamos evaluando migrar desde Boot 3. Que problemas encontraron con Jackson 3?",
                                 "tecnologias": %s}
                                """.formatted(datos.tecnologiasAprobadas(2))))
                .andExpect(status().isCreated())
                .andReturn();
        return idDe(r);
    }

    private UUID crearProyecto(String token) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/proyectos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo": "Motor de plantillas en Java",
                                 "descripcion": "Un motor de plantillas minimalista escrito en Java 17.",
                                 "estadoProyecto": "EN_DESARROLLO",
                                 "tecnologias": %s}
                                """.formatted(datos.tecnologiasAprobadas(2))))
                .andExpect(status().isCreated())
                .andReturn();
        return idDe(r);
    }

    private static UUID idDe(MvcResult r) throws Exception {
        return UUID.fromString(JsonPath.read(r.getResponse().getContentAsString(), "$.id"));
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
