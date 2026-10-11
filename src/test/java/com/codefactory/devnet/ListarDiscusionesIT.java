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

import java.util.ArrayList;
import java.util.List;
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
 * HU-12: listar y filtrar discusiones, verificado extremo a extremo.
 *
 * <p>Todas las lecturas van sin token: es un listado publico.</p>
 *
 * <p><b>Aislamiento.</b> Las afirmaciones se hacen sobre las discusiones que crea cada
 * prueba (presentes, ausentes, en que orden), no sobre el total del listado, para no
 * depender de lo que haya en la base.</p>
 *
 * <p><b>Requiere Docker.</b> Lo ejecuta Failsafe en {@code ./mvnw verify}.</p>
 */
@PruebaIntegracion
@DisplayName("HU-12 Listar y filtrar discusiones (integracion)")
class ListarDiscusionesIT {

    private static final String RUTA = "/api/v1/discusiones";
    private static final String CUERPO =
            "Cuerpo de una discusion de prueba con la longitud minima que exige el dominio.";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SoporteDatosPrueba datos;

    @Autowired
    private JdbcTemplate jdbc;

    // ==================================================================
    @Nested
    @DisplayName("Criterio 1: el filtro por etiqueta muestra solo discusiones con esa etiqueta")
    class FiltroPorEtiqueta {

        @Test
        @DisplayName("devuelve las discusiones con la etiqueta y excluye las demas")
        void filtra_por_etiqueta() throws Exception {
            // Arrange
            String token = autenticarDesarrollador();
            UUID sobreRust = abrir(token, "Ownership en Rust explicado", tecnologia("rust"));
            UUID sobreGo = abrir(token, "Goroutines frente a hilos", tecnologia("go"));

            // Act + Assert
            mvc.perform(get(RUTA).param("tecnologia", "rust").param("limite", "50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido[*].id", hasItem(sobreRust.toString())))
                    .andExpect(jsonPath("$.contenido[*].id", not(hasItem(sobreGo.toString()))))
                    .andExpect(jsonPath("$.mensaje").value(nullValue()));
        }

