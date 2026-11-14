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

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-19: reporte de actividad y crecimiento.
 *
 * <p>Complementa a {@code ConsultarReporteActividadTest} y a los tests de dominio. Aqui se
 * verifica lo que solo se ve con la base real: las cifras salen de las tablas, el calculo bajo
 * demanda ({@code sp_consolidar_metricas_pendientes}) hace lo que dice, y "usuarios activos" cuenta
 * usuarios distintos.</p>
 *
 * <p>Los datos se siembran en <b>2019</b>, un periodo que ninguna otra prueba toca: los inicios
 * de sesion de otros tests quedan auditados en la fecha de hoy ({@code REQUIRES_NEW} sobrevive al
 * rollback) y contaminarian cualquier rango reciente. El mismo criterio que
 * {@code BaseDatosSprint3IT} usa con 2020.</p>
 *
 * <p><b>Requiere Docker.</b> Lo ejecuta Failsafe en {@code ./mvnw verify}.</p>
 */
@PruebaIntegracion
@DisplayName("HU-19 Reporte de actividad y crecimiento (integracion)")
class ReporteActividadIT {

    private static final String RUTA = "/api/v1/reportes/actividad";
    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    // Dos semanas completas, de lunes a domingo
    private static final String DESDE = "2019-06-03";
    private static final String HASTA = "2019-06-16";

    private static final String TITULO = "Publicacion sembrada para el reporte";
    private static final String CUERPO = "Contenido de prueba con mas de veinte caracteres para cumplir el CHECK.";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SoporteDatosPrueba datos;

    @Autowired
    private JdbcTemplate jdbc;

    // ==================================================================
    @Nested
    @DisplayName("Criterio 1: con un rango valido muestra usuarios activos y lo publicado")
    class RangoValido {

