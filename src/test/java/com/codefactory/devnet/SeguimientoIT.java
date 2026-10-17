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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-15: seguir a otros miembros, verificado extremo a extremo.
 *
 * <p>Lo que solo se ve con la base real: la fila en {@code seguimiento}, que la clave
 * primaria compuesta impide duplicar, y que el permiso {@code usuario:seguir} (V7) llega
 * en el token a DESARROLLADOR y no a VISITANTE.</p>
 *
 * <p><b>Requiere Docker.</b> Lo ejecuta Failsafe en {@code ./mvnw verify}.</p>
 */
@PruebaIntegracion
@DisplayName("HU-15 Seguir a otros miembros (integracion)")
class SeguimientoIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SoporteDatosPrueba datos;

    @Autowired
    private JdbcTemplate jdbc;

    // ==================================================================
    @Nested
    @DisplayName("Criterio 1: al seguir a otro miembro, aparece en la lista de seguidos")
    class AlSeguir {

        @Test
        @DisplayName("204, queda una fila a nombre del usuario del token y aparece en sus seguidos")
        void sigue_y_aparece_en_la_lista() throws Exception {
            // Arrange
            var ana = datos.crear("DESARROLLADOR", false);
            var luis = datos.crear("DESARROLLADOR", false);

            // Act
            mvc.perform(seguir(luis.id(), autenticar(ana.correo())))
                    .andExpect(status().isNoContent());

            // Assert: en la base
            assertThat(filas(ana.id(), luis.id())).isEqualTo(1);

            // ...y en la lectura publica (sin token)
            mvc.perform(get("/api/v1/perfiles/" + ana.id() + "/seguidos"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].usuarioId").value(luis.id().toString()))
                    .andExpect(jsonPath("$[0].nombreUsuario").value(luis.nombreUsuario()));
        }

        @Test
        @DisplayName("el seguido ve a su seguidor en su lista de seguidores")
        void aparece_en_los_seguidores_del_otro() throws Exception {
            // Arrange
            var ana = datos.crear("DESARROLLADOR", false);
            var luis = datos.crear("DESARROLLADOR", false);
            mvc.perform(seguir(luis.id(), autenticar(ana.correo()))).andExpect(status().isNoContent());

            // Act + Assert
            mvc.perform(get("/api/v1/perfiles/" + luis.id() + "/seguidores"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].usuarioId", hasItem(ana.id().toString())));
        }

        @Test
        @DisplayName("seguir no es reciproco: Luis no pasa a seguir a Ana")
        void no_es_reciproco() throws Exception {
            // Arrange
            var ana = datos.crear("DESARROLLADOR", false);
            var luis = datos.crear("DESARROLLADOR", false);

            // Act
            mvc.perform(seguir(luis.id(), autenticar(ana.correo()))).andExpect(status().isNoContent());

            // Assert
            mvc.perform(get("/api/v1/perfiles/" + luis.id() + "/seguidos"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("seguir dos veces al mismo miembro deja una sola fila")
        void no_duplica() throws Exception {
            // Arrange
            var ana = datos.crear("DESARROLLADOR", false);
            var luis = datos.crear("DESARROLLADOR", false);
            String tokenAna = autenticar(ana.correo());

            // Act
            mvc.perform(seguir(luis.id(), tokenAna)).andExpect(status().isNoContent());
            mvc.perform(seguir(luis.id(), tokenAna)).andExpect(status().isNoContent());

            // Assert
            assertThat(filas(ana.id(), luis.id())).isEqualTo(1);
            mvc.perform(get("/api/v1/perfiles/" + ana.id() + "/seguidos"))
                    .andExpect(jsonPath("$", hasSize(1)));
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Criterio 2: al dejar de seguir, desaparece de la lista de seguidos")
    class AlDejarDeSeguir {

        @Test
        @DisplayName("204, la fila se borra y ya no aparece en sus seguidos")
        void deja_de_seguir() throws Exception {
            // Arrange
            var ana = datos.crear("DESARROLLADOR", false);
            var luis = datos.crear("DESARROLLADOR", false);
            String tokenAna = autenticar(ana.correo());
            mvc.perform(seguir(luis.id(), tokenAna)).andExpect(status().isNoContent());

            // Act
            mvc.perform(dejarDeSeguir(luis.id(), tokenAna)).andExpect(status().isNoContent());

            // Assert
            assertThat(filas(ana.id(), luis.id())).isZero();
            mvc.perform(get("/api/v1/perfiles/" + ana.id() + "/seguidos"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].usuarioId", not(hasItem(luis.id().toString()))));
        }

        @Test
        @DisplayName("dejar de seguir a uno no toca a los demas que sigue")
        void no_toca_a_los_demas() throws Exception {
            // Arrange
            var ana = datos.crear("DESARROLLADOR", false);
            var luis = datos.crear("DESARROLLADOR", false);
            var eva = datos.crear("DESARROLLADOR", false);
            String tokenAna = autenticar(ana.correo());
            mvc.perform(seguir(luis.id(), tokenAna)).andExpect(status().isNoContent());
            mvc.perform(seguir(eva.id(), tokenAna)).andExpect(status().isNoContent());

            // Act
            mvc.perform(dejarDeSeguir(luis.id(), tokenAna)).andExpect(status().isNoContent());

            // Assert: queda Eva
            mvc.perform(get("/api/v1/perfiles/" + ana.id() + "/seguidos"))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].usuarioId").value(eva.id().toString()));
        }

        @Test
        @DisplayName("dejar de seguir a quien no se seguia no es error")
        void es_idempotente() throws Exception {
            // Arrange
            var ana = datos.crear("DESARROLLADOR", false);
            var luis = datos.crear("DESARROLLADOR", false);

            // Act + Assert
            mvc.perform(dejarDeSeguir(luis.id(), autenticar(ana.correo())))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("seguir, dejar de seguir y volver a seguir deja una sola fila")
        void seguir_dejar_seguir() throws Exception {
            // Arrange
            var ana = datos.crear("DESARROLLADOR", false);
            var luis = datos.crear("DESARROLLADOR", false);
            String tokenAna = autenticar(ana.correo());

            // Act
            mvc.perform(seguir(luis.id(), tokenAna)).andExpect(status().isNoContent());
            mvc.perform(dejarDeSeguir(luis.id(), tokenAna)).andExpect(status().isNoContent());
            mvc.perform(seguir(luis.id(), tokenAna)).andExpect(status().isNoContent());

            // Assert
            assertThat(filas(ana.id(), luis.id())).isEqualTo(1);
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Criterio 3: un usuario no puede seguirse a si mismo")
    class SeguirseASiMismo {

        @Test
        @DisplayName("responde 422 SEGUIMIENTO_PROPIO y no crea ninguna fila")
        void no_puede_seguirse() throws Exception {
            // Arrange
            var ana = datos.crear("DESARROLLADOR", false);

            // Act + Assert
            mvc.perform(seguir(ana.id(), autenticar(ana.correo())))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.errorCode").value("SEGUIMIENTO_PROPIO"));
            assertThat(filas(ana.id(), ana.id())).isZero();
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
            var luis = datos.crear("DESARROLLADOR", false);

            // Act + Assert
            mvc.perform(put("/api/v1/perfiles/" + luis.id() + "/seguimiento"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("un visitante no puede seguir: su rol no tiene usuario:seguir")
        void un_visitante_no_puede() throws Exception {
            // Arrange
            var visitante = datos.crear("VISITANTE", false);
            var luis = datos.crear("DESARROLLADOR", false);

            // Act + Assert
            mvc.perform(seguir(luis.id(), autenticar(visitante.correo())))
                    .andExpect(status().isForbidden());
            assertThat(filas(visitante.id(), luis.id())).isZero();
        }

        @Test
        @DisplayName("V7 concede usuario:seguir exactamente a DESARROLLADOR y MODERADOR")
        void el_permiso_se_reparte_como_dice_v7() {
            // Act
            java.util.List<String> roles = jdbc.queryForList("""
                    SELECT r.codigo FROM rol_permiso rp
                      JOIN rol r ON r.id = rp.rol_id
                      JOIN permiso p ON p.id = rp.permiso_id
                     WHERE p.codigo = 'usuario:seguir'
                     ORDER BY r.codigo
                    """, String.class);

            // Assert: ni ADMIN ni VISITANTE
            assertThat(roles).containsExactly("DESARROLLADOR", "MODERADOR");
        }

        @Test
        @DisplayName("seguir a un miembro que no existe responde 404")
        void miembro_inexistente() throws Exception {
            // Arrange
            var ana = datos.crear("DESARROLLADOR", false);

            // Act + Assert
            mvc.perform(seguir(UUID.randomUUID(), autenticar(ana.correo())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("listar los seguidos de un miembro que no existe responde 404")
        void listar_de_inexistente() throws Exception {
            mvc.perform(get("/api/v1/perfiles/" + UUID.randomUUID() + "/seguidos"))
                    .andExpect(status().isNotFound());
        }
    }

    // ------------------------------------------------------------------
    // Ayudas
    // ------------------------------------------------------------------

    private static org.springframework.test.web.servlet.RequestBuilder seguir(UUID seguido, String token) {
        return put("/api/v1/perfiles/" + seguido + "/seguimiento")
                .header("Authorization", "Bearer " + token);
    }

    private static org.springframework.test.web.servlet.RequestBuilder dejarDeSeguir(UUID seguido, String token) {
        return delete("/api/v1/perfiles/" + seguido + "/seguimiento")
                .header("Authorization", "Bearer " + token);
    }

    private int filas(UUID seguidor, UUID seguido) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM seguimiento WHERE seguidor_id = ? AND seguido_id = ?",
                Integer.class, seguidor, seguido);
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