        @Test
        @DisplayName("todas las discusiones devueltas llevan la etiqueta filtrada")
        void todas_llevan_la_etiqueta() throws Exception {
            // Arrange: una discusion con dos etiquetas tambien cuenta
            String token = autenticarDesarrollador();
            abrir(token, "Kotlin en el backend", tecnologia("kotlin"), tecnologia("java"));

            // Act + Assert
            mvc.perform(get(RUTA).param("tecnologia", "kotlin"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido[*].tecnologias[*].slug", hasItem("kotlin")))
                    .andExpect(jsonPath("$.contenido[*].tecnologias", everyItem(
                            org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.hasEntry("slug", "kotlin")))));
        }

        @Test
        @DisplayName("acepta el slug en mayusculas")
        void slug_sin_distinguir_mayusculas() throws Exception {
            // Arrange
            UUID discusion = abrir(autenticarDesarrollador(), "APIs con Spring Boot", tecnologia("spring-boot"));

            // Act + Assert
            mvc.perform(get(RUTA).param("tecnologia", "Spring-Boot").param("limite", "50"))
                    .andExpect(jsonPath("$.contenido[*].id", hasItem(discusion.toString())));
        }

        @Test
        @DisplayName("cada discusion trae autor, extracto, categoria y etiquetas con nombre")
        void forma_de_cada_elemento() throws Exception {
            // Arrange
            var dev = datos.crear("DESARROLLADOR", false);
            UUID discusion = abrir(autenticar(dev.correo()), "Docker en desarrollo local", tecnologia("docker"));

            // Act + Assert
            mvc.perform(get(RUTA).param("tecnologia", "docker").param("limite", "50"))
                    .andExpect(jsonPath("$.contenido[?(@.id == '%s')].autor.nombreUsuario"
                            .formatted(discusion)).value(hasItem(dev.nombreUsuario())))
                    .andExpect(jsonPath("$.contenido[?(@.id == '%s')].extracto"
                            .formatted(discusion)).value(hasItem(CUERPO)))
                    .andExpect(jsonPath("$.contenido[?(@.id == '%s')].categoria"
                            .formatted(discusion)).value(hasItem("DEBATE")))
                    .andExpect(jsonPath("$.contenido[?(@.id == '%s')].tecnologias[0].nombre"
                            .formatted(discusion)).value(hasItem("Docker")));
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Criterio 2: el listado es paginado y no repite elementos entre paginas")
    class Paginacion {

        @Test
        @DisplayName("recorre todas las paginas sin repetir ni saltar, de la mas reciente a la mas antigua")
        void no_repite_entre_paginas() throws Exception {
            // Arrange: cinco discusiones con la misma etiqueta
            String token = autenticarDesarrollador();
            short git = tecnologia("git");
            List<UUID> creadas = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                creadas.add(abrir(token, "Flujo de ramas numero " + i, git));
            }

            // Act: pagina a pagina, de dos en dos, hasta que no haya mas
            List<String> vistas = new ArrayList<>();
            String cursor = null;
            int paginas = 0;
            do {
                var peticion = get(RUTA).param("tecnologia", "git").param("limite", "2");
                if (cursor != null) {
                    peticion.param("cursor", cursor);
                }
                String json = mvc.perform(peticion)
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString();
                vistas.addAll(JsonPath.read(json, "$.contenido[*].id"));
                cursor = JsonPath.read(json, "$.cursorSiguiente");
                paginas++;
            } while (cursor != null && paginas < 50);

            // Assert: ningun elemento repetido...
            assertThat(vistas).doesNotHaveDuplicates();
            // ...ninguno saltado, y las mias en orden inverso de creacion
            List<String> esperadas = new ArrayList<>(creadas.stream().map(UUID::toString).toList());
            java.util.Collections.reverse(esperadas);
            assertThat(vistas.stream().filter(esperadas::contains).toList()).isEqualTo(esperadas);
            assertThat(paginas).isGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("la primera pagina avisa de que hay mas y entrega un cursor")
        void primera_pagina() throws Exception {
            // Arrange
            String token = autenticarDesarrollador();
            short redis = tecnologia("redis");
            abrir(token, "Redis como cache distribuida", redis);
            abrir(token, "Redis como cola de mensajes", redis);

            // Act + Assert
            mvc.perform(get(RUTA).param("tecnologia", "redis").param("limite", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido", hasSize(1)))
                    .andExpect(jsonPath("$.hayMas").value(true))
                    .andExpect(jsonPath("$.cursorSiguiente").isNotEmpty());
        }

        @Test
        @DisplayName("no muestra discusiones que no esten publicadas")
        void solo_publicadas() throws Exception {
            // Arrange: se archiva por SQL; archivar discusiones aun no tiene endpoint
            UUID discusion = abrir(autenticarDesarrollador(), "Discusion que se archivara", tecnologia("mongodb"));
            jdbc.update("UPDATE publicacion SET estado = 'ARCHIVADO' WHERE id = ?", discusion);

            // Act + Assert
            mvc.perform(get(RUTA).param("tecnologia", "mongodb").param("limite", "50"))
                    .andExpect(jsonPath("$.contenido[*].id", not(hasItem(discusion.toString()))));
        }

        @Test
        @DisplayName("no mezcla proyectos en el listado de discusiones")
        void no_mezcla_proyectos() throws Exception {
            // Arrange: un proyecto con la misma etiqueta
            String token = autenticarDesarrollador();
            short aws = tecnologia("aws");
            MvcResult proyecto = mvc.perform(post("/api/v1/proyectos")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"titulo": "Infraestructura en AWS", "descripcion": "%s", "tecnologias": [%d]}
                                    """.formatted(CUERPO, aws)))
                    .andExpect(status().isCreated())
                    .andReturn();
            String idProyecto = JsonPath.read(proyecto.getResponse().getContentAsString(), "$.id");

            // Act + Assert
            mvc.perform(get(RUTA).param("tecnologia", "aws").param("limite", "50"))
                    .andExpect(jsonPath("$.contenido[*].id", not(hasItem(idProyecto))));
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Criterio 3: si no hay discusiones para un filtro, se muestra un estado vacio")
    class EstadoVacio {

        @Test
        @DisplayName("una etiqueta sin discusiones responde 200, lista vacia y mensaje")
        void etiqueta_sin_discusiones() throws Exception {
            // Arrange: ninguna prueba etiqueta discusiones con Azure; se comprueba igual
            Integer conAzure = jdbc.queryForObject("""
                    SELECT count(*) FROM publicacion p
                      JOIN publicacion_tecnologia pt ON pt.publicacion_id = p.id
                      JOIN tecnologia t ON t.id = pt.tecnologia_id
                     WHERE p.tipo = 'DISCUSION' AND t.slug = 'azure'
                    """, Integer.class);
            assertThat(conAzure).isZero();

            // Act + Assert
            mvc.perform(get(RUTA).param("tecnologia", "azure"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido", hasSize(0)))
                    .andExpect(jsonPath("$.hayMas").value(false))
                    .andExpect(jsonPath("$.cursorSiguiente").value(nullValue()))
                    .andExpect(jsonPath("$.mensaje", containsString("Azure")))
                    .andExpect(jsonPath("$.mensaje", containsString("Abre la primera")));
        }

        @Test
        @DisplayName("una etiqueta que no existe responde vacio explicando que no esta en el catalogo")
        void etiqueta_inexistente() throws Exception {
            mvc.perform(get(RUTA).param("tecnologia", "cobol-2099"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido", hasSize(0)))
                    .andExpect(jsonPath("$.mensaje", containsString("no esta en el catalogo")));
        }
    }

    // ==================================================================

    private UUID abrir(String token, String titulo, short... etiquetas) throws Exception {
        StringBuilder ids = new StringBuilder();
        for (short e : etiquetas) {
            ids.append(ids.isEmpty() ? "" : ",").append(e);
        }
        MvcResult r = mvc.perform(post(RUTA)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo": "%s", "cuerpo": "%s", "tecnologias": [%s]}
                                """.formatted(titulo, CUERPO, ids)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(r.getResponse().getContentAsString(), "$.id"));
    }

    private short tecnologia(String slug) {
        Short id = jdbc.queryForObject("SELECT id FROM tecnologia WHERE slug = ?", Short.class, slug);
        return id == null ? 0 : id;
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