        @Test
        @DisplayName("cuenta usuarios activos (distintos), proyectos, discusiones y comentarios del rango")
        void cifras_del_rango() throws Exception {
            // Arrange
            sembrarDosSemanas();
            String token = tokenAdmin();

            // Act + Assert
            mvc.perform(reporte(token, DESDE, HASTA, null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sinActividad").value(false))
                    .andExpect(jsonPath("$.proyectos").value(1))
                    .andExpect(jsonPath("$.discusiones").value(1))
                    .andExpect(jsonPath("$.comentarios").value(1))
                    // ana publico el 4 y el 6, beto comento el 5: son DOS personas, aunque
                    // sumando los activos de cada dia saldrian tres
                    .andExpect(jsonPath("$.usuariosActivos").value(2));
        }

        @Test
        @DisplayName("lo que cae fuera del rango no se cuenta")
        void excluye_lo_de_fuera() throws Exception {
            // Arrange: todo lo sembrado esta en la semana del 3 al 9; se pide la del 10 al 16
            sembrarDosSemanas();

            // Act + Assert: solo cami se registro esa semana
            mvc.perform(reporte(tokenAdmin(), "2019-06-10", HASTA, null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.proyectos").value(0))
                    .andExpect(jsonPath("$.discusiones").value(0))
                    .andExpect(jsonPath("$.comentarios").value(0))
                    .andExpect(jsonPath("$.usuariosActivos").value(0))
                    .andExpect(jsonPath("$.nuevosUsuarios").value(1));
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Criterio 2: muestra el crecimiento de nuevos registros por semana o por mes")
    class Crecimiento {

        @Test
        @DisplayName("por semana: nuevos de cada semana (lunes) y acumulado")
        void por_semana() throws Exception {
            // Arrange
            sembrarDosSemanas();

            // Act + Assert: ana y beto el 4 y 5 (semana del 3); cami el 12 (semana del 10)
            mvc.perform(reporte(tokenAdmin(), DESDE, HASTA, "SEMANA"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.agrupacion").value("SEMANA"))
                    .andExpect(jsonPath("$.nuevosUsuarios").value(3))
                    .andExpect(jsonPath("$.crecimiento", hasSize(2)))
                    .andExpect(jsonPath("$.crecimiento[0].periodo").value("2019-06-03"))
                    .andExpect(jsonPath("$.crecimiento[0].nuevos").value(2))
                    .andExpect(jsonPath("$.crecimiento[0].acumulado").value(2))
                    .andExpect(jsonPath("$.crecimiento[1].periodo").value("2019-06-10"))
                    .andExpect(jsonPath("$.crecimiento[1].nuevos").value(1))
                    .andExpect(jsonPath("$.crecimiento[1].acumulado").value(3));
        }

        @Test
        @DisplayName("sin indicar agrupacion, agrupa por semana")
        void por_defecto_semana() throws Exception {
            sembrarDosSemanas();

            mvc.perform(reporte(tokenAdmin(), DESDE, HASTA, null))
                    .andExpect(jsonPath("$.agrupacion").value("SEMANA"));
        }

        @Test
        @DisplayName("por mes: el rango cruza de junio a julio y cada mes suma lo suyo")
        void por_mes() throws Exception {
            // Arrange: uno el 25 de junio y otro el 2 de julio
            usuarioRegistradoEl(LocalDate.of(2019, 6, 25));
            usuarioRegistradoEl(LocalDate.of(2019, 7, 2));

            // Act + Assert
            mvc.perform(reporte(tokenAdmin(), "2019-06-24", "2019-07-07", "MES"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.agrupacion").value("MES"))
                    .andExpect(jsonPath("$.crecimiento", hasSize(2)))
                    .andExpect(jsonPath("$.crecimiento[0].periodo").value("2019-06-01"))
                    .andExpect(jsonPath("$.crecimiento[0].nuevos").value(1))
                    .andExpect(jsonPath("$.crecimiento[1].periodo").value("2019-07-01"))
                    .andExpect(jsonPath("$.crecimiento[1].nuevos").value(1))
                    .andExpect(jsonPath("$.crecimiento[1].acumulado").value(2));
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Criterio 3: un rango invalido se rechaza")
    class RangoInvalido {

        @Test
        @DisplayName("la fecha final anterior a la inicial responde 400 RANGO_FECHAS_INVALIDO")
        void final_anterior() throws Exception {
            mvc.perform(reporte(tokenAdmin(), "2019-06-16", "2019-06-03", null))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("RANGO_FECHAS_INVALIDO"));
        }

        @Test
        @DisplayName("sin fechas responde 400 RANGO_FECHAS_REQUERIDO")
        void sin_fechas() throws Exception {
            mvc.perform(get(RUTA).header("Authorization", "Bearer " + tokenAdmin()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("RANGO_FECHAS_REQUERIDO"));
        }

        @Test
        @DisplayName("un rango de mas de 92 dias responde 400 RANGO_FECHAS_DEMASIADO_AMPLIO")
        void demasiado_amplio() throws Exception {
            mvc.perform(reporte(tokenAdmin(), "2019-01-01", "2019-06-30", null))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("RANGO_FECHAS_DEMASIADO_AMPLIO"));
        }

        @Test
        @DisplayName("una agrupacion desconocida responde 400 AGRUPACION_INVALIDA")
        void agrupacion_invalida() throws Exception {
            mvc.perform(reporte(tokenAdmin(), DESDE, HASTA, "ANIO"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("AGRUPACION_INVALIDA"));
        }

        @Test
        @DisplayName("una fecha con formato incorrecto responde 400 PARAMETRO_INVALIDO")
        void formato_incorrecto() throws Exception {
            mvc.perform(reporte(tokenAdmin(), "ayer", HASTA, null))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("PARAMETRO_INVALIDO"));
        }

        @Test
        @DisplayName("un rango rechazado no calcula nada")
        void no_calcula() throws Exception {
            // Act
            mvc.perform(reporte(tokenAdmin(), "2019-06-16", "2019-06-03", null))
                    .andExpect(status().isBadRequest());

            // Assert
            assertThat(filasDeMetricas()).isZero();
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Criterio 4: solo los administradores acceden al reporte")
    class SoloAdministradores {

        @Test
        @DisplayName("un desarrollador recibe 403 y el intento queda auditado")
        void desarrollador() throws Exception {
            var dev = datos.crear("DESARROLLADOR", false);
            int antes = datos.contarEventosAuditoria("ACCESO_DENEGADO", dev.id());

            mvc.perform(reporte(autenticar(dev.correo()), DESDE, HASTA, null))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("ACCESO_DENEGADO"));
            assertThat(datos.contarEventosAuditoria("ACCESO_DENEGADO", dev.id())).isEqualTo(antes + 1);
        }

        @Test
        @DisplayName("un moderador con MFA tampoco: el permiso es solo del ADMIN")
        void moderador() throws Exception {
            var moderador = datos.crear("MODERADOR", true);

            mvc.perform(reporte(autenticar(moderador.correo()), DESDE, HASTA, null))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("un visitante recibe 403")
        void visitante() throws Exception {
            var visitante = datos.crear("VISITANTE", false);

            mvc.perform(reporte(autenticar(visitante.correo()), DESDE, HASTA, null))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("un administrador SIN MFA inscrito recibe 403: su token sale sin permisos")
        void administrador_sin_mfa() throws Exception {
            var admin = datos.crear("ADMIN", false);

            mvc.perform(reporte(autenticar(admin.correo()), DESDE, HASTA, null))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("sin token recibe 401")
        void sin_token() throws Exception {
            mvc.perform(get(RUTA).param("desde", DESDE).param("hasta", HASTA))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("AUTH_REQUERIDA"));
        }

        @Test
        @DisplayName("V13 concede metricas:consultar exactamente a ADMIN")
        void el_permiso_es_solo_del_admin() {
            List<String> roles = jdbc.queryForList("""
                    SELECT r.codigo FROM rol_permiso rp
                      JOIN rol r ON r.id = rp.rol_id
                      JOIN permiso p ON p.id = rp.permiso_id
                     WHERE p.codigo = 'metricas:consultar'
                    """, String.class);

            assertThat(roles).containsExactly("ADMIN");
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Criterio 5: si no hay actividad en el rango se muestra un estado vacio")
    class EstadoVacio {

        @Test
        @DisplayName("un rango sin actividad responde 200 con totales en 0 y sin serie")
        void sin_actividad() throws Exception {
            mvc.perform(reporte(tokenAdmin(), "2018-01-01", "2018-01-14", null))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.sinActividad").value(true))
                    .andExpect(jsonPath("$.usuariosActivos").value(0))
                    .andExpect(jsonPath("$.proyectos").value(0))
                    .andExpect(jsonPath("$.discusiones").value(0))
                    .andExpect(jsonPath("$.comentarios").value(0))
                    .andExpect(jsonPath("$.nuevosUsuarios").value(0))
                    .andExpect(jsonPath("$.crecimiento", hasSize(0)));
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Consolidacion bajo demanda (V13)")
    class ConsolidacionBajoDemanda {

        @Test
        @DisplayName("la primera consulta calcula los 14 dias y la segunda no recalcula los ya cerrados")
        void calcula_una_sola_vez() throws Exception {
            // Arrange
            sembrarDosSemanas();
            String token = tokenAdmin();

            // Act
            mvc.perform(reporte(token, DESDE, HASTA, null)).andExpect(status().isOk());
            int tras_primera = filasDeMetricas();
            Timestamp calculado = ultimoCalculo();
            mvc.perform(reporte(token, DESDE, HASTA, null)).andExpect(status().isOk());

            // Assert
            assertThat(tras_primera).isEqualTo(14);
            assertThat(ultimoCalculo()).as("ningun dia cerrado se recalcula").isEqualTo(calculado);
        }

        @Test
        @DisplayName("un dia calculado antes de que terminara se recalcula y recoge lo que llego despues")
        void recalcula_lo_que_quedo_a_medias() throws Exception {
            // Arrange: se consolida, y se finge que el 4 de junio se calculo a las 01:00 de ese dia
            var ana = sembrarDosSemanas();
            String token = tokenAdmin();
            mvc.perform(reporte(token, DESDE, HASTA, null)).andExpect(jsonPath("$.comentarios").value(1));
            jdbc.update("""
                    UPDATE metricas_diarias
                       SET calculado_en = CAST(fecha AS timestamp) AT TIME ZONE 'America/Bogota' + interval '1 hour'
                     WHERE fecha = '2019-06-04'
                    """);
            UUID publicacion = jdbc.queryForObject(
                    "SELECT id FROM publicacion WHERE autor_id = ? AND tipo = 'PROYECTO'", UUID.class, ana);
            jdbc.update("INSERT INTO comentario (publicacion_id, autor_id, contenido, creado_en) VALUES (?, ?, 'Tarde', ?)",
                    publicacion, ana, enBogota(LocalDate.of(2019, 6, 4), 23, 30));

            // Act
            mvc.perform(reporte(token, DESDE, HASTA, null))

            // Assert: el comentario que llego despues del primer calculo ya cuenta
                    .andExpect(jsonPath("$.comentarios").value(2));
        }

        @Test
        @DisplayName("el procedimiento no consolida dias futuros ni falla con un rango en el futuro")
        void no_consolida_el_futuro() {
            // Act
            jdbc.update("CALL sp_consolidar_metricas_pendientes(CAST(? AS date), CAST(? AS date))",
                    LocalDate.of(2099, 1, 1), LocalDate.of(2099, 1, 31));

            // Assert
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM metricas_diarias WHERE fecha >= '2099-01-01'", Integer.class)).isZero();
        }

        @Test
        @DisplayName("el procedimiento rechaza un rango invertido")
        void rechaza_rango_invertido() {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                            "CALL sp_consolidar_metricas_pendientes(CAST(? AS date), CAST(? AS date))",
                            LocalDate.of(2019, 6, 16), LocalDate.of(2019, 6, 3)))
                    .hasMessageContaining("Rango invertido");
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("fn_usuarios_activos")
    class UsuariosActivos {

        @Test
        @DisplayName("coincide, dia por dia, con usuarios_activos que consolida V11 (la definicion no se separa)")
        void misma_definicion_que_v11() {
            // Arrange
            sembrarDosSemanas();
            jdbc.update("CALL sp_consolidar_metricas_pendientes(CAST(? AS date), CAST(? AS date))",
                    LocalDate.parse(DESDE), LocalDate.parse(HASTA));

            // Act + Assert
            for (LocalDate dia = LocalDate.parse(DESDE); !dia.isAfter(LocalDate.parse(HASTA)); dia = dia.plusDays(1)) {
                Integer consolidado = jdbc.queryForObject(
                        "SELECT usuarios_activos FROM metricas_diarias WHERE fecha = ?", Integer.class, dia);
                Integer enVivo = jdbc.queryForObject(
                        "SELECT fn_usuarios_activos(CAST(? AS date), CAST(? AS date))", Integer.class, dia, dia);
                assertThat(enVivo).as("usuarios activos el " + dia).isEqualTo(consolidado);
            }
        }

        @Test
        @DisplayName("un usuario activo varios dias del rango cuenta una sola vez")
        void cuenta_distintos() {
            // Arrange: ana publica el 4 y el 6 de junio
            sembrarDosSemanas();

            // Act
            Integer enElRango = jdbc.queryForObject(
                    "SELECT fn_usuarios_activos(CAST(? AS date), CAST(? AS date))", Integer.class,
                    LocalDate.parse(DESDE), LocalDate.parse(HASTA));

            // Assert
            assertThat(enElRango).isEqualTo(2);
        }
    }

    // ------------------------------------------------------------------
    // Siembra
    // ------------------------------------------------------------------

    /**
     * Ana se registra el 4 de junio y publica un proyecto ese dia y una discusion el 6; Beto se
     * registra el 5 y comenta el proyecto ese dia; Cami se registra el 12 y no hace nada mas.
     *
     * @return el id de Ana
     */
    private UUID sembrarDosSemanas() {
        UUID ana = usuarioRegistradoEl(LocalDate.of(2019, 6, 4));
        UUID beto = usuarioRegistradoEl(LocalDate.of(2019, 6, 5));
        usuarioRegistradoEl(LocalDate.of(2019, 6, 12));

        UUID proyecto = publicacion(ana, "PROYECTO", enBogota(LocalDate.of(2019, 6, 4), 11, 0));
        publicacion(ana, "DISCUSION", enBogota(LocalDate.of(2019, 6, 6), 9, 0));
        jdbc.update("INSERT INTO comentario (publicacion_id, autor_id, contenido, creado_en) VALUES (?, ?, 'Hola', ?)",
                proyecto, beto, enBogota(LocalDate.of(2019, 6, 5), 15, 0));
        return ana;
    }

    private UUID usuarioRegistradoEl(LocalDate dia) {
        var usuario = datos.crear("DESARROLLADOR", false);
        jdbc.update("UPDATE usuario SET creado_en = ? WHERE id = ?", enBogota(dia, 10, 0), usuario.id());
        return usuario.id();
    }

    private UUID publicacion(UUID autor, String tipo, Timestamp momento) {
        return jdbc.queryForObject("""
                INSERT INTO publicacion (autor_id, tipo, titulo, contenido, estado, publicado_en, creado_en)
                VALUES (?, ?, ?, ?, 'PUBLICADO', ?, ?)
                RETURNING id
                """, UUID.class, autor, tipo, TITULO, CUERPO, momento, momento);
    }

    private int filasDeMetricas() {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM metricas_diarias WHERE fecha BETWEEN ? AND ?",
                Integer.class, LocalDate.parse(DESDE), LocalDate.parse(HASTA));
        return n == null ? 0 : n;
    }

    private Timestamp ultimoCalculo() {
        return jdbc.queryForObject("SELECT max(calculado_en) FROM metricas_diarias WHERE fecha BETWEEN ? AND ?",
                Timestamp.class, LocalDate.parse(DESDE), LocalDate.parse(HASTA));
    }

    private static Timestamp enBogota(LocalDate dia, int hora, int minuto) {
        return Timestamp.from(dia.atTime(hora, minuto).atZone(BOGOTA).toInstant());
    }

    // ------------------------------------------------------------------
    // Peticiones
    // ------------------------------------------------------------------

    private static MockHttpServletRequestBuilder reporte(String token, String desde, String hasta, String agrupacion) {
        MockHttpServletRequestBuilder pedido = get(RUTA)
                .header("Authorization", "Bearer " + token)
                .param("desde", desde).param("hasta", hasta);
        return agrupacion == null ? pedido : pedido.param("agrupacion", agrupacion);
    }

    private String tokenAdmin() throws Exception {
        return autenticar(datos.crear("ADMIN", true).correo());
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
