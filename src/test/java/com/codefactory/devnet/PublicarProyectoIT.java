package com.codefactory.devnet;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-07 (AB#17): publicar un proyecto, verificado extremo a extremo.
 *
 * <p>Complementa a {@code ProyectoTest}, que prueba las mismas reglas de forma
 * unitaria. Aqui se verifica que ademas se persiste en las cuatro tablas y que el
 * proyecto queda visible en la lista del autor.</p>
 */
@PruebaIntegracion
@DisplayName("HU-07 Publicar un proyecto (integracion)")
class PublicarProyectoIT {

    private static final String RUTA = "/api/v1/proyectos";

    private static final String TITULO = "Motor de plantillas en Java";
    private static final String DESCRIPCION =
            "Un motor de plantillas minimalista escrito en Java 17 con soporte para expresiones.";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SoporteDatosPrueba datos;

    // ==================================================================
    @Nested
    @DisplayName("Criterio 1: queda asociado al autor y visible en su lista")
    class CriterioUno {

        @Test
        @DisplayName("publica y devuelve 201 con la cabecera Location")
        void publica_correctamente() throws Exception {
            // Arrange
            var dev = datos.crear("DESARROLLADOR", false);
            String token = autenticar(dev.correo());
            List<Short> stack = datos.tecnologiasAprobadas(2);

            // Act + Assert
            mvc.perform(post(RUTA)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(TITULO, DESCRIPCION, stack,
                                    "https://github.com/prueba/" + UUID.randomUUID())))
                    .andExpect(status().isCreated())
                    .andExpect(header().exists("Location"))
                    .andExpect(header().exists("X-Trace-Id"))
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.titulo").value(TITULO))
                    .andExpect(jsonPath("$.publicadoEn").isNotEmpty());
        }

        @Test
        @DisplayName("el proyecto aparece despues en la lista del autor")
        void aparece_en_la_lista_del_autor() throws Exception {
            // Arrange
            var dev = datos.crear("DESARROLLADOR", false);
            String token = autenticar(dev.correo());
            publicar(token, TITULO, DESCRIPCION);

            // Act + Assert: lectura publica, sin token
            mvc.perform(get(RUTA + "/autor/" + dev.id()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido").isArray())
                    .andExpect(jsonPath("$.contenido[0].titulo").value(TITULO))
                    .andExpect(jsonPath("$.contenido[0].autorId").value(dev.id().toString()))
                    .andExpect(jsonPath("$.contenido[0].autorNombreUsuario").value(dev.nombreUsuario()))
                    .andExpect(jsonPath("$.hayMas").value(false));
        }

        @Test
        @DisplayName("persiste el stack tecnologico junto al proyecto")
        void persiste_el_stack() throws Exception {
            // Arrange
            var dev = datos.crear("DESARROLLADOR", false);
            String token = autenticar(dev.correo());
            List<Short> stack = datos.tecnologiasAprobadas(3);

            // Act
            mvc.perform(post(RUTA)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(TITULO, DESCRIPCION, stack, null)))
                    .andExpect(status().isCreated());

            // Assert
            mvc.perform(get(RUTA + "/autor/" + dev.id()))
                    .andExpect(jsonPath("$.contenido[0].tecnologias", org.hamcrest.Matchers.hasSize(3)));
        }

        // Existio aqui una prueba que exigia perfil para publicar
        // (PROYECTO_PERFIL_INEXISTENTE). Se retiro al integrar los modulos: esa
        // comprobacion obligaba a 'project' a consultar 'profile', y 'profile' ya
        // depende de 'project' para mostrar los proyectos del perfil publico. El ciclo
        // resultante habria hecho imposible extraer cualquiera de los dos modulos,
        // que es justo lo que el monolito modular existe para evitar.
        //
        // El perfil se crea junto con la cuenta, asi que su ausencia seria una
        // inconsistencia previa y no un caso de uso.
    }

    // ==================================================================
    @Nested
    @DisplayName("Criterio 2: los campos obligatorios se validan")
    class CriterioDos {

        @Test
        @DisplayName("rechaza sin titulo con 400 y el campo senalado")
        void rechaza_sin_titulo() throws Exception {
            // Arrange
            var dev = datos.crear("DESARROLLADOR", false);
            String token = autenticar(dev.correo());

            // Act + Assert
            mvc.perform(post(RUTA)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(null, DESCRIPCION, datos.tecnologiasAprobadas(1), null)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDACION_FALLIDA"))
                    .andExpect(jsonPath("$.details[?(@.campo == 'titulo')]").exists());
        }

        @Test
        @DisplayName("rechaza sin descripcion")
        void rechaza_sin_descripcion() throws Exception {
            // Arrange
            var dev = datos.crear("DESARROLLADOR", false);
            String token = autenticar(dev.correo());

            // Act + Assert
            mvc.perform(post(RUTA)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(TITULO, null, datos.tecnologiasAprobadas(1), null)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.details[?(@.campo == 'descripcion')]").exists());
        }

        @Test
        @DisplayName("rechaza tecnologias inexistentes con 422")
        void rechaza_tecnologias_desconocidas() throws Exception {
            // Arrange
            var dev = datos.crear("DESARROLLADOR", false);
            String token = autenticar(dev.correo());

            // Act + Assert: 422 y no 400, porque la peticion esta bien formada; lo
            // que falla es una regla que depende del estado del catalogo
            mvc.perform(post(RUTA)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(TITULO, DESCRIPCION, List.of((short) 32000), null)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errorCode").value("PROYECTO_TECNOLOGIA_DESCONOCIDA"));
        }

        @Test
        @DisplayName("rechaza un repositorio ya enlazado a otro proyecto con 409")
        void rechaza_repositorio_duplicado() throws Exception {
            // Arrange
            var dev = datos.crear("DESARROLLADOR", false);
            String token = autenticar(dev.correo());
            String repo = "https://github.com/prueba/" + UUID.randomUUID();
            List<Short> stack = datos.tecnologiasAprobadas(1);

            mvc.perform(post(RUTA)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(TITULO, DESCRIPCION, stack, repo)))
                    .andExpect(status().isCreated());

            // Act + Assert: 409 legible en vez de la violacion de la restriccion UNIQUE
            mvc.perform(post(RUTA)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo("Otro proyecto distinto", DESCRIPCION, stack, repo)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("PROYECTO_REPOSITORIO_DUPLICADO"));
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Criterio 3: solo el autor publica en su nombre")
    class CriterioTres {

        @Test
        @DisplayName("rechaza con 403 si el cuerpo declara otro autor")
        void rechaza_autor_ajeno() throws Exception {
            // Arrange
            var dev = datos.crear("DESARROLLADOR", false);
            var otro = datos.crear("DESARROLLADOR", false);
            String token = autenticar(dev.correo());

            // Act + Assert: no se ignora en silencio, para que nadie crea estar
            // publicando en nombre de otro
            mvc.perform(post(RUTA)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"autorId": "%s", "titulo": "%s", "descripcion": "%s", "tecnologias": [%d]}
                                    """.formatted(otro.id(), TITULO, DESCRIPCION,
                                    datos.tecnologiasAprobadas(1).get(0))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("PROYECTO_AUTOR_AJENO"));
        }

        @Test
        @DisplayName("sin token no se puede publicar")
        void exige_autenticacion() throws Exception {
            // Act + Assert
            mvc.perform(post(RUTA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(TITULO, DESCRIPCION, datos.tecnologiasAprobadas(1), null)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("un rol sin el permiso publicacion:crear recibe 403")
        void exige_el_permiso_de_crear() throws Exception {
            // Arrange: ADMIN administra, pero no tiene publicacion:crear
            var admin = datos.crear("ADMIN", true);
            String token = autenticar(admin.correo());

            // Act + Assert: aqui HU-06 y HU-07 se cruzan
            mvc.perform(post(RUTA)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo(TITULO, DESCRIPCION, datos.tecnologiasAprobadas(1), null)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("ACCESO_DENEGADO"));
        }
    }

    // ==================================================================

    private void publicar(String token, String titulo, String descripcion) throws Exception {
        mvc.perform(post(RUTA)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(titulo, descripcion, datos.tecnologiasAprobadas(2), null)))
                .andExpect(status().isCreated());
    }

    /** Construye el JSON a mano: independiente de la version de Jackson. */
    private String cuerpo(String titulo, String descripcion, List<Short> tecnologias, String repo) {
        StringBuilder sb = new StringBuilder("{");
        if (titulo != null) {
            sb.append("\"titulo\": \"").append(titulo).append("\",");
        }
        if (descripcion != null) {
            sb.append("\"descripcion\": \"").append(descripcion).append("\",");
        }
        if (repo != null) {
            sb.append("\"urlRepositorio\": \"").append(repo).append("\",");
        }
        sb.append("\"estadoProyecto\": \"EN_DESARROLLO\",");
        sb.append("\"tecnologias\": [");
        for (int i = 0; i < tecnologias.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(tecnologias.get(i));
        }
        sb.append("]}");
        return sb.toString();
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