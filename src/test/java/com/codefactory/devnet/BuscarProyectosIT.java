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
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-09 (AB#19): explorar y buscar proyectos, verificado extremo a extremo.
 *
 * <p>Contra PostgreSQL real porque es la base quien hace la busqueda: el vector de texto
 * completo, el diccionario en espanol y el indice GIN no existen fuera de ella.</p>
 *
 * <p><b>Aislamiento.</b> Cada prueba inventa una palabra que no existe en ningun otro
 * proyecto y la pone en los suyos. Asi los resultados no dependen de lo que hayan dejado
 * otras pruebas, y se puede afirmar exactamente que aparece y que no.</p>
 *
 * <p>Todas las peticiones de busqueda van sin token: es lectura publica.</p>
 *
 * <p><b>Requiere Docker.</b> Lo ejecuta Failsafe en {@code ./mvnw verify}.</p>
 */
@PruebaIntegracion
@DisplayName("HU-09 Explorar y buscar proyectos (integracion)")
class BuscarProyectosIT {

    private static final String RUTA = "/api/v1/proyectos";
    private static final String DESCRIPCION =
            "Proyecto de prueba para verificar la busqueda por texto completo en espanol.";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SoporteDatosPrueba datos;

    @Autowired
    private JdbcTemplate jdbc;

    // ==================================================================
    @Nested
    @DisplayName("HU-09.1: la busqueda por texto devuelve una lista paginada de coincidencias")
    class BusquedaPorTexto {

        @Test
        @DisplayName("devuelve los proyectos que contienen el termino, y solo esos")
        void encuentra_por_texto() throws Exception {
            // Arrange
            String palabra = palabraUnica();
            String token = autenticarDesarrollador();
            UUID coincide = publicar(token, "Motor de plantillas " + palabra, tecnologia("java"));
            UUID noCoincide = publicar(token, "Gestor de inventarios sin la palabra", tecnologia("java"));

            // Act + Assert
            mvc.perform(get(RUTA).param("q", palabra))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido", hasSize(1)))
                    .andExpect(jsonPath("$.contenido[0].id").value(coincide.toString()))
                    .andExpect(jsonPath("$.contenido[*].id", not(hasItem(noCoincide.toString()))))
                    .andExpect(jsonPath("$.mensaje").value(nullValue()));
        }

        @Test
        @DisplayName("entiende espanol: 'plantilla' encuentra 'plantillas'")
        void aplica_el_diccionario_en_espanol() throws Exception {
            // Arrange
            String palabra = palabraUnica();
            String token = autenticarDesarrollador();
            UUID proyecto = publicar(token, "Motor de plantillas " + palabra, tecnologia("java"));

            // Act + Assert: singular contra plural, gracias al stemming de 'spanish'
            mvc.perform(get(RUTA).param("q", "plantilla " + palabra))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido[*].id", hasItem(proyecto.toString())));
        }

        @Test
        @DisplayName("busca tambien en la descripcion, no solo en el titulo")
        void busca_en_la_descripcion() throws Exception {
            // Arrange
            String palabra = palabraUnica();
            String token = autenticarDesarrollador();
            UUID proyecto = publicar(token, "Titulo sin la palabra buscada",
                    DESCRIPCION + " Clave: " + palabra, tecnologia("java"));

            // Act + Assert
            mvc.perform(get(RUTA).param("q", palabra))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido[0].id").value(proyecto.toString()));
        }

        @Test
        @DisplayName("pagina por cursor, del mas reciente al mas antiguo, sin repetir ni saltar")
        void pagina_los_resultados() throws Exception {
            // Arrange: tres proyectos con la misma palabra
            String palabra = palabraUnica();
            String token = autenticarDesarrollador();
            UUID primero = publicar(token, "Proyecto uno " + palabra, tecnologia("java"));
            UUID segundo = publicar(token, "Proyecto dos " + palabra, tecnologia("java"));
            UUID tercero = publicar(token, "Proyecto tres " + palabra, tecnologia("java"));

            // Act: pagina 1, de dos
            MvcResult pagina1 = mvc.perform(get(RUTA).param("q", palabra).param("limite", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido", hasSize(2)))
                    .andExpect(jsonPath("$.hayMas").value(true))
                    .andReturn();
            String json1 = pagina1.getResponse().getContentAsString();
            String cursor = JsonPath.read(json1, "$.cursorSiguiente");

            // Act: pagina 2, con el cursor tal cual
            MvcResult pagina2 = mvc.perform(get(RUTA).param("q", palabra).param("limite", "2")
                            .param("cursor", cursor))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido", hasSize(1)))
                    .andExpect(jsonPath("$.hayMas").value(false))
                    .andReturn();

            // Assert: el orden es de mas reciente a mas antiguo, entre las dos paginas
            List<String> ids = new java.util.ArrayList<>(JsonPath.read(json1, "$.contenido[*].id"));
            ids.addAll(JsonPath.read(pagina2.getResponse().getContentAsString(), "$.contenido[*].id"));
            assertThat(ids).containsExactly(tercero.toString(), segundo.toString(), primero.toString());
        }

        @Test
        @DisplayName("no muestra proyectos eliminados (archivados)")
        void excluye_los_no_publicados() throws Exception {
            // Arrange
            String palabra = palabraUnica();
            String token = autenticarDesarrollador();
            String titulo = "Proyecto eliminado " + palabra;
            UUID proyecto = publicar(token, titulo, tecnologia("java"));
            mvc.perform(post(RUTA + "/" + proyecto + "/archivado")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"confirmacionTitulo\": \"" + titulo + "\"}"))
                    .andExpect(status().isNoContent());

            // Act + Assert
            mvc.perform(get(RUTA).param("q", palabra))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido", hasSize(0)));
        }

        @Test
        @DisplayName("incluye el nombre de usuario del autor de cada proyecto")
        void resuelve_el_autor() throws Exception {
            // Arrange
            String palabra = palabraUnica();
            var dev = datos.crear("DESARROLLADOR", false);
            publicar(autenticar(dev.correo()), "Proyecto con autor " + palabra, tecnologia("java"));

            // Act + Assert
            mvc.perform(get(RUTA).param("q", palabra))
                    .andExpect(jsonPath("$.contenido[0].autorId").value(dev.id().toString()))
                    .andExpect(jsonPath("$.contenido[0].autorNombreUsuario").value(dev.nombreUsuario()));
        }

        @Test
        @DisplayName("rechaza un termino de mas de 100 caracteres con 400")
        void rechaza_termino_excesivo() throws Exception {
            // Act + Assert
            mvc.perform(get(RUTA).param("q", "a".repeat(101)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("PROYECTO_BUSQUEDA_INVALIDA"));
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("HU-09.2: el filtro por tecnologia muestra solo proyectos con esa etiqueta")
    class FiltroPorTecnologia {

        @Test
        @DisplayName("devuelve solo los proyectos etiquetados con la tecnologia")
        void filtra_por_tecnologia() throws Exception {
            // Arrange: dos proyectos con la misma palabra, cada uno con una tecnologia
            String palabra = palabraUnica();
            String token = autenticarDesarrollador();
            UUID conRust = publicar(token, "Motor en Rust " + palabra, tecnologia("rust"));
            UUID conGo = publicar(token, "Motor en Go " + palabra, tecnologia("go"));

            // Act + Assert
            mvc.perform(get(RUTA).param("q", palabra).param("tecnologia", "rust"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido", hasSize(1)))
                    .andExpect(jsonPath("$.contenido[0].id").value(conRust.toString()))
                    .andExpect(jsonPath("$.contenido[*].id", not(hasItem(conGo.toString()))));
        }

        @Test
        @DisplayName("sin texto, todos los resultados llevan la etiqueta filtrada")
        void todos_llevan_la_etiqueta() throws Exception {
            // Arrange
            String token = autenticarDesarrollador();
            UUID proyecto = publicar(token, "Servicio en Kotlin " + palabraUnica(), tecnologia("kotlin"));

            // Act + Assert
            mvc.perform(get(RUTA).param("tecnologia", "kotlin"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido[*].id", hasItem(proyecto.toString())))
                    .andExpect(jsonPath("$.contenido[*].tecnologias", everyItem(hasItem("Kotlin"))));
        }

        @Test
        @DisplayName("acepta el slug en mayusculas")
        void slug_sin_distinguir_mayusculas() throws Exception {
            // Arrange
            String palabra = palabraUnica();
            UUID proyecto = publicar(autenticarDesarrollador(), "Api en Spring " + palabra, tecnologia("spring-boot"));

            // Act + Assert
            mvc.perform(get(RUTA).param("q", palabra).param("tecnologia", "Spring-Boot"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido[0].id").value(proyecto.toString()));
        }

        @Test
        @DisplayName("el catalogo de tecnologias es publico y trae id, nombre y slug")
        void catalogo_publico() throws Exception {
            // Act + Assert: sin token
            mvc.perform(get("/api/v1/tecnologias"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.slug == 'java')].nombre").value(hasItem("Java")))
                    .andExpect(jsonPath("$[?(@.slug == 'java')].id").value(hasItem((int) tecnologia("java"))))
                    .andExpect(jsonPath("$[*].categoria").isNotEmpty());
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("HU-09.3: sin coincidencias hay un estado vacio con mensaje orientador")
    class EstadoVacio {

        @Test
        @DisplayName("un texto sin coincidencias responde 200, lista vacia y mensaje")
        void texto_sin_coincidencias() throws Exception {
            // Arrange
            String palabra = palabraUnica();

            // Act + Assert: 200 y no 404, la busqueda funciono; simplemente no hay nada
            mvc.perform(get(RUTA).param("q", palabra))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido", hasSize(0)))
                    .andExpect(jsonPath("$.hayMas").value(false))
                    .andExpect(jsonPath("$.cursorSiguiente").value(nullValue()))
                    .andExpect(jsonPath("$.mensaje", containsString(palabra)))
                    .andExpect(jsonPath("$.mensaje", containsString("otras palabras")));
        }

        @Test
        @DisplayName("texto y tecnologia sin coincidencias sugieren quitar el filtro")
        void filtro_sin_coincidencias() throws Exception {
            // Arrange: la palabra existe, pero no en un proyecto de Rust
            String palabra = palabraUnica();
            publicar(autenticarDesarrollador(), "Motor en Go " + palabra, tecnologia("go"));

            // Act + Assert
            mvc.perform(get(RUTA).param("q", palabra).param("tecnologia", "rust"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido", hasSize(0)))
                    .andExpect(jsonPath("$.mensaje", containsString("Rust")))
                    .andExpect(jsonPath("$.mensaje", containsString("quita el filtro")));
        }

        @Test
        @DisplayName("una tecnologia que no existe responde vacio explicando que no esta en el catalogo")
        void tecnologia_inexistente() throws Exception {
            // Act + Assert
            mvc.perform(get(RUTA).param("tecnologia", "cobol-2099"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido", hasSize(0)))
                    .andExpect(jsonPath("$.mensaje", containsString("no esta en el catalogo")));
        }
    }

    // ==================================================================

    /**
     * Una palabra de letras al azar que ningun otro proyecto contiene.
     *
     * <p>Solo letras: el analizador de texto de PostgreSQL trata distinto las mezclas de
     * letras y numeros, y no es eso lo que se prueba aqui.</p>
     */
    private static String palabraUnica() {
        Random azar = new Random();
        StringBuilder sb = new StringBuilder("zq");
        for (int i = 0; i < 10; i++) {
            sb.append((char) ('a' + azar.nextInt(26)));
        }
        return sb.toString();
    }

    private short tecnologia(String slug) {
        Short id = jdbc.queryForObject("SELECT id FROM tecnologia WHERE slug = ?", Short.class, slug);
        return id == null ? 0 : id;
    }

    private UUID publicar(String token, String titulo, short tecnologia) throws Exception {
        return publicar(token, titulo, DESCRIPCION, tecnologia);
    }

    /** Publica por la API, como lo haria un cliente, y devuelve el identificador. */
    private UUID publicar(String token, String titulo, String descripcion, short tecnologia) throws Exception {
        MvcResult resultado = mvc.perform(post(RUTA)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo": "%s", "descripcion": "%s", "tecnologias": [%d]}
                                """.formatted(titulo, descripcion, tecnologia)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(resultado.getResponse().getContentAsString(), "$.id"));
    }

    private String autenticarDesarrollador() throws Exception {
        return autenticar(datos.crear("DESARROLLADOR", false).correo());
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
