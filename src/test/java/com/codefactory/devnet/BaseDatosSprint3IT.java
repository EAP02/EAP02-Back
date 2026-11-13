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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Entregable de Bases de Datos del sprint 3 (§3.7): triggers y procedimiento
 * almacenado de V9, V10 y V11, contra PostgreSQL real.
 *
 * <p>Cada grupo prueba un objeto de la base y la decision que lo justifica
 * (docs/bd/02-modelo-logico.md, "Refinamiento del sprint 3"). Donde la historia de
 * usuario pasa por la API (comentar, HU-13), se prueba por la API; donde el cambio
 * solo puede llegar por consola (roles, suspensiones), por SQL directo, que es
 * exactamente el caso que el trigger existe para cubrir.</p>
 *
 * <p>Todas las pruebas corren en la transaccion de {@code @PruebaIntegracion} y se
 * revierten: los triggers y el procedimiento actuan dentro de ella igual que en una
 * transaccion confirmada.</p>
 */
@PruebaIntegracion
@DisplayName("BD sprint 3: triggers y procedimiento (integracion)")
class BaseDatosSprint3IT {

    private static final String TITULO = "Triggers en PostgreSQL: cuando si usarlos";
    private static final String CUERPO =
            "Contadores, auditoria e integridad: casos donde la base es la fuente de verdad.";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SoporteDatosPrueba datos;

    @Autowired
    private JdbcTemplate jdbc;

    // ==================================================================
    // V9 · contador de comentarios (HU-13)
    // ==================================================================

    @Nested
    @DisplayName("V9 tg_comentario_contador: publicacion.contador_comentarios")
    class ContadorComentarios {

        @Test
        @DisplayName("comentar por la API suma uno y eliminar por la API resta uno")
        void el_contador_sigue_a_la_api() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            String token = autenticar(autor.correo());
            UUID discusion = crearDiscusion(token);

            // Act: dos comentarios
            UUID primero = comentar(discusion, token, "Primer comentario");
            comentar(discusion, token, "Segundo comentario");

            // Assert
            assertThat(contadorDe(discusion)).isEqualTo(2);

            // Act: se elimina uno (borrado logico, estado ELIMINADO)
            mvc.perform(delete("/api/v1/comentarios/" + primero)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNoContent());

            // Assert
            assertThat(contadorDe(discusion)).isEqualTo(1);
        }

        @Test
        @DisplayName("ocultar y restaurar por SQL tambien lo mantiene; editar el texto no lo cambia")
        void el_contador_no_depende_de_la_api() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            String token = autenticar(autor.correo());
            UUID discusion = crearDiscusion(token);
            UUID comentario = comentar(discusion, token, "Un comentario");

            // Act + Assert: un moderador lo oculta por consola
            jdbc.update("UPDATE comentario SET estado = 'OCULTO' WHERE id = ?", comentario);
            assertThat(contadorDe(discusion)).isZero();

            // ... lo restaura
            jdbc.update("UPDATE comentario SET estado = 'PUBLICADO' WHERE id = ?", comentario);
            assertThat(contadorDe(discusion)).isEqualTo(1);

            // ... y editar el texto no es publicar otro comentario
            jdbc.update("UPDATE comentario SET contenido = 'Texto editado' WHERE id = ?", comentario);
            assertThat(contadorDe(discusion)).isEqualTo(1);

