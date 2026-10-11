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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-11: crear una discusion, verificado extremo a extremo.
 *
 * <p>Complementa a {@code DiscusionTest}. Aqui se verifica lo que solo se ve con la base
 * real: que la discusion entra en las tres tablas como una publicacion de tipo
 * DISCUSION, que se lee con su autor sin token, y que el hilo agrupa a quienes
 * comentaron.</p>
 *
 * <p>Crear comentarios es otra HU, asi que el hilo se puebla insertando filas en
 * {@code comentario} directamente, con {@link #comentar}.</p>
 *
 * <p><b>Requiere Docker.</b> Lo ejecuta Failsafe en {@code ./mvnw verify}.</p>
 */
@PruebaIntegracion
@DisplayName("HU-11 Crear una discusion (integracion)")
class CrearDiscusionIT {

    private static final String RUTA = "/api/v1/discusiones";
    private static final String TITULO = "Spring Boot 4: vale la pena migrar ya?";
    private static final String CUERPO =
            "Estamos evaluando migrar desde Boot 3. Que problemas encontraron con Jackson 3?";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SoporteDatosPrueba datos;

    @Autowired
    private JdbcTemplate jdbc;

    // ==================================================================
    @Nested
    @DisplayName("Criterio 1: con datos validos queda publicada y visible con su autor")
    class PublicadaYVisible {

        @Test
        @DisplayName("responde 201 con la cabecera Location")
        void crea_correctamente() throws Exception {
            // Arrange
            var dev = datos.crear("DESARROLLADOR", false);

            // Act + Assert
            mvc.perform(post(RUTA)
                            .header("Authorization", "Bearer " + autenticar(dev.correo()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(TITULO, CUERPO, etiquetas(2))))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.titulo").value(TITULO))
                    .andExpect(jsonPath("$.publicadoEn").isNotEmpty());
        }

        @Test
        @DisplayName("se lee sin token, con su autor y sus etiquetas")
        void visible_con_su_autor() throws Exception {
            // Arrange
            var dev = datos.crear("DESARROLLADOR", false);
            UUID discusion = crearDiscusion(autenticar(dev.correo()));

            // Act + Assert: lectura publica
            mvc.perform(get(RUTA + "/" + discusion))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.titulo").value(TITULO))
                    .andExpect(jsonPath("$.cuerpo").value(CUERPO))
                    .andExpect(jsonPath("$.categoria").value("DEBATE"))
                    .andExpect(jsonPath("$.resuelta").value(false))
                    .andExpect(jsonPath("$.autor.id").value(dev.id().toString()))
                    .andExpect(jsonPath("$.autor.nombreUsuario").value(dev.nombreUsuario()))
                    .andExpect(jsonPath("$.tecnologias", hasSize(2)))
                    .andExpect(jsonPath("$.tecnologias[0].nombre").isNotEmpty())
                    .andExpect(jsonPath("$.tecnologias[0].slug").isNotEmpty());
        }

        @Test
        @DisplayName("se guarda como publicacion de tipo DISCUSION, publicada, con su subtipo")
        void persiste_en_las_tres_tablas() throws Exception {
            // Arrange
            var dev = datos.crear("DESARROLLADOR", false);

            // Act
            UUID discusion = crearDiscusion(autenticar(dev.correo()));

            // Assert
            Map<String, Object> fila = jdbc.queryForMap(
                    "SELECT tipo, estado, autor_id FROM publicacion WHERE id = ?", discusion);
            assertThat(fila.get("tipo")).isEqualTo("DISCUSION");
            assertThat(fila.get("estado")).isEqualTo("PUBLICADO");
            assertThat(jdbc.queryForObject(
                    "SELECT categoria FROM publicacion_discusion WHERE publicacion_id = ?",
                    String.class, discusion)).isEqualTo("DEBATE");
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM publicacion_tecnologia WHERE publicacion_id = ?",
                    Integer.class, discusion)).isEqualTo(2);
        }

        @Test
        @DisplayName("respeta la categoria indicada")
        void respeta_la_categoria() throws Exception {
            // Arrange
            var dev = datos.crear("DESARROLLADOR", false);

            // Act
            MvcResult r = mvc.perform(post(RUTA)
                            .header("Authorization", "Bearer " + autenticar(dev.correo()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"titulo": "%s", "cuerpo": "%s", "categoria": "PREGUNTA", "tecnologias": %s}
                                    """.formatted(TITULO, CUERPO, etiquetas(1))))
                    .andExpect(status().isCreated())
                    .andReturn();

            // Assert
            mvc.perform(get(RUTA + "/" + idDe(r)))
                    .andExpect(jsonPath("$.categoria").value("PREGUNTA"));
        }

        @Test
        @DisplayName("no se mezcla con los proyectos en la busqueda de proyectos")
        void no_aparece_como_proyecto() throws Exception {
            // Arrange: comparten tabla, pero la busqueda de HU-09 es solo de proyectos
            var dev = datos.crear("DESARROLLADOR", false);
            String palabra = "zqdiscusion" + UUID.randomUUID().toString().replaceAll("[^a-f]", "");
            mvc.perform(post(RUTA)
                            .header("Authorization", "Bearer " + autenticar(dev.correo()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(TITULO + " " + palabra, CUERPO, etiquetas(1))))
                    .andExpect(status().isCreated());

            // Act + Assert
            mvc.perform(get("/api/v1/proyectos").param("q", palabra))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido", hasSize(0)));
        }

        @Test
        @DisplayName("una discusion inexistente responde 404")
        void inexistente() throws Exception {
            mvc.perform(get(RUTA + "/" + UUID.randomUUID()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("DISCUSION_NO_ENCONTRADA"));
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Criterio 2: si falta un campo obligatorio, se rechaza")
    class CamposObligatorios {

        @Test
        @DisplayName("sin cuerpo responde 400 senalando el campo")
        void rechaza_sin_cuerpo() throws Exception {
            // Arrange
            var dev = datos.crear("DESARROLLADOR", false);

            // Act + Assert: el ejemplo literal de la HU
            mvc.perform(post(RUTA)
                            .header("Authorization", "Bearer " + autenticar(dev.correo()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"titulo": "%s", "tecnologias": %s}
                                    """.formatted(TITULO, etiquetas(1))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDACION_FALLIDA"))
                    .andExpect(jsonPath("$.details[?(@.campo == 'cuerpo')]").exists());

            assertThat(contarDiscusionesDe(dev.id())).isZero();
        }

        @Test
        @DisplayName("sin etiquetas responde 400")
        void rechaza_sin_etiquetas() throws Exception {
            // Arrange
            var dev = datos.crear("DESARROLLADOR", false);

            // Act + Assert
            mvc.perform(post(RUTA)
                            .header("Authorization", "Bearer " + autenticar(dev.correo()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(TITULO, CUERPO, List.of())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details[?(@.campo == 'tecnologias')]").exists());
        }

        @Test
        @DisplayName("con etiquetas que no existen responde 422 y no crea nada")
        void rechaza_etiquetas_desconocidas() throws Exception {
            // Arrange
            var dev = datos.crear("DESARROLLADOR", false);

            // Act + Assert
            mvc.perform(post(RUTA)
                            .header("Authorization", "Bearer " + autenticar(dev.correo()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(TITULO, CUERPO, List.of((short) 32000))))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errorCode").value("DISCUSION_TECNOLOGIA_DESCONOCIDA"));

            assertThat(contarDiscusionesDe(dev.id())).isZero();
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Criterio 3: queda asociada al miembro que la creo")
    class Autoria {

        @Test
        @DisplayName("el autor es el del token, aunque el cuerpo diga otra cosa")
        void el_autor_sale_del_token() throws Exception {
            // Arrange
            var dev = datos.crear("DESARROLLADOR", false);
            var otro = datos.crear("DESARROLLADOR", false);

            // Act: un campo autorId en el cuerpo no existe en el contrato y se ignora
            MvcResult r = mvc.perform(post(RUTA)
                            .header("Authorization", "Bearer " + autenticar(dev.correo()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"autorId": "%s", "titulo": "%s", "cuerpo": "%s", "tecnologias": %s}
                                    """.formatted(otro.id(), TITULO, CUERPO, etiquetas(1))))
                    .andExpect(status().isCreated())
                    .andReturn();

            // Assert
            assertThat(jdbc.queryForObject("SELECT autor_id FROM publicacion WHERE id = ?",
                    UUID.class, idDe(r))).isEqualTo(dev.id());
        }

        @Test
        @DisplayName("sin token no se puede crear")
        void exige_autenticacion() throws Exception {
            mvc.perform(post(RUTA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(TITULO, CUERPO, etiquetas(1))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("un visitante no puede crear: su rol no tiene publicacion:crear")
        void un_visitante_no_puede() throws Exception {
            // Arrange
            var visitante = datos.crear("VISITANTE", false);

            // Act + Assert
            mvc.perform(post(RUTA)
                            .header("Authorization", "Bearer " + autenticar(visitante.correo()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(TITULO, CUERPO, etiquetas(1))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("ACCESO_DENEGADO"));
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Criterio 4: existe un hilo con todos los usuarios que han comentado")
    class Hilo {

        @Test
        @DisplayName("una discusion recien creada tiene el hilo vacio")
        void hilo_vacio_al_crear() throws Exception {
            // Arrange
            var dev = datos.crear("DESARROLLADOR", false);
            UUID discusion = crearDiscusion(autenticar(dev.correo()));

            // Act + Assert
            mvc.perform(get(RUTA + "/" + discusion))
                    .andExpect(jsonPath("$.hilo.totalComentarios").value(0))
                    .andExpect(jsonPath("$.hilo.participantes", hasSize(0)));
        }

        @Test
        @DisplayName("lista a cada usuario que comento una sola vez, por orden de llegada")
        void agrupa_a_los_participantes() throws Exception {
            // Arrange: ana comenta dos veces, beto una
            var autor = datos.crear("DESARROLLADOR", false);
            var ana = datos.crear("DESARROLLADOR", false);
            var beto = datos.crear("DESARROLLADOR", false);
            UUID discusion = crearDiscusion(autenticar(autor.correo()));
            comentar(discusion, ana.id(), "2026-10-01T10:00:00Z", "PUBLICADO");
            comentar(discusion, beto.id(), "2026-10-01T11:00:00Z", "PUBLICADO");
            comentar(discusion, ana.id(), "2026-10-01T12:00:00Z", "PUBLICADO");

            // Act + Assert
            mvc.perform(get(RUTA + "/" + discusion))
                    .andExpect(jsonPath("$.hilo.totalComentarios").value(3))
                    .andExpect(jsonPath("$.hilo.participantes", hasSize(2)))
                    .andExpect(jsonPath("$.hilo.participantes[0].usuarioId").value(ana.id().toString()))
                    .andExpect(jsonPath("$.hilo.participantes[0].nombreUsuario").value(ana.nombreUsuario()))
                    .andExpect(jsonPath("$.hilo.participantes[0].comentarios").value(2))
                    .andExpect(jsonPath("$.hilo.participantes[1].usuarioId").value(beto.id().toString()))
                    .andExpect(jsonPath("$.hilo.participantes[1].comentarios").value(1));
        }

        @Test
        @DisplayName("quien solo tiene comentarios eliminados ya no aparece en el hilo")
        void ignora_comentarios_eliminados() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var ana = datos.crear("DESARROLLADOR", false);
            UUID discusion = crearDiscusion(autenticar(autor.correo()));
            comentar(discusion, ana.id(), "2026-10-01T10:00:00Z", "ELIMINADO");

            // Act + Assert
            mvc.perform(get(RUTA + "/" + discusion))
                    .andExpect(jsonPath("$.hilo.participantes", hasSize(0)));
        }
    }

    // ==================================================================

    /**
     * Inserta un comentario raiz directamente en la base.
     *
     * <p>Crear comentarios por la API es otra HU; hasta entonces, esta es la unica forma
     * de poblar el hilo para probarlo.</p>
     */
    private void comentar(UUID discusion, UUID autor, String instante, String estado) {
        jdbc.update("""
                INSERT INTO comentario (publicacion_id, autor_id, contenido, estado, creado_en)
                VALUES (?, ?, 'Comentario de prueba', ?, ?::timestamptz)
                """, discusion, autor, estado, instante);
    }

    private UUID crearDiscusion(String token) throws Exception {
        MvcResult r = mvc.perform(post(RUTA)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(TITULO, CUERPO, etiquetas(2))))
                .andExpect(status().isCreated())
                .andReturn();
        return idDe(r);
    }

    private int contarDiscusionesDe(UUID autor) {
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM publicacion WHERE autor_id = ? AND tipo = 'DISCUSION'",
                Integer.class, autor);
        return total == null ? 0 : total;
    }

    private List<Short> etiquetas(int cuantas) {
        return datos.tecnologiasAprobadas(cuantas);
    }

    private static UUID idDe(MvcResult r) throws Exception {
        return UUID.fromString(JsonPath.read(r.getResponse().getContentAsString(), "$.id"));
    }

    private static String cuerpo(String titulo, String cuerpo, List<Short> etiquetas) {
        return """
                {"titulo": "%s", "cuerpo": "%s", "tecnologias": %s}
                """.formatted(titulo, cuerpo, etiquetas);
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
