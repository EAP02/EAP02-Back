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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-13: comentar en proyectos y discusiones, verificado extremo a extremo.
 *
 * <p>Complementa a {@code ComentarioTest}. Aqui se verifica lo que solo se ve con la base
 * real: que el comentario queda en la tabla {@code comentario} asociado a su autor y a la
 * publicacion, que eliminar es un cambio de estado, y que el permiso de administrador
 * llega en el token.</p>
 *
 * <p><b>Requiere Docker.</b> Lo ejecuta Failsafe en {@code ./mvnw verify}.</p>
 */
@PruebaIntegracion
@DisplayName("HU-13 Comentar en proyectos y discusiones (integracion)")
class ComentarioIT {

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
    @DisplayName("Criterio 1: un comentario valido se publica y queda asociado a su autor y a la entidad")
    class ComentarioValido {

        @Test
        @DisplayName("en una discusion: responde 201 y queda asociado al autor y a la discusion")
        void comenta_una_discusion() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var ana = datos.crear("DESARROLLADOR", false);
            UUID discusion = crearDiscusion(autenticar(autor.correo()));

            // Act
            MvcResult r = mvc.perform(comentarEn(discusion, autenticar(ana.correo()), "Buen aporte"))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andExpect(jsonPath("$.autorId").value(ana.id().toString()))
                    .andExpect(jsonPath("$.publicacionId").value(discusion.toString()))
                    .andExpect(jsonPath("$.contenido").value("Buen aporte"))
                    .andReturn();

            // Assert: en la base
            Map<String, Object> fila = jdbc.queryForMap(
                    "SELECT autor_id, publicacion_id, estado FROM comentario WHERE id = ?", idDe(r));
            assertThat(fila.get("autor_id")).isEqualTo(ana.id());
            assertThat(fila.get("publicacion_id")).isEqualTo(discusion);
            assertThat(fila.get("estado")).isEqualTo("PUBLICADO");
        }

        @Test
        @DisplayName("en un proyecto: el mismo endpoint sirve, porque ambos son una publicacion")
        void comenta_un_proyecto() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var ana = datos.crear("DESARROLLADOR", false);
            UUID proyecto = crearProyecto(autenticar(autor.correo()));

