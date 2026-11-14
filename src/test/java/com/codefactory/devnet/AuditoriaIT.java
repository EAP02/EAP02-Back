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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-20: registro de auditoria.
 *
 * <p>Complementa a {@code ConsultarAuditoriaTest} y a los tests de dominio del modulo
 * {@code administration}. Aqui se verifica lo que solo se ve con la base real: que cada
 * hecho sensible deja su fila, que la API la devuelve con quien, que, cuando y sobre que
 * objeto, y que solo un administrador puede leerla.</p>
 *
 * <p>Las filas de auditoria las escribe la aplicacion en una transaccion propia
 * ({@code REQUIRES_NEW}), asi que sobreviven al rollback de cada test y se acumulan entre
 * ellos. Por eso cada consulta se acota por el {@code actorId} del usuario que el test
 * acaba de crear.</p>
 *
 * <p><b>Requiere Docker.</b> Lo ejecuta Failsafe en {@code ./mvnw verify}.</p>
 */
@PruebaIntegracion
@DisplayName("HU-20 Registro de auditoria (integracion)")
class AuditoriaIT {

    private static final String RUTA = "/api/v1/auditoria";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SoporteDatosPrueba datos;

    @Autowired
    private JdbcTemplate jdbc;

    // ==================================================================
    @Nested
    @DisplayName("Criterio 1: cada inicio de sesion, exitoso o fallido, queda registrado")
    class InicioDeSesion {