            // ... y un borrado fisico tambien resta
            jdbc.update("DELETE FROM comentario WHERE id = ?", comentario);
            assertThat(contadorDe(discusion)).isZero();
        }

        @Test
        @DisplayName("un cambio de contador no mueve actualizado_en: contar no es editar")
        void contar_no_es_editar() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            String token = autenticar(autor.correo());
            UUID discusion = crearDiscusion(token);
            // now() es la hora de inicio de la transaccion de la prueba: sin llevar la
            // marca al pasado no se distinguiria "no se movio" de "se movio a now()".
            // El trigger de V9 la sobrescribiria, asi que se fija con los triggers en
            // pausa solo para esta sentencia (session_replication_role, solo en pruebas).
            jdbc.execute("SET LOCAL session_replication_role = replica");
            jdbc.update("UPDATE publicacion SET actualizado_en = '2020-01-01T00:00:00Z' WHERE id = ?",
                    discusion);
            jdbc.execute("SET LOCAL session_replication_role = origin");
            Instant antes = actualizadoEn(discusion);
            assertThat(antes).isEqualTo(Instant.parse("2020-01-01T00:00:00Z"));

            // Act: un comentario y una reaccion solo tocan contadores
            comentar(discusion, token, "No es una edicion");
            jdbc.update("UPDATE publicacion SET contador_reacciones = contador_reacciones + 1 WHERE id = ?",
                    discusion);

            // Assert
            assertThat(actualizadoEn(discusion)).isEqualTo(antes);

            // Y un cambio real si lo mueve
            jdbc.update("UPDATE publicacion SET titulo = 'Titulo realmente editado' WHERE id = ?", discusion);
            assertThat(actualizadoEn(discusion)).isAfter(antes);
        }
    }

    // ==================================================================
    // V9 · integridad del discriminador y del hilo
    // ==================================================================

    @Nested
    @DisplayName("V9 integridad: discriminador supertipo/subtipo y hilo de comentarios")
    class Integridad {

        @Test
        @DisplayName("una discusion no puede recibir un subtipo de proyecto")
        void el_subtipo_debe_coincidir_con_el_tipo() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            UUID discusion = crearDiscusion(autenticar(autor.correo()));

            // Act + Assert
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO publicacion_proyecto (publicacion_id, resumen) VALUES (?, 'Resumen')",
                    discusion))
                    .hasMessageContaining("es de tipo DISCUSION y no admite un subtipo PROYECTO");
        }

        @Test
        @DisplayName("una publicacion con subtipo no puede cambiar de tipo")
        void el_tipo_es_inmutable_con_subtipo() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            UUID discusion = crearDiscusion(autenticar(autor.correo()));

            // Act + Assert
            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE publicacion SET tipo = 'PROYECTO' WHERE id = ?", discusion))
                    .hasMessageContaining("su tipo no puede cambiar");
        }

        @Test
        @DisplayName("una respuesta no puede colgar de un comentario de otra publicacion")
        void la_respuesta_cuelga_de_la_misma_publicacion() throws Exception {
            // Arrange: un comentario en la discusion A
            var autor = datos.crear("DESARROLLADOR", false);
            String token = autenticar(autor.correo());
            UUID discusionA = crearDiscusion(token);
            UUID discusionB = crearDiscusion(token);
            UUID comentarioEnA = comentar(discusionA, token, "Comentario en A");

            // Act + Assert: respuesta en B que dice colgar de A
            assertThatThrownBy(() -> jdbc.update("""
                    INSERT INTO comentario (publicacion_id, autor_id, comentario_padre_id, contenido, profundidad)
                    VALUES (?, ?, ?, 'Respuesta cruzada', 1)
                    """, discusionB, autor.id(), comentarioEnA))
                    .hasMessageContaining("fk_comentario_padre_misma_publicacion");
        }

        @Test
        @DisplayName("una respuesta en la misma publicacion si es valida y tambien cuenta")
        void la_respuesta_en_la_misma_publicacion_es_valida() throws Exception {
            // Arrange
            var autor = datos.crear("DESARROLLADOR", false);
            String token = autenticar(autor.correo());
            UUID discusion = crearDiscusion(token);
            UUID comentario = comentar(discusion, token, "Comentario raiz");

            // Act
            jdbc.update("""
                    INSERT INTO comentario (publicacion_id, autor_id, comentario_padre_id, contenido, profundidad)
                    VALUES (?, ?, ?, 'Respuesta valida', 1)
                    """, discusion, autor.id(), comentario);

            // Assert
            assertThat(contadorDe(discusion)).isEqualTo(2);
        }
    }

    // ==================================================================
    // V10 · auditoria por triggers
    // ==================================================================

    @Nested
    @DisplayName("V10 auditoria por triggers en tablas criticas")
    class AuditoriaPorTriggers {

        @Test
        @DisplayName("asignar un rol por consola queda auditado con el usuario de base de datos")
        void asignar_un_rol_queda_auditado() {
            // Arrange
            var usuario = datos.crear("DESARROLLADOR", false);
            var admin = datos.crear("ADMIN", true);

            // Act: escalada de privilegios hecha por consola
            jdbc.update("""
                    INSERT INTO usuario_rol (usuario_id, rol_id, asignado_por)
                    SELECT ?, id, ? FROM rol WHERE codigo = 'MODERADOR'
                    """, usuario.id(), admin.id());

            // Assert
            Map<String, Object> fila = ultimaAuditoria("usuario_rol", "INSERT", usuario.id() + ":%");
            assertThat(fila.get("actor_id")).isEqualTo(admin.id());
            assertThat(fila.get("usuario_bd")).isEqualTo(usuarioBd());
            assertThat(fila.get("datos_antes")).isNull();
            assertThat(fila.get("datos_despues").toString()).contains("\"asignado_por\"");
        }

        @Test
        @DisplayName("retirar un rol deja la fila borrada en datos_antes")
        void retirar_un_rol_queda_auditado() {
            // Arrange
            var usuario = datos.crear("DESARROLLADOR", false);

            // Act
            jdbc.update("DELETE FROM usuario_rol WHERE usuario_id = ?", usuario.id());

            // Assert
            Map<String, Object> fila = ultimaAuditoria("usuario_rol", "DELETE", usuario.id() + ":%");
            assertThat(fila.get("datos_antes").toString()).contains(usuario.id().toString());
            assertThat(fila.get("datos_despues")).isNull();
        }

        @Test
        @DisplayName("cambiar los permisos de un rol queda auditado")
        void cambiar_permisos_queda_auditado() {
            // Arrange
            int antes = contarAuditoria("rol_permiso", "DELETE");

            // Act
            jdbc.update("""
                    DELETE FROM rol_permiso
                     WHERE rol_id = (SELECT id FROM rol WHERE codigo = 'DESARROLLADOR')
                       AND permiso_id = (SELECT id FROM permiso WHERE codigo = 'usuario:seguir')
                    """);

            // Assert
            assertThat(contarAuditoria("rol_permiso", "DELETE")).isEqualTo(antes + 1);
        }

        @Test
        @DisplayName("una suspension queda auditada con quien la aplico")
        void una_suspension_queda_auditada() {
            // Arrange
            var usuario = datos.crear("DESARROLLADOR", false);
            var moderador = datos.crear("MODERADOR", true);

            // Act
            UUID suspension = jdbc.queryForObject("""
                    INSERT INTO suspension (usuario_id, motivo, termina_en, aplicada_por)
                    VALUES (?, 'Spam reiterado en comentarios', now() + interval '7 days', ?)
                    RETURNING id
                    """, UUID.class, usuario.id(), moderador.id());

            // Assert
            Map<String, Object> fila = ultimaAuditoria("suspension", "INSERT", suspension.toString());
            assertThat(fila.get("actor_id")).isEqualTo(moderador.id());
        }

        @Test
        @DisplayName("un cambio de estado del usuario se audita sin correo, nombre ni secretos")
        void el_estado_del_usuario_se_audita_sin_datos_sensibles() {
            // Arrange
            var usuario = datos.crear("DESARROLLADOR", false);

            // Act
            jdbc.update("UPDATE usuario SET estado = 'SUSPENDIDO' WHERE id = ?", usuario.id());

            // Assert
            Map<String, Object> fila = ultimaAuditoria("usuario", "UPDATE", usuario.id().toString());
            String antes = fila.get("datos_antes").toString();
            String despues = fila.get("datos_despues").toString();
            assertThat(antes).contains("\"estado\": \"ACTIVO\"");
            assertThat(despues).contains("\"estado\": \"SUSPENDIDO\"");
            assertThat(antes + despues)
                    .doesNotContain("clave_hash", "mfa_secreto", "correo", "nombre_usuario",
                            usuario.correo(), usuario.nombreUsuario());
        }

        @Test
        @DisplayName("un inicio de sesion no genera auditoria por trigger, aunque actualice la fila del usuario")
        void un_login_no_inunda_la_auditoria() throws Exception {
            // Arrange
            var usuario = datos.crear("DESARROLLADOR", false);
            int antes = contarAuditoria("usuario", "UPDATE");

            // Act: el login actualiza ultimo_acceso_en e intentos_fallidos
            autenticar(usuario.correo());

            // Assert
            assertThat(contarAuditoria("usuario", "UPDATE")).isEqualTo(antes);
        }
    }

    // ==================================================================
    // V11 · procedimiento almacenado
    // ==================================================================

    @Nested
    @DisplayName("V11 sp_consolidar_metricas_diarias")
    class ProcedimientoMetricas {

        /** Un dia del pasado sin datos de otras pruebas. */
        private final LocalDate dia = LocalDate.of(2020, 3, 15);

        @Test
        @DisplayName("calcula cada metrica del dia y es idempotente")
        void consolida_un_dia() throws Exception {
            // Arrange: actividad fechada el 15/03/2020 a las 10:00 de Colombia
            Timestamp momento = enBogota(dia, 10, 0);
            var ana = datos.crear("DESARROLLADOR", false);
            var beto = datos.crear("DESARROLLADOR", false);
            jdbc.update("UPDATE usuario SET creado_en = ? WHERE id IN (?, ?)", momento, ana.id(), beto.id());

            UUID proyecto = publicacion(ana.id(), "PROYECTO", momento);
            publicacion(ana.id(), "DISCUSION", momento);
            jdbc.update("INSERT INTO comentario (publicacion_id, autor_id, contenido, creado_en) VALUES (?, ?, 'Hola', ?)",
                    proyecto, beto.id(), momento);
            jdbc.update("""
                    INSERT INTO auditoria (tabla, operacion, registro_id, actor_id, creado_en)
                    VALUES ('usuario', 'INICIO_SESION', ?, ?, ?)
                    """, ana.id().toString(), ana.id(), momento);

            // Act: dos veces, para probar la idempotencia
            jdbc.update("CALL sp_consolidar_metricas_diarias(?, ?)", dia, dia);
            jdbc.update("CALL sp_consolidar_metricas_diarias(?, ?)", dia, dia);

            // Assert
            Map<String, Object> m = jdbc.queryForMap("SELECT * FROM metricas_diarias WHERE fecha = ?", dia);
            assertThat(m)
                    .containsEntry("usuarios_nuevos", 2)
                    .containsEntry("usuarios_activos", 2)      // ana (login, publico), beto (comento)
                    .containsEntry("publicaciones_nuevas", 2)
                    .containsEntry("proyectos_nuevos", 1)
                    .containsEntry("discusiones_nuevas", 1)
                    .containsEntry("comentarios_nuevos", 1)
                    .containsEntry("mensajes_enviados", 0)
                    .containsEntry("colaboraciones_aceptadas", 0)
                    .containsEntry("reportes_abiertos", 0);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM metricas_diarias WHERE fecha = ?",
                    Integer.class, dia)).isEqualTo(1);
        }

        @Test
        @DisplayName("los dias son de Colombia: las 11:30 p. m. del 15 cuentan para el 15, no para el 16 UTC")
        void usa_el_dia_de_colombia() {
            // Arrange: 23:30 en Bogota = 04:30 UTC del dia siguiente
            var ana = datos.crear("DESARROLLADOR", false);
            jdbc.update("UPDATE usuario SET creado_en = ? WHERE id = ?", enBogota(dia, 23, 30), ana.id());

            // Act
            jdbc.update("CALL sp_consolidar_metricas_diarias(?, ?)", dia, dia.plusDays(1));

            // Assert
            assertThat(usuariosNuevos(dia)).isEqualTo(1);
            assertThat(usuariosNuevos(dia.plusDays(1))).isZero();
        }

        @Test
        @DisplayName("un rango crea una fila por dia, aunque no haya actividad")
        void un_rango_crea_una_fila_por_dia() {
            // Act
            jdbc.update("CALL sp_consolidar_metricas_diarias(?, ?)", dia, dia.plusDays(6));

            // Assert
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM metricas_diarias WHERE fecha BETWEEN ? AND ?
                    """, Integer.class, dia, dia.plusDays(6))).isEqualTo(7);
        }

        @Test
        @DisplayName("rechaza un rango invertido")
        void rechaza_un_rango_invertido() {
            assertThatThrownBy(() -> jdbc.update("CALL sp_consolidar_metricas_diarias(?, ?)",
                    dia, dia.minusDays(1)))
                    .hasMessageContaining("Rango invertido");
        }

        @Test
        @DisplayName("rechaza los dias futuros")
        void rechaza_dias_futuros() {
            assertThatThrownBy(() -> jdbc.update("CALL sp_consolidar_metricas_diarias(?, ?)",
                    LocalDate.now().plusDays(2), LocalDate.now().plusDays(2)))
                    .hasMessageContaining("No se consolidan dias futuros");
        }

        @Test
        @DisplayName("sin argumentos consolida el dia de ayer")
        void sin_argumentos_consolida_ayer() {
            // Act
            jdbc.update("CALL sp_consolidar_metricas_diarias()");

            // Assert
            LocalDate ayer = jdbc.queryForObject(
                    "SELECT ((now() AT TIME ZONE 'America/Bogota')::date - 1)", LocalDate.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM metricas_diarias WHERE fecha = ?",
                    Integer.class, ayer)).isEqualTo(1);
        }

        private UUID publicacion(UUID autor, String tipo, Timestamp publicadoEn) {
            return jdbc.queryForObject("""
                    INSERT INTO publicacion (autor_id, tipo, titulo, contenido, estado, publicado_en, creado_en)
                    VALUES (?, ?, ?, ?, 'PUBLICADO', ?, ?)
                    RETURNING id
                    """, UUID.class, autor, tipo, TITULO, CUERPO, publicadoEn, publicadoEn);
        }

        private int usuariosNuevos(LocalDate fecha) {
            return jdbc.queryForObject("SELECT usuarios_nuevos FROM metricas_diarias WHERE fecha = ?",
                    Integer.class, fecha);
        }

        private Timestamp enBogota(LocalDate fecha, int hora, int minuto) {
            return Timestamp.from(fecha.atTime(hora, minuto)
                    .atZone(java.time.ZoneId.of("America/Bogota")).toInstant());
        }
    }

    // ------------------------------------------------------------------

    private int contadorDe(UUID publicacion) {
        return jdbc.queryForObject("SELECT contador_comentarios FROM publicacion WHERE id = ?",
                Integer.class, publicacion);
    }

    private Instant actualizadoEn(UUID publicacion) {
        return jdbc.queryForObject("SELECT actualizado_en FROM publicacion WHERE id = ?",
                Timestamp.class, publicacion).toInstant();
    }

    private Map<String, Object> ultimaAuditoria(String tabla, String operacion, String registroLike) {
        return jdbc.queryForMap("""
                SELECT actor_id, usuario_bd, datos_antes::text AS datos_antes, datos_despues::text AS datos_despues
                  FROM auditoria
                 WHERE tabla = ? AND operacion = ? AND registro_id LIKE ?
                 ORDER BY id DESC LIMIT 1
                """, tabla, operacion, registroLike);
    }

    private int contarAuditoria(String tabla, String operacion) {
        return jdbc.queryForObject("SELECT count(*) FROM auditoria WHERE tabla = ? AND operacion = ?",
                Integer.class, tabla, operacion);
    }

    private String usuarioBd() {
        return jdbc.queryForObject("SELECT current_user", String.class);
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

    private UUID comentar(UUID publicacion, String token, String contenido) throws Exception {
        MvcResult r = mvc.perform(post("/api/v1/publicaciones/" + publicacion + "/comentarios")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contenido\": \"" + contenido + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return idDe(r);
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