            // Act + Assert
            mvc.perform(comentarEn(proyecto, autenticar(ana.correo()), "Lo probe y funciona"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.publicacionId").value(proyecto.toString()));
        }

        @Test
        @DisplayName("el comentario aparece en la lectura publica, con el nombre de su autor")
        void aparece_en_el_listado() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var ana = datos.crear("DESARROLLADOR", false);
            UUID discusion = crearDiscusion(autenticar(autor.correo()));
            mvc.perform(comentarEn(discusion, autenticar(ana.correo()), "Hola a todos"))
                    .andExpect(status().isCreated());

            // Act + Assert: sin token
            mvc.perform(get(rutaComentarios(discusion)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].autorNombreUsuario").value(ana.nombreUsuario()))
                    .andExpect(jsonPath("$[0].contenido").value("Hola a todos"));
        }

        @Test
        @DisplayName("el autor es el del token, aunque el cuerpo diga otra cosa")
        void el_autor_sale_del_token() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var ana = datos.crear("DESARROLLADOR", false);
            var otro = datos.crear("DESARROLLADOR", false);
            UUID discusion = crearDiscusion(autenticar(autor.correo()));

            // Act: el contrato no tiene autorId y se ignora
            MvcResult r = mvc.perform(post(rutaComentarios(discusion))
                            .header("Authorization", "Bearer " + autenticar(ana.correo()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"autorId\": \"" + otro.id() + "\", \"contenido\": \"Mio\"}"))
                    .andExpect(status().isCreated())
                    .andReturn();

            // Assert
            assertThat(jdbc.queryForObject("SELECT autor_id FROM comentario WHERE id = ?",
                    UUID.class, idDe(r))).isEqualTo(ana.id());
        }

        @Test
        @DisplayName("una publicacion inexistente responde 404 y no guarda nada")
        void publicacion_inexistente() throws Exception {
            // Arrange
            var ana = datos.crear("DESARROLLADOR", false);

            // Act + Assert
            mvc.perform(comentarEn(UUID.randomUUID(), autenticar(ana.correo()), "Hola"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("RECURSO_NO_ENCONTRADO"));
        }

        @Test
        @DisplayName("sin token no se puede comentar")
        void exige_autenticacion() throws Exception {
            mvc.perform(post(rutaComentarios(UUID.randomUUID()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"contenido\": \"Hola\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("un visitante no puede comentar: su rol no tiene comentario:crear")
        void un_visitante_no_puede() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var visitante = datos.crear("VISITANTE", false);
            UUID discusion = crearDiscusion(autenticar(autor.correo()));

            // Act + Assert
            mvc.perform(comentarEn(discusion, autenticar(visitante.correo()), "Hola"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("ACCESO_DENEGADO"));
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Criterio 2: un comentario vacio se rechaza")
    class ComentarioVacio {

        @Test
        @DisplayName("vacio responde 400 senalando el campo y no guarda nada")
        void rechaza_vacio() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var ana = datos.crear("DESARROLLADOR", false);
            UUID discusion = crearDiscusion(autenticar(autor.correo()));

            // Act + Assert
            mvc.perform(comentarEn(discusion, autenticar(ana.correo()), ""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDACION_FALLIDA"))
                    .andExpect(jsonPath("$.details[?(@.campo == 'contenido')]").exists());

            assertThat(contarComentariosDe(ana.id())).isZero();
        }

        @Test
        @DisplayName("solo con espacios tambien se rechaza")
        void rechaza_solo_espacios() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var ana = datos.crear("DESARROLLADOR", false);
            UUID discusion = crearDiscusion(autenticar(autor.correo()));

            // Act + Assert
            mvc.perform(comentarEn(discusion, autenticar(ana.correo()), "     "))
                    .andExpect(status().isBadRequest());

            assertThat(contarComentariosDe(ana.id())).isZero();
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Criterio 3: el autor puede eliminar su propio comentario y este deja de mostrarse")
    class AutorElimina {

        @Test
        @DisplayName("responde 204, queda ELIMINADO en la base y desaparece del listado")
        void el_autor_elimina_lo_suyo() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var ana = datos.crear("DESARROLLADOR", false);
            String tokenAna = autenticar(ana.correo());
            UUID discusion = crearDiscusion(autenticar(autor.correo()));
            UUID comentario = idDe(mvc.perform(comentarEn(discusion, tokenAna, "Para borrar"))
                    .andExpect(status().isCreated()).andReturn());

            // Act
            mvc.perform(delete("/api/v1/comentarios/" + comentario)
                            .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isNoContent());

            // Assert: la fila se conserva, solo cambia de estado, y ya no se muestra
            assertThat(estadoDe(comentario)).isEqualTo("ELIMINADO");
            mvc.perform(get(rutaComentarios(discusion)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("eliminar dos veces: la segunda responde 404")
        void eliminar_dos_veces() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var ana = datos.crear("DESARROLLADOR", false);
            String tokenAna = autenticar(ana.correo());
            UUID discusion = crearDiscusion(autenticar(autor.correo()));
            UUID comentario = idDe(mvc.perform(comentarEn(discusion, tokenAna, "Una vez"))
                    .andExpect(status().isCreated()).andReturn());
            mvc.perform(delete("/api/v1/comentarios/" + comentario)
                            .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isNoContent());

            // Act + Assert
            mvc.perform(delete("/api/v1/comentarios/" + comentario)
                            .header("Authorization", "Bearer " + tokenAna))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("COMENTARIO_NO_ENCONTRADO"));
        }

        @Test
        @DisplayName("quien no es el autor recibe 403, el comentario sigue y el intento se audita")
        void un_tercero_no_puede() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var ana = datos.crear("DESARROLLADOR", false);
            var intruso = datos.crear("DESARROLLADOR", false);
            UUID discusion = crearDiscusion(autenticar(autor.correo()));
            UUID comentario = idDe(mvc.perform(comentarEn(discusion, autenticar(ana.correo()), "Mio"))
                    .andExpect(status().isCreated()).andReturn());
            String tokenIntruso = autenticar(intruso.correo());
            int antes = datos.contarEventosAuditoria("ACCESO_DENEGADO", intruso.id());

            // Act
            mvc.perform(delete("/api/v1/comentarios/" + comentario)
                            .header("Authorization", "Bearer " + tokenIntruso))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("COMENTARIO_AJENO"));

            // Assert
            assertThat(estadoDe(comentario)).isEqualTo("PUBLICADO");
            assertThat(datos.contarEventosAuditoria("ACCESO_DENEGADO", intruso.id()))
                    .as("evento ACCESO_DENEGADO registrado para el actor")
                    .isGreaterThanOrEqualTo(antes + 1);
        }

        @Test
        @DisplayName("un comentario inexistente responde 404")
        void inexistente() throws Exception {
            // Arrange
            var ana = datos.crear("DESARROLLADOR", false);

            // Act + Assert
            mvc.perform(delete("/api/v1/comentarios/" + UUID.randomUUID())
                            .header("Authorization", "Bearer " + autenticar(ana.correo())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("COMENTARIO_NO_ENCONTRADO"));
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Criterio 4: los comentarios pueden ser eliminados por administradores")
    class AdministradorElimina {

        @Test
        @DisplayName("un administrador elimina un comentario ajeno y la intervencion queda auditada")
        void admin_elimina_ajeno() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var ana = datos.crear("DESARROLLADOR", false);
            var admin = datos.crear("ADMIN", true);
            UUID discusion = crearDiscusion(autenticar(autor.correo()));
            UUID comentario = idDe(mvc.perform(comentarEn(discusion, autenticar(ana.correo()), "Ofensivo"))
                    .andExpect(status().isCreated()).andReturn());
            int antes = datos.contarEventosAuditoria("DELETE", admin.id());

            // Act
            mvc.perform(delete("/api/v1/comentarios/" + comentario)
                            .header("Authorization", "Bearer " + autenticar(admin.correo())))
                    .andExpect(status().isNoContent());

            // Assert
            assertThat(estadoDe(comentario)).isEqualTo("ELIMINADO");
            assertThat(datos.contarEventosAuditoria("DELETE", admin.id()))
                    .as("evento DELETE registrado para el administrador")
                    .isEqualTo(antes + 1);
        }

        @Test
        @DisplayName("un moderador NO puede eliminar comentarios ajenos: el permiso es solo del ADMIN")
        void moderador_no_puede() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var ana = datos.crear("DESARROLLADOR", false);
            var moderador = datos.crear("MODERADOR", true);
            UUID discusion = crearDiscusion(autenticar(autor.correo()));
            UUID comentario = idDe(mvc.perform(comentarEn(discusion, autenticar(ana.correo()), "Mio"))
                    .andExpect(status().isCreated()).andReturn());

            // Act + Assert
            mvc.perform(delete("/api/v1/comentarios/" + comentario)
                            .header("Authorization", "Bearer " + autenticar(moderador.correo())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("COMENTARIO_AJENO"));

            assertThat(estadoDe(comentario)).isEqualTo("PUBLICADO");
        }
    }

    // ==================================================================

    private static String rutaComentarios(UUID publicacion) {
        return "/api/v1/publicaciones/" + publicacion + "/comentarios";
    }

    private org.springframework.test.web.servlet.RequestBuilder comentarEn(UUID publicacion,
                                                                          String token,
                                                                          String contenido) {
        return post(rutaComentarios(publicacion))
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"contenido\": \"" + contenido + "\"}");
    }

    private UUID crearDiscusion(String token) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/discusiones")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo": "%s", "cuerpo": "%s", "tecnologias": %s}
                                """.formatted(TITULO, CUERPO, datos.tecnologiasAprobadas(2))))
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

    private String estadoDe(UUID comentario) {
        return jdbc.queryForObject("SELECT estado FROM comentario WHERE id = ?", String.class, comentario);
    }

    private int contarComentariosDe(UUID autor) {
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM comentario WHERE autor_id = ?", Integer.class, autor);
        return total == null ? 0 : total;
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
