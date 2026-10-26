package com.codefactory.devnet;

import com.codefactory.devnet.profile.api.PerfilConsulta;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Perfil tecnico del desarrollador, verificado extremo a extremo.
 */
@PruebaIntegracion
@DisplayName("Perfil del desarrollador (integracion)")
class PerfilIT {

    private static final String RUTA = "/api/v1/perfiles/";
    private static final String BIOGRAFIA = "Backend developer con foco en sistemas distribuidos.";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SoporteDatosPrueba datos;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PerfilConsulta perfilConsulta;

    // ==================================================================
    @Nested
    @DisplayName("Consultar un perfil publico")
    class Consultar {

        @Test
        @DisplayName("200 sin token, con los nombres de las tecnologias y no sus identificadores")
        void devuelve_el_perfil_publico() throws Exception {
            // Arrange: SoporteDatosPrueba declara Java en nivel AVANZADO
            var ana = datos.crear("DESARROLLADOR", false);

            // Act + Assert
            mvc.perform(get(RUTA + ana.id()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(ana.id().toString()))
                    .andExpect(jsonPath("$.nombre").value("Usuario " + ana.id().toString().substring(0, 8)))
                    .andExpect(jsonPath("$.tecnologias", hasSize(1)))
                    .andExpect(jsonPath("$.tecnologias[0]").value("Java"))
                    .andExpect(jsonPath("$.proyectosPublicados", hasSize(0)));
        }

        @Test
        @DisplayName("404 PERFIL_NO_ENCONTRADO si no existe")
        void perfil_inexistente() throws Exception {
            mvc.perform(get(RUTA + UUID.randomUUID()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("PERFIL_NO_ENCONTRADO"));
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Editar el perfil propio")
    class Editar {

        @Test
        @DisplayName("200, guarda los cambios y el stack queda ordenado y sin duplicados")
        void edita_el_perfil() throws Exception {
            // Arrange: la segunda tecnologia se declara dos veces y antes que la primera
            var ana = datos.crear("DESARROLLADOR", false);
            List<Short> ids = datos.tecnologiasAprobadas(2);
            String tecnologias = """
                    [{"tecnologiaId": %d, "nivel": "EXPERTO", "anios": 5},
                     {"tecnologiaId": %d, "nivel": "BASICO"},
                     {"tecnologiaId": %d, "nivel": "INTERMEDIO", "anios": 1}]
                    """.formatted(ids.get(1), ids.get(0), ids.get(1));

            // Act
            mvc.perform(editar(ana.id(), autenticar(ana.correo()),
                            cuerpo("  Ana Restrepo  ", tecnologias, "https://github.com/ana")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nombre").value("Ana Restrepo"))
                    .andExpect(jsonPath("$.biografia").value(BIOGRAFIA))
                    .andExpect(jsonPath("$.githubUrl").value("https://github.com/ana"))
                    .andExpect(jsonPath("$.linkedinUrl").doesNotExist())
                    .andExpect(jsonPath("$.tecnologias", hasSize(2)))
                    .andExpect(jsonPath("$.tecnologias[0].id").value(ids.get(0).intValue()))
                    .andExpect(jsonPath("$.tecnologias[0].nivel").value("BASICO"))
                    .andExpect(jsonPath("$.tecnologias[0].nombre").value(nombreTecnologia(ids.get(0))))
                    // Se conserva la primera aparicion: EXPERTO, no INTERMEDIO
                    .andExpect(jsonPath("$.tecnologias[1].id").value(ids.get(1).intValue()))
                    .andExpect(jsonPath("$.tecnologias[1].nivel").value("EXPERTO"))
                    .andExpect(jsonPath("$.tecnologias[1].anios").value(5));

            // Assert: la lectura publica ve lo mismo
            mvc.perform(get(RUTA + ana.id()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nombre").value("Ana Restrepo"))
                    .andExpect(jsonPath("$.tecnologias", hasSize(2)));
        }

        @Test
        @DisplayName("403 PERFIL_AJENO al editar el perfil de otra persona")
        void perfil_ajeno() throws Exception {
            // Arrange
            var ana = datos.crear("DESARROLLADOR", false);
            var luis = datos.crear("DESARROLLADOR", false);

            // Act + Assert: Ana usa su token sobre el id de Luis
            mvc.perform(editar(luis.id(), autenticar(ana.correo()), cuerpoValido()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("PERFIL_AJENO"));
        }

        @Test
        @DisplayName("401 AUTH_REQUERIDA sin token")
        void sin_token() throws Exception {
            // Arrange
            var ana = datos.crear("DESARROLLADOR", false);

            // Act + Assert
            mvc.perform(put(RUTA + ana.id())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpoValido()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("AUTH_REQUERIDA"));
        }

        @Test
        @DisplayName("404 PERFIL_NO_ENCONTRADO si el titular aun no tiene perfil")
        void titular_sin_perfil() throws Exception {
            // Arrange
            var sinPerfil = datos.crearSinPerfil("DESARROLLADOR");

            // Act + Assert
            mvc.perform(editar(sinPerfil.id(), autenticar(sinPerfil.correo()), cuerpoValido()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("PERFIL_NO_ENCONTRADO"));
        }

        @Test
        @DisplayName("422 PERFIL_TECNOLOGIA_DESCONOCIDA con una tecnologia no aprobada")
        void tecnologia_no_aprobada() throws Exception {
            // Arrange
            var ana = datos.crear("DESARROLLADOR", false);
            short propuesta = tecnologiaNoAprobada();

            // Act + Assert
            mvc.perform(editar(ana.id(), autenticar(ana.correo()),
                            cuerpo("Ana", tecnologia(propuesta), null)))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.errorCode").value("PERFIL_TECNOLOGIA_DESCONOCIDA"))
                    .andExpect(jsonPath("$.details[?(@.campo == 'tecnologias')]").exists());
        }

        @Test
        @DisplayName("400 PERFIL_INVALIDO con un enlace que no es https")
        void enlace_sin_https() throws Exception {
            // Arrange
            var ana = datos.crear("DESARROLLADOR", false);
            String tecnologias = tecnologia(datos.tecnologiasAprobadas(1).get(0));

            // Act + Assert
            mvc.perform(editar(ana.id(), autenticar(ana.correo()),
                            cuerpo("Ana", tecnologias, "http://github.com/ana")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("PERFIL_INVALIDO"))
                    .andExpect(jsonPath("$.details[?(@.campo == 'githubUrl')]").exists());
        }

        @Test
        @DisplayName("400 VALIDACION_FALLIDA sin ninguna tecnologia")
        void sin_tecnologias() throws Exception {
            // Arrange
            var ana = datos.crear("DESARROLLADOR", false);

            // Act + Assert
            mvc.perform(editar(ana.id(), autenticar(ana.correo()), cuerpo("Ana", "[]", null)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDACION_FALLIDA"))
                    .andExpect(jsonPath("$.details[?(@.campo == 'tecnologias')]").exists());
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Cambiar el avatar propio")
    class Avatar {

        @Test
        @DisplayName("200, el avatar nuevo aparece en la respuesta y en el perfil publico")
        void cambia_el_avatar() throws Exception {
            // Arrange
            var ana = datos.crear("DESARROLLADOR", false);
            String url = "https://cdn.devnet.test/avatares/ana.png";

            // Act
            mvc.perform(avatar(ana.id(), autenticar(ana.correo()), url))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.avatarUrl").value(url))
                    .andExpect(jsonPath("$.tecnologias[0].nombre").value("Java"));

            // Assert
            mvc.perform(get(RUTA + ana.id()))
                    .andExpect(jsonPath("$.avatarUrl").value(url));
        }

        @Test
        @DisplayName("403 PERFIL_AJENO al cambiar el avatar de otra persona")
        void avatar_ajeno() throws Exception {
            // Arrange
            var ana = datos.crear("DESARROLLADOR", false);
            var luis = datos.crear("DESARROLLADOR", false);

            // Act + Assert
            mvc.perform(avatar(luis.id(), autenticar(ana.correo()), "https://cdn.devnet.test/a.png"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("PERFIL_AJENO"));
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Consulta que el modulo ofrece a los demas (PerfilConsulta)")
    class ConsultaEntreModulos {

        @Test
        @DisplayName("resume el perfil: verificado por GitHub y con su numero de tecnologias")
        void resume_el_perfil() {
            // Arrange: SoporteDatosPrueba vincula una identidad de GitHub
            var ana = datos.crear("DESARROLLADOR", false);

            // Act
            var resumen = perfilConsulta.buscar(ana.id());

            // Assert
            assertThat(resumen).hasValueSatisfying(r -> {
                assertThat(r.usuarioId()).isEqualTo(ana.id());
                assertThat(r.verificado()).isTrue();
                assertThat(r.tecnologias()).isEqualTo(1);
            });
        }

        @Test
        @DisplayName("vacio si el usuario no tiene perfil")
        void sin_perfil() {
            assertThat(perfilConsulta.buscar(UUID.randomUUID())).isEmpty();
        }
    }

    // ------------------------------------------------------------------

    private static RequestBuilder editar(UUID id, String token, String cuerpo) {
        return put(RUTA + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private static RequestBuilder avatar(UUID id, String token, String url) {
        return patch(RUTA + id + "/avatar")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"avatarUrl\": \"" + url + "\"}");
    }

    private static String cuerpo(String nombre, String tecnologias, String githubUrl) {
        return """
                {"nombre": "%s", "biografia": "%s", "tecnologias": %s, "githubUrl": %s}
                """.formatted(nombre, BIOGRAFIA, tecnologias,
                githubUrl == null ? "null" : "\"" + githubUrl + "\"");
    }

    private String cuerpoValido() {
        return cuerpo("Ana", tecnologia(datos.tecnologiasAprobadas(1).get(0)), null);
    }

    private static String tecnologia(short id) {
        return "[{\"tecnologiaId\": " + id + ", \"nivel\": \"AVANZADO\"}]";
    }

    private String nombreTecnologia(short id) {
        return jdbc.queryForObject("SELECT nombre FROM tecnologia WHERE id = ?", String.class, id);
    }

    /** Una tecnologia propuesta que nadie ha aprobado todavia: existe, pero no es valida. */
    private short tecnologiaNoAprobada() {
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        Short id = jdbc.queryForObject("""
                INSERT INTO tecnologia (nombre, slug, categoria, aprobada)
                VALUES (?, ?, 'OTRA', false)
                RETURNING id
                """, Short.class, "Propuesta " + sufijo, "propuesta-" + sufijo);
        return id;
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