        @Test
        @DisplayName("un inicio exitoso aparece con quien, que, cuando y sobre que objeto")
        void inicio_exitoso() throws Exception {
            // Arrange
            var admin = datos.crear("ADMIN", true);
            String token = autenticar(admin.correo());

            // Act + Assert
            mvc.perform(consulta(token).param("actorId", admin.id().toString())
                            .param("operacion", "INICIO_SESION"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.contenido[0].accion").value("INICIO_SESION"))
                    .andExpect(jsonPath("$.contenido[0].actor.id").value(admin.id().toString()))
                    .andExpect(jsonPath("$.contenido[0].actor.nombreUsuario").value(admin.nombreUsuario()))
                    .andExpect(jsonPath("$.contenido[0].objeto.tabla").value("usuario"))
                    .andExpect(jsonPath("$.contenido[0].objeto.registroId").value(admin.id().toString()))
                    .andExpect(jsonPath("$.contenido[0].fecha").isNotEmpty());
        }

        @Test
        @DisplayName("un inicio fallido tambien queda, con el correo intentado y sin actor")
        void inicio_fallido() throws Exception {
            // Arrange
            var admin = datos.crear("ADMIN", true);
            var victima = datos.crear("DESARROLLADOR", false);
            String token = autenticar(admin.correo());

            // Act: contrasena equivocada
            mvc.perform(post("/api/v1/auth/inicio-sesion")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"correo\": \"" + victima.correo() + "\", \"clave\": \"ClaveErronea123!\"}"))
                    .andExpect(status().isUnauthorized());

            // Assert: el mas reciente de su tipo es el que se acaba de provocar
            mvc.perform(consulta(token).param("operacion", "INICIO_SESION_FALLIDO"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido[0].accion").value("INICIO_SESION_FALLIDO"))
                    .andExpect(jsonPath("$.contenido[0].objeto.registroId").value(victima.correo()))
                    .andExpect(jsonPath("$.contenido[0].actor").doesNotExist());
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Criterio 2: los cambios de rol y las eliminaciones de contenido quedan registrados")
    class CambiosYEliminaciones {

        @Test
        @DisplayName("asignar un rol deja una fila en usuario_rol con quien lo asigno")
        void cambio_de_rol() throws Exception {
            // Arrange
            var admin = datos.crear("ADMIN", true);
            var dev = datos.crear("DESARROLLADOR", false);
            String token = autenticar(admin.correo());

            // Act: cambio por consola, como hoy (no hay endpoint para roles); lo captura el trigger V10
            jdbc.update("""
                    INSERT INTO usuario_rol (usuario_id, rol_id, asignado_por)
                    SELECT ?, id, ? FROM rol WHERE codigo = 'MODERADOR'
                    """, dev.id(), admin.id());

            // Assert
            mvc.perform(consulta(token).param("actorId", admin.id().toString())
                            .param("tabla", "usuario_rol"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.contenido[0].accion").value("INSERT"))
                    .andExpect(jsonPath("$.contenido[0].objeto.registroId", startsWith(dev.id() + ":")))
                    .andExpect(jsonPath("$.contenido[0].actor.id").value(admin.id().toString()))
                    .andExpect(jsonPath("$.contenido[0].datosDespues.asignado_por").value(admin.id().toString()));
        }

        @Test
        @DisplayName("quitar un rol tambien queda, con el estado anterior")
        void retiro_de_rol() throws Exception {
            // Arrange
            var admin = datos.crear("ADMIN", true);
            var dev = datos.crear("DESARROLLADOR", false);
            String token = autenticar(admin.correo());

            // Act
            jdbc.update("DELETE FROM usuario_rol WHERE usuario_id = ?", dev.id());

            // Assert: el DELETE no trae actor (la fila borrada tenia asignado_por nulo), se
            // busca por el objeto
            mvc.perform(consulta(token).param("tabla", "usuario_rol").param("operacion", "DELETE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido[0].objeto.registroId", startsWith(dev.id() + ":")))
                    .andExpect(jsonPath("$.contenido[0].datosAntes").isNotEmpty());
        }

        @Test
        @DisplayName("el autor que elimina su propio comentario deja registro")
        void eliminacion_por_el_autor() throws Exception {
            // Arrange
            var admin = datos.crear("ADMIN", true);
            var autor = datos.crear("DESARROLLADOR", false);
            String tokenAutor = autenticar(autor.correo());
            UUID comentario = comentar(tokenAutor, crearDiscusion(tokenAutor));

            // Act
            mvc.perform(delete("/api/v1/comentarios/" + comentario)
                            .header("Authorization", "Bearer " + tokenAutor))
                    .andExpect(status().isNoContent());

            // Assert
            mvc.perform(consulta(autenticar(admin.correo())).param("actorId", autor.id().toString())
                            .param("operacion", "DELETE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.contenido[0].objeto.tabla").value("comentario"))
                    .andExpect(jsonPath("$.contenido[0].objeto.registroId").value(comentario.toString()))
                    .andExpect(jsonPath("$.contenido[0].datosAntes").doesNotExist());
        }

        @Test
        @DisplayName("la intervencion de un administrador sobre un comentario ajeno deja registro y dice de quien era")
        void eliminacion_por_un_administrador() throws Exception {
            // Arrange
            var admin = datos.crear("ADMIN", true);
            var autor = datos.crear("DESARROLLADOR", false);
            String tokenAutor = autenticar(autor.correo());
            String tokenAdmin = autenticar(admin.correo());
            UUID comentario = comentar(tokenAutor, crearDiscusion(tokenAutor));

            // Act
            mvc.perform(delete("/api/v1/comentarios/" + comentario)
                            .header("Authorization", "Bearer " + tokenAdmin))
                    .andExpect(status().isNoContent());

            // Assert
            mvc.perform(consulta(tokenAdmin).param("actorId", admin.id().toString())
                            .param("operacion", "DELETE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.contenido[0].objeto.registroId").value(comentario.toString()))
                    .andExpect(jsonPath("$.contenido[0].datosAntes.autor_id").value(autor.id().toString()));
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Criterio 3: cada registro dice quien, que, cuando y sobre que objeto")
    class FormaDelRegistro {

        @Test
        @DisplayName("un acceso denegado trae actor, accion, fecha, objeto (la ruta) y la traza")
        void acceso_denegado_completo() throws Exception {
            // Arrange
            var admin = datos.crear("ADMIN", true);
            var intruso = datos.crear("DESARROLLADOR", false);
            mvc.perform(get(RUTA).header("Authorization", "Bearer " + autenticar(intruso.correo())))
                    .andExpect(status().isForbidden());

            // Act + Assert
            mvc.perform(consulta(autenticar(admin.correo())).param("actorId", intruso.id().toString())
                            .param("operacion", "ACCESO_DENEGADO"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido[0].actor.nombreUsuario").value(intruso.nombreUsuario()))
                    .andExpect(jsonPath("$.contenido[0].accion").value("ACCESO_DENEGADO"))
                    .andExpect(jsonPath("$.contenido[0].objeto.registroId").value("GET " + RUTA))
                    .andExpect(jsonPath("$.contenido[0].fecha").isNotEmpty())
                    .andExpect(jsonPath("$.contenido[0].traceId").isNotEmpty());
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Criterio 4: el registro no almacena contrasenas ni tokens")
    class SinSecretos {

        @Test
        @DisplayName("ni la tabla ni la respuesta contienen la contrasena ni el token")
        void no_hay_secretos() throws Exception {
            // Arrange: un inicio bueno, uno con una contrasena equivocada y una renovacion
            var admin = datos.crear("ADMIN", true);
            var dev = datos.crear("DESARROLLADOR", false);
            String tokenDev = autenticar(dev.correo());
            String tokenAdmin = autenticar(admin.correo());
            String intento = "ClaveErronea123!";
            mvc.perform(post("/api/v1/auth/inicio-sesion")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"correo\": \"" + dev.correo() + "\", \"clave\": \"" + intento + "\"}"))
                    .andExpect(status().isUnauthorized());

            // Act
            String respuesta = mvc.perform(consulta(tokenAdmin).param("tamano", "100"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            // Assert: la respuesta
            assertThat(respuesta)
                    .doesNotContain(SoporteDatosPrueba.CLAVE)
                    .doesNotContain(intento)
                    .doesNotContain(tokenDev)
                    .doesNotContain(tokenAdmin);

            // Assert: la tabla entera, en cualquier columna de texto
            for (String secreto : List.of(SoporteDatosPrueba.CLAVE, intento, tokenDev, tokenAdmin)) {
                Integer filas = jdbc.queryForObject("""
                        SELECT count(*) FROM auditoria
                         WHERE registro_id LIKE ? OR trace_id LIKE ?
                            OR datos_antes::text LIKE ? OR datos_despues::text LIKE ?
                        """, Integer.class, "%" + secreto + "%", "%" + secreto + "%",
                        "%" + secreto + "%", "%" + secreto + "%");
                assertThat(filas).as("filas de auditoria que contienen un secreto").isZero();
            }
        }

        @Test
        @DisplayName("un cambio en usuario solo guarda id, estado y mfa: nunca el hash ni el secreto")
        void usuario_sin_credenciales() throws Exception {
            // Arrange
            var admin = datos.crear("ADMIN", true);
            var dev = datos.crear("DESARROLLADOR", false);
            String token = autenticar(admin.correo());

            // Act
            jdbc.update("UPDATE usuario SET mfa_habilitado = true WHERE id = ?", dev.id());

            // Assert
            String cuerpo = mvc.perform(consulta(token).param("tabla", "usuario")
                            .param("operacion", "UPDATE"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(cuerpo).contains(dev.id().toString())
                    .doesNotContain("clave_hash").doesNotContain("mfa_secreto");
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Criterio 5: solo los administradores pueden consultar el registro")
    class SoloAdministradores {

        @Test
        @DisplayName("un desarrollador recibe 403 ACCESO_DENEGADO y el intento queda auditado")
        void desarrollador() throws Exception {
            // Arrange
            var dev = datos.crear("DESARROLLADOR", false);
            int antes = datos.contarEventosAuditoria("ACCESO_DENEGADO", dev.id());

            // Act + Assert
            mvc.perform(consulta(autenticar(dev.correo())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("ACCESO_DENEGADO"));
            assertThat(datos.contarEventosAuditoria("ACCESO_DENEGADO", dev.id())).isEqualTo(antes + 1);
        }

        @Test
        @DisplayName("un moderador con MFA tampoco puede: el permiso es solo del ADMIN")
        void moderador() throws Exception {
            var moderador = datos.crear("MODERADOR", true);

            mvc.perform(consulta(autenticar(moderador.correo())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("un visitante recibe 403")
        void visitante() throws Exception {
            var visitante = datos.crear("VISITANTE", false);

            mvc.perform(consulta(autenticar(visitante.correo())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("un administrador SIN MFA inscrito recibe 403: su token sale sin permisos")
        void administrador_sin_mfa() throws Exception {
            var admin = datos.crear("ADMIN", false);

            mvc.perform(consulta(autenticar(admin.correo())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("sin token recibe 401")
        void sin_token() throws Exception {
            mvc.perform(get(RUTA))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("AUTH_REQUERIDA"));
        }

        @Test
        @DisplayName("el registro es de solo lectura: no hay POST, PUT ni DELETE")
        void solo_lectura() throws Exception {
            var admin = datos.crear("ADMIN", true);
            String token = autenticar(admin.correo());

            mvc.perform(delete(RUTA).header("Authorization", "Bearer " + token))
                    .andExpect(status().isMethodNotAllowed());
            mvc.perform(post(RUTA).header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Filtros, rango de fechas y paginacion")
    class Consulta {

        @Test
        @DisplayName("pagina del mas reciente al mas antiguo y cuenta el total")
        void paginacion() throws Exception {
            // Arrange: tres inicios de sesion del mismo administrador
            var admin = datos.crear("ADMIN", true);
            autenticar(admin.correo());
            autenticar(admin.correo());
            String token = autenticar(admin.correo());
            MockHttpServletRequestBuilder pedido = consulta(token)
                    .param("actorId", admin.id().toString()).param("operacion", "INICIO_SESION")
                    .param("tamano", "2");

            // Act
            String primera = mvc.perform(pedido)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido", hasSize(2)))
                    .andExpect(jsonPath("$.total").value(3))
                    .andExpect(jsonPath("$.totalPaginas").value(2))
                    .andReturn().getResponse().getContentAsString();

            // Assert: la segunda pagina trae lo que falta y el orden es descendente
            mvc.perform(consulta(token).param("actorId", admin.id().toString())
                            .param("operacion", "INICIO_SESION").param("tamano", "2").param("pagina", "1"))
                    .andExpect(jsonPath("$.contenido", hasSize(1)));
            long primero = ((Number) JsonPath.read(primera, "$.contenido[0].id")).longValue();
            long segundo = ((Number) JsonPath.read(primera, "$.contenido[1].id")).longValue();
            assertThat(primero).isGreaterThan(segundo);
        }

        @Test
        @DisplayName("el rango incluye el dia de hoy (hora de Colombia)")
        void rango_con_hoy() throws Exception {
            var admin = datos.crear("ADMIN", true);
            String token = autenticar(admin.correo());
            String hoy = LocalDate.now(com.codefactory.devnet.administration.domain.RangoFechas.ZONA).toString();

            mvc.perform(consulta(token).param("actorId", admin.id().toString())
                            .param("desde", hoy).param("hasta", hoy))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(1));
        }

        @Test
        @DisplayName("sin actividad en el rango devuelve un estado vacio, no un error")
        void estado_vacio() throws Exception {
            var admin = datos.crear("ADMIN", true);
            String token = autenticar(admin.correo());

            mvc.perform(consulta(token).param("desde", "2099-01-01").param("hasta", "2099-01-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido", hasSize(0)))
                    .andExpect(jsonPath("$.total").value(0))
                    .andExpect(jsonPath("$.totalPaginas").value(0));
        }

        @Test
        @DisplayName("la fecha final anterior a la inicial responde 400 RANGO_FECHAS_INVALIDO")
        void rango_invertido() throws Exception {
            var admin = datos.crear("ADMIN", true);

            mvc.perform(consulta(autenticar(admin.correo()))
                            .param("desde", "2026-11-13").param("hasta", "2026-11-01"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("RANGO_FECHAS_INVALIDO"));
        }

        @Test
        @DisplayName("una operacion desconocida responde 400 y lista las validas")
        void operacion_invalida() throws Exception {
            var admin = datos.crear("ADMIN", true);

            mvc.perform(consulta(autenticar(admin.correo())).param("operacion", "BORRAR_TODO"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("AUDITORIA_OPERACION_INVALIDA"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("INICIO_SESION")));
        }

        @Test
        @DisplayName("un tamano de pagina fuera de 1..100 responde 400 PAGINACION_INVALIDA")
        void tamano_fuera_de_rango() throws Exception {
            var admin = datos.crear("ADMIN", true);
            String token = autenticar(admin.correo());

            mvc.perform(consulta(token).param("tamano", "101"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("PAGINACION_INVALIDA"));
            mvc.perform(consulta(token).param("tamano", "0"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("PAGINACION_INVALIDA"));
        }

        @Test
        @DisplayName("un id o una fecha con formato incorrecto responde 400 PARAMETRO_INVALIDO")
        void formato_incorrecto() throws Exception {
            var admin = datos.crear("ADMIN", true);
            String token = autenticar(admin.correo());

            mvc.perform(consulta(token).param("actorId", "no-es-un-uuid"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("PARAMETRO_INVALIDO"));
            mvc.perform(consulta(token).param("desde", "ayer"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("PARAMETRO_INVALIDO"));
        }
    }

    // ------------------------------------------------------------------
    // Ayudas
    // ------------------------------------------------------------------

    private static MockHttpServletRequestBuilder consulta(String token) {
        return get(RUTA).header("Authorization", "Bearer " + token);
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

    private UUID crearDiscusion(String token) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/discusiones")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo": "Spring Boot 4: vale la pena migrar ya?",
                                 "cuerpo": "Llevo dos semanas probando la migracion y quiero comparar experiencias con otros.",
                                 "tecnologias": %s}
                                """.formatted(datos.tecnologiasAprobadas(2))))
                .andExpect(status().isCreated())
                .andReturn();
        return idDe(r);
    }

    private UUID comentar(String token, UUID publicacion) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/publicaciones/" + publicacion + "/comentarios")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contenido\": \"Un comentario de prueba\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return idDe(r);
    }

    private static UUID idDe(MvcResult r) throws Exception {
        return UUID.fromString(JsonPath.read(r.getResponse().getContentAsString(), "$.id"));
    }
}
