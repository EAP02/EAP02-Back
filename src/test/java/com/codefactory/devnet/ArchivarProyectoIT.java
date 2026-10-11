package com.codefactory.devnet;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-08 (AB#18): eliminar un proyecto, verificado extremo a extremo.
 *
 * <p>Eliminar es archivar (regla R3). Se comprueba lo que solo se ve con la base real:
 * que el estado cambia, que la transicion queda en el historial con su autor, que el
 * proyecto desaparece de la lectura publica y que los intentos denegados se auditan.</p>
 *
 * <p>Usa el escenario literal de las tareas: un proyecto llamado "Sistema de
 * Gestion", con tilde incluida.</p>
 *
 * <p><b>Requiere Docker.</b> Lo ejecuta Failsafe en {@code ./mvnw verify}.</p>
 */
@PruebaIntegracion
@DisplayName("HU-08 Eliminar un proyecto (integracion)")
class ArchivarProyectoIT {

    private static final String RUTA = "/api/v1/proyectos";
    private static final String TITULO = "Sistema de Gestión";
    private static final String DESCRIPCION =
            "Sistema de gestion de inventarios para pequenos comercios, escrito en Spring Boot.";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SoporteDatosPrueba datos;

    @Autowired
    private JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager entityManager;

    // ==================================================================
    @Nested
    @DisplayName("HU-08.3: el autor elimina su proyecto con confirmacion previa")
    class EliminacionConConfirmacion {

        @Test
        @DisplayName("sin confirmacion se rechaza con 400 y el proyecto sigue publicado")
        void exige_confirmacion() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            String token = autenticar(autor.correo());
            UUID proyecto = publicar(token);

            // Act + Assert: ni siquiera envia cuerpo, como un clic accidental
            mvc.perform(post(RUTA + "/" + proyecto + "/archivado")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("PROYECTO_CONFIRMACION_REQUERIDA"));

            assertThat(datos.estadoDePublicacion(proyecto)).isEqualTo("PUBLICADO");
        }

        @Test
        @DisplayName("una confirmacion que no coincide se rechaza con 422")
        void confirmacion_incorrecta() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            String token = autenticar(autor.correo());
            UUID proyecto = publicar(token);

            // Act + Assert
            eliminar(proyecto, token, "Sistema de Gestion")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errorCode").value("PROYECTO_CONFIRMACION_INVALIDA"));

            assertThat(datos.estadoDePublicacion(proyecto)).isEqualTo("PUBLICADO");
        }

        @Test
        @DisplayName("con el titulo exacto responde 204 y el proyecto queda archivado")
        void elimina_con_confirmacion() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            String token = autenticar(autor.correo());
            UUID proyecto = publicar(token);

            // Act
            eliminar(proyecto, token, TITULO).andExpect(status().isNoContent());

            // Assert: archivado, no borrado. La fila sigue ahi.
            assertThat(datos.estadoDePublicacion(proyecto)).isEqualTo("ARCHIVADO");
        }

        @Test
        @DisplayName("la eliminacion queda en el historial con su autor (regla R5)")
        void queda_en_el_historial() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            String token = autenticar(autor.correo());
            UUID proyecto = publicar(token);

            // Act
            eliminar(proyecto, token, TITULO).andExpect(status().isNoContent());

            // Assert
            Map<String, Object> fila = jdbc.queryForMap("""
                    SELECT estado_anterior, estado_nuevo, actor_id, motivo
                      FROM historial_estado_publicacion WHERE publicacion_id = ?
                    """, proyecto);
            assertThat(fila.get("estado_anterior")).isEqualTo("PUBLICADO");
            assertThat(fila.get("estado_nuevo")).isEqualTo("ARCHIVADO");
            assertThat(fila.get("actor_id")).isEqualTo(autor.id());
            assertThat(fila.get("motivo")).isEqualTo("Eliminado por su autor.");
        }

        @Test
        @DisplayName("el proyecto eliminado desaparece de la lista publica del autor")
        void desaparece_de_la_lista() throws Exception {
            // Arrange: dos proyectos, se elimina uno
            var autor = datos.crear("DESARROLLADOR", false);
            String token = autenticar(autor.correo());
            UUID proyecto = publicar(token);
            publicar(token, "Otro proyecto que se queda");

            // Act
            eliminar(proyecto, token, TITULO).andExpect(status().isNoContent());
            comoNuevaPeticion();

            // Assert
            mvc.perform(get(RUTA + "/autor/" + autor.id()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido", hasSize(1)))
                    .andExpect(jsonPath("$.contenido[0].titulo").value("Otro proyecto que se queda"));
        }

        @Test
        @DisplayName("un proyecto eliminado ya no se puede eliminar ni editar: 409")
        void eliminado_es_definitivo() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            String token = autenticar(autor.correo());
            UUID proyecto = publicar(token);
            eliminar(proyecto, token, TITULO).andExpect(status().isNoContent());
            comoNuevaPeticion();

            // Act + Assert: ARCHIVADO es terminal
            eliminar(proyecto, token, TITULO)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("PROYECTO_NO_EDITABLE"));

            mvc.perform(put(RUTA + "/" + proyecto)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpoProyecto("Intento de resucitarlo", datos.tecnologiasAprobadas(1))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("PROYECTO_NO_EDITABLE"));
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("HU-08.4: quien no es el autor ni moderador no puede eliminar")
    class RestriccionParaTerceros {

        @Test
        @DisplayName("otro desarrollador recibe 403 PROYECTO_AJENO, aunque confirme bien")
        void un_tercero_no_puede_eliminar() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var intruso = datos.crear("DESARROLLADOR", false);
            UUID proyecto = publicar(autenticar(autor.correo()));

            // Act + Assert: conocer el titulo no da derecho a nada
            eliminar(proyecto, autenticar(intruso.correo()), TITULO)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("PROYECTO_AJENO"));

            assertThat(datos.estadoDePublicacion(proyecto)).isEqualTo("PUBLICADO");
        }

        @Test
        @DisplayName("el intento denegado queda registrado en auditoria")
        void el_intento_se_audita() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var intruso = datos.crear("DESARROLLADOR", false);
            UUID proyecto = publicar(autenticar(autor.correo()));

            // Act
            eliminar(proyecto, autenticar(intruso.correo()), TITULO)
                    .andExpect(status().isForbidden());

            // Assert
            assertThat(datos.contarEventosAuditoria("ACCESO_DENEGADO", intruso.id())).isEqualTo(1);
        }

        @Test
        @DisplayName("un visitante no puede eliminar: su rol no tiene el permiso")
        void un_visitante_no_puede() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var visitante = datos.crear("VISITANTE", false);
            UUID proyecto = publicar(autenticar(autor.correo()));

            // Act + Assert: lo frena @PreAuthorize antes de llegar al caso de uso
            eliminar(proyecto, autenticar(visitante.correo()), TITULO)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("ACCESO_DENEGADO"));
        }

        @Test
        @DisplayName("un moderador con MFA puede eliminar un proyecto ajeno, y queda auditado")
        void un_moderador_puede_eliminar() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var moderador = datos.crear("MODERADOR", true);
            UUID proyecto = publicar(autenticar(autor.correo()));

            // Act
            eliminar(proyecto, autenticar(moderador.correo()), TITULO)
                    .andExpect(status().isNoContent());

            // Assert
            assertThat(datos.estadoDePublicacion(proyecto)).isEqualTo("ARCHIVADO");
            assertThat(jdbc.queryForObject(
                    "SELECT motivo FROM historial_estado_publicacion WHERE publicacion_id = ?",
                    String.class, proyecto)).isEqualTo("Eliminado por moderacion.");
            assertThat(datos.contarEventosAuditoria("UPDATE", moderador.id())).isEqualTo(1);
        }

        @Test
        @DisplayName("sin token no se puede eliminar")
        void exige_autenticacion() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            UUID proyecto = publicar(autenticar(autor.correo()));

            // Act + Assert
            mvc.perform(post(RUTA + "/" + proyecto + "/archivado")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(confirmacion(TITULO)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("un proyecto inexistente responde 404")
        void proyecto_inexistente() throws Exception {
            // Arrange
            var dev = datos.crear("DESARROLLADOR", false);

            // Act + Assert
            eliminar(UUID.randomUUID(), autenticar(dev.correo()), TITULO)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("PROYECTO_NO_ENCONTRADO"));
        }
    }

    // ==================================================================

    private ResultActions eliminar(UUID proyecto, String token, String titulo) throws Exception {
        return mvc.perform(post(RUTA + "/" + proyecto + "/archivado")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(confirmacion(titulo)));
    }

    private UUID publicar(String token) throws Exception {
        return publicar(token, TITULO);
    }

    /** Publica por la API, como lo haria un cliente, y devuelve el identificador. */
    private UUID publicar(String token, String titulo) throws Exception {
        MvcResult resultado = mvc.perform(post(RUTA)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoProyecto(titulo, datos.tecnologiasAprobadas(2))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(resultado.getResponse().getContentAsString(), "$.id"));
    }

    /**
     * Vacia la sesion de Hibernate, como ocurre de verdad entre dos peticiones.
     *
     * <p>Archivar cambia el estado por JDBC, por debajo de Hibernate, y aqui todas las
     * peticiones comparten la sesion de la transaccion de la prueba, que aun recuerda
     * PUBLICADO. En produccion cada peticion abre la suya. Ver {@code EditarProyectoIT}.</p>
     */
    private void comoNuevaPeticion() {
        entityManager.flush();
        entityManager.clear();
    }

    private static String confirmacion(String titulo) {
        return "{\"confirmacionTitulo\": \"" + titulo + "\"}";
    }

    private String cuerpoProyecto(String titulo, List<Short> stack) {
        return """
                {"titulo": "%s", "descripcion": "%s", "estadoProyecto": "ESTABLE", "tecnologias": %s}
                """.formatted(titulo, DESCRIPCION, stack);
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
