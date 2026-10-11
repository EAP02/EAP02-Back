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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-08 (AB#18): editar un proyecto, verificado extremo a extremo.
 *
 * <p>Complementa a {@code EditarProyectoTest}, que prueba las reglas de forma unitaria.
 * Aqui se verifica lo que solo se ve con la base real: que los cambios llegan a las
 * cuatro tablas, que la autorizacion por propiedad funciona con tokens de verdad y que
 * los intentos denegados quedan en {@code auditoria}.</p>
 *
 * <p><b>Requiere Docker.</b> Lo ejecuta Failsafe en {@code ./mvnw verify}.</p>
 */
@PruebaIntegracion
@DisplayName("HU-08 Editar un proyecto (integracion)")
class EditarProyectoIT {

    private static final String RUTA = "/api/v1/proyectos";

    private static final String TITULO = "Motor de plantillas en Java";
    private static final String DESCRIPCION =
            "Un motor de plantillas minimalista escrito en Java 17 con soporte para expresiones.";
    private static final String TITULO_NUEVO = "Motor de plantillas en Java 2.0";
    private static final String DESCRIPCION_NUEVA =
            "Motor de plantillas minimalista en Java 17, ahora con soporte para bucles.";

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
    @DisplayName("HU-08.1: el autor edita su proyecto y los cambios se persisten")
    class EdicionDelAutor {

        @Test
        @DisplayName("responde 200 con el proyecto tal como quedo")
        void edita_correctamente() throws Exception {
            // Arrange
            var dev = datos.crear("DESARROLLADOR", false);
            String token = autenticar(dev.correo());
            UUID proyecto = publicar(token, null);
            List<Short> stackNuevo = datos.tecnologiasAprobadas(3);

            // Act + Assert
            mvc.perform(put(RUTA + "/" + proyecto)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(TITULO_NUEVO, DESCRIPCION_NUEVA, stackNuevo, null)))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("X-Trace-Id"))
                    .andExpect(jsonPath("$.id").value(proyecto.toString()))
                    .andExpect(jsonPath("$.autorId").value(dev.id().toString()))
                    .andExpect(jsonPath("$.titulo").value(TITULO_NUEVO))
                    .andExpect(jsonPath("$.descripcion").value(DESCRIPCION_NUEVA))
                    .andExpect(jsonPath("$.estadoProyecto").value("ESTABLE"))
                    .andExpect(jsonPath("$.tecnologias", hasSize(3)));
        }

        @Test
        @DisplayName("los cambios quedan guardados y se ven en la lista del autor")
        void los_cambios_persisten() throws Exception {
            // Arrange
            var dev = datos.crear("DESARROLLADOR", false);
            String token = autenticar(dev.correo());
            UUID proyecto = publicar(token, null);
            String publicadoEnAntes = publicadoEn(proyecto);

            // Act
            mvc.perform(put(RUTA + "/" + proyecto)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(TITULO_NUEVO, DESCRIPCION_NUEVA, datos.tecnologiasAprobadas(1), null)))
                    .andExpect(status().isOk());

            // Assert: en la base...
            assertThat(jdbc.queryForObject("SELECT contenido FROM publicacion WHERE id = ?",
                    String.class, proyecto)).isEqualTo(DESCRIPCION_NUEVA);
            assertThat(jdbc.queryForObject("SELECT estado_proyecto FROM publicacion_proyecto WHERE publicacion_id = ?",
                    String.class, proyecto)).isEqualTo("ESTABLE");
            assertThat(publicadoEn(proyecto))
                    .as("editar no es volver a publicar")
                    .isEqualTo(publicadoEnAntes);

            // ...y en la lectura publica, sin token, como la haria otro cliente
            comoNuevaPeticion();
            mvc.perform(get(RUTA + "/autor/" + dev.id()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido", hasSize(1)))
                    .andExpect(jsonPath("$.contenido[0].titulo").value(TITULO_NUEVO))
                    .andExpect(jsonPath("$.contenido[0].tecnologias", hasSize(1)));
        }

        @Test
        @DisplayName("reemplaza el stack tecnologico, no lo acumula")
        void reemplaza_el_stack() throws Exception {
            // Arrange: publicado con las tecnologias 1 y 2
            var dev = datos.crear("DESARROLLADOR", false);
            String token = autenticar(dev.correo());
            UUID proyecto = publicar(token, null);
            List<Short> catalogo = datos.tecnologiasAprobadas(4);
            List<Short> stackNuevo = List.of(catalogo.get(2), catalogo.get(3));

            // Act
            mvc.perform(put(RUTA + "/" + proyecto)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(TITULO_NUEVO, DESCRIPCION_NUEVA, stackNuevo, null)))
                    .andExpect(status().isOk());

            // Assert
            List<Short> guardadas = jdbc.queryForList(
                    "SELECT tecnologia_id FROM publicacion_tecnologia WHERE publicacion_id = ?",
                    Short.class, proyecto);
            assertThat(guardadas).containsExactlyInAnyOrderElementsOf(stackNuevo);
        }

        @Test
        @DisplayName("cambia el repositorio enlazado por el nuevo")
        void cambia_el_repositorio() throws Exception {
            // Arrange
            var dev = datos.crear("DESARROLLADOR", false);
            String token = autenticar(dev.correo());
            UUID proyecto = publicar(token, repoAleatorio());
            String repoNuevo = repoAleatorio();

            // Act
            mvc.perform(put(RUTA + "/" + proyecto)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(TITULO_NUEVO, DESCRIPCION_NUEVA, datos.tecnologiasAprobadas(1), repoNuevo)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.urlRepositorio").value(repoNuevo));

            // Assert: el antiguo desaparece, no se acumula
            assertThat(jdbc.queryForList("SELECT url FROM repositorio WHERE publicacion_proyecto_id = ?",
                    String.class, proyecto)).containsExactly(repoNuevo);
        }

        @Test
        @DisplayName("conservar el mismo repositorio no cuenta como duplicado")
        void conserva_el_mismo_repositorio() throws Exception {
            // Arrange
            var dev = datos.crear("DESARROLLADOR", false);
            String token = autenticar(dev.correo());
            String repo = repoAleatorio();
            UUID proyecto = publicar(token, repo);

            // Act + Assert
            mvc.perform(put(RUTA + "/" + proyecto)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(TITULO_NUEVO, DESCRIPCION_NUEVA, datos.tecnologiasAprobadas(1), repo)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.urlRepositorio").value(repo));
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("HU-08.2: quien no es el autor ni moderador no puede editar")
    class RestriccionParaTerceros {

        @Test
        @DisplayName("otro desarrollador recibe 403 PROYECTO_AJENO y el proyecto no cambia")
        void un_tercero_no_puede_editar() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var intruso = datos.crear("DESARROLLADOR", false);
            UUID proyecto = publicar(autenticar(autor.correo()), null);

            // Act + Assert
            mvc.perform(put(RUTA + "/" + proyecto)
                            .header("Authorization", "Bearer " + autenticar(intruso.correo()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo("Secuestrado por otro", DESCRIPCION_NUEVA, datos.tecnologiasAprobadas(1), null)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("PROYECTO_AJENO"));

            assertThat(jdbc.queryForObject("SELECT titulo FROM publicacion WHERE id = ?",
                    String.class, proyecto)).isEqualTo(TITULO);
        }

        @Test
        @DisplayName("el intento denegado queda registrado en auditoria")
        void el_intento_se_audita() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var intruso = datos.crear("DESARROLLADOR", false);
            UUID proyecto = publicar(autenticar(autor.correo()), null);

            // Act
            mvc.perform(put(RUTA + "/" + proyecto)
                            .header("Authorization", "Bearer " + autenticar(intruso.correo()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(TITULO_NUEVO, DESCRIPCION_NUEVA, datos.tecnologiasAprobadas(1), null)))
                    .andExpect(status().isForbidden());

            // Assert: lo mismo que HU-06 exige para cualquier acceso denegado
            assertThat(datos.contarEventosAuditoria("ACCESO_DENEGADO", intruso.id())).isEqualTo(1);
        }

        @Test
        @DisplayName("un moderador con MFA puede editar un proyecto ajeno, y queda auditado")
        void un_moderador_puede_editar() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var moderador = datos.crear("MODERADOR", true);
            UUID proyecto = publicar(autenticar(autor.correo()), null);

            // Act + Assert
            mvc.perform(put(RUTA + "/" + proyecto)
                            .header("Authorization", "Bearer " + autenticar(moderador.correo()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(TITULO_NUEVO, DESCRIPCION_NUEVA, datos.tecnologiasAprobadas(1), null)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.autorId").value(autor.id().toString()));

            // Assert: el autor sigue siendo el mismo, y la intervencion deja rastro
            assertThat(datos.contarEventosAuditoria("UPDATE", moderador.id())).isEqualTo(1);
        }

        @Test
        @DisplayName("un moderador SIN MFA no ejerce el permiso y recibe 403")
        void moderador_sin_mfa_no_puede() throws Exception {
            // Arrange: su token sale sin permisos (ADR-004)
            var autor = datos.crear("DESARROLLADOR", false);
            var moderador = datos.crear("MODERADOR", false);
            UUID proyecto = publicar(autenticar(autor.correo()), null);

            // Act + Assert
            mvc.perform(put(RUTA + "/" + proyecto)
                            .header("Authorization", "Bearer " + autenticar(moderador.correo()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(TITULO_NUEVO, DESCRIPCION_NUEVA, datos.tecnologiasAprobadas(1), null)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("ACCESO_DENEGADO"));
        }

        @Test
        @DisplayName("sin token no se puede editar")
        void exige_autenticacion() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            UUID proyecto = publicar(autenticar(autor.correo()), null);

            // Act + Assert
            mvc.perform(put(RUTA + "/" + proyecto)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(TITULO_NUEVO, DESCRIPCION_NUEVA, datos.tecnologiasAprobadas(1), null)))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Reglas de negocio de la edicion")
    class Reglas {

        @Test
        @DisplayName("valida los campos igual que al publicar")
        void valida_los_campos() throws Exception {
            // Arrange
            var dev = datos.crear("DESARROLLADOR", false);
            String token = autenticar(dev.correo());
            UUID proyecto = publicar(token, null);

            // Act + Assert
            mvc.perform(put(RUTA + "/" + proyecto)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo("abc", DESCRIPCION_NUEVA, datos.tecnologiasAprobadas(1), null)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDACION_FALLIDA"))
                    .andExpect(jsonPath("$.details[?(@.campo == 'titulo')]").exists());
        }

        @Test
        @DisplayName("rechaza tecnologias inexistentes con 422")
        void rechaza_tecnologias_desconocidas() throws Exception {
            // Arrange
            var dev = datos.crear("DESARROLLADOR", false);
            String token = autenticar(dev.correo());
            UUID proyecto = publicar(token, null);

            // Act + Assert
            mvc.perform(put(RUTA + "/" + proyecto)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(TITULO_NUEVO, DESCRIPCION_NUEVA, List.of((short) 32000), null)))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errorCode").value("PROYECTO_TECNOLOGIA_DESCONOCIDA"));
        }

        @Test
        @DisplayName("rechaza enlazar un repositorio que ya es de otro proyecto con 409")
        void rechaza_repositorio_de_otro_proyecto() throws Exception {
            // Arrange
            var dev = datos.crear("DESARROLLADOR", false);
            String token = autenticar(dev.correo());
            String repoAjeno = repoAleatorio();
            publicar(token, repoAjeno);
            UUID proyecto = publicar(token, null);

            // Act + Assert
            mvc.perform(put(RUTA + "/" + proyecto)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(TITULO_NUEVO, DESCRIPCION_NUEVA, datos.tecnologiasAprobadas(1), repoAjeno)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("PROYECTO_REPOSITORIO_DUPLICADO"));
        }

        @Test
        @DisplayName("un proyecto inexistente responde 404")
        void proyecto_inexistente() throws Exception {
            // Arrange
            var dev = datos.crear("DESARROLLADOR", false);

            // Act + Assert
            mvc.perform(put(RUTA + "/" + UUID.randomUUID())
                            .header("Authorization", "Bearer " + autenticar(dev.correo()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(TITULO_NUEVO, DESCRIPCION_NUEVA, datos.tecnologiasAprobadas(1), null)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("PROYECTO_NO_ENCONTRADO"));
        }

        @Test
        @DisplayName("el autor no puede editar un proyecto oculto por moderacion: 409")
        void no_se_edita_un_proyecto_oculto() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            String token = autenticar(autor.correo());
            UUID proyecto = publicar(token, null);
            ocultar(proyecto);

            // Act + Assert: cambiar lo que motivo la ocultacion borraria la evidencia
            mvc.perform(put(RUTA + "/" + proyecto)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(TITULO_NUEVO, DESCRIPCION_NUEVA, datos.tecnologiasAprobadas(1), null)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("PROYECTO_NO_EDITABLE"));
        }

        @Test
        @DisplayName("un tercero que intenta editar un proyecto oculto recibe 404, no 403")
        void un_proyecto_oculto_no_se_revela() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            var intruso = datos.crear("DESARROLLADOR", false);
            UUID proyecto = publicar(autenticar(autor.correo()), null);
            ocultar(proyecto);

            // Act + Assert: un 403 confirmaria que existe (ADR-005)
            mvc.perform(put(RUTA + "/" + proyecto)
                            .header("Authorization", "Bearer " + autenticar(intruso.correo()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(TITULO_NUEVO, DESCRIPCION_NUEVA, datos.tecnologiasAprobadas(1), null)))
                    .andExpect(status().isNotFound());
        }
    }

    // ==================================================================

    /** Publica por la API, como lo haria un cliente, y devuelve el identificador. */
    private UUID publicar(String token, String repo) throws Exception {
        List<Short> stack = datos.tecnologiasAprobadas(2);
        MvcResult resultado = mvc.perform(post(RUTA)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoPublicacion(stack, repo)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(resultado.getResponse().getContentAsString(), "$.id"));
    }

    /** Oculta el proyecto por la API, con un moderador creado para la ocasion. */
    private void ocultar(UUID proyecto) throws Exception {
        var moderador = datos.crear("MODERADOR", true);
        mvc.perform(post(RUTA + "/" + proyecto + "/ocultamiento")
                        .header("Authorization", "Bearer " + autenticar(moderador.correo()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\": \"Contenido duplicado de otro proyecto ya publicado.\"}"))
                .andExpect(status().isNoContent());
        assertThat(datos.estadoDePublicacion(proyecto)).isEqualTo("OCULTO");

        // Ocultar cambia el estado por JDBC, por debajo de Hibernate: la sesion
        // compartida aun recuerda PUBLICADO.
        comoNuevaPeticion();
    }

    /**
     * Vacia la sesion de Hibernate, como ocurre de verdad entre dos peticiones.
     *
     * <p>En produccion cada peticion abre su propia sesion y lee la base. Aqui todas
     * comparten la de la transaccion de la prueba, que conserva en memoria las entidades
     * de peticiones anteriores; sin esto, una lectura posterior podria devolver la copia
     * en memoria y no lo que realmente quedo guardado.</p>
     */
    private void comoNuevaPeticion() {
        entityManager.flush();
        entityManager.clear();
    }

    private String publicadoEn(UUID proyecto) {
        return jdbc.queryForObject("SELECT publicado_en::text FROM publicacion WHERE id = ?",
                String.class, proyecto);
    }

    private static String repoAleatorio() {
        return "https://github.com/prueba/" + UUID.randomUUID();
    }

    private String cuerpoPublicacion(List<Short> stack, String repo) {
        return """
                {"titulo": "%s", "descripcion": "%s", "estadoProyecto": "EN_DESARROLLO",
                 "tecnologias": %s %s}
                """.formatted(TITULO, DESCRIPCION, stack,
                repo == null ? "" : ", \"urlRepositorio\": \"" + repo + "\"");
    }

    /** Construye el JSON a mano, como {@code PublicarProyectoIT}: independiente de Jackson. */
    private String cuerpo(String titulo, String descripcion, List<Short> tecnologias, String repo) {
        return """
                {"titulo": "%s", "descripcion": "%s", "estadoProyecto": "ESTABLE",
                 "tecnologias": %s %s}
                """.formatted(titulo, descripcion, tecnologias,
                repo == null ? "" : ", \"urlRepositorio\": \"" + repo + "\"");
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
