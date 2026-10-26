package com.codefactory.devnet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Inicio de sesion cuando algo falla, verificado extremo a extremo.
 *
 * <p>El camino feliz ya lo ejercitan todas las demas IT al autenticarse. Aqui va lo
 * que solo se ve con la base real: que los intentos fallidos se acumulan entre
 * peticiones hasta bloquear la cuenta, y que cada fallo queda en {@code auditoria}.</p>
 *
 * <p><b>Requiere Docker.</b> Lo ejecuta Failsafe en {@code ./mvnw verify}.</p>
 */
@PruebaIntegracion
@DisplayName("Inicio de sesion fallido (integracion)")
class InicioSesionIT {

    /** {@code devnet.seguridad.intentos-maximos} en application-test.yml. */
    private static final int INTENTOS_MAXIMOS = 5;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private SoporteDatosPrueba datos;

    @Autowired
    private JdbcTemplate jdbc;

    // ==================================================================
    @Nested
    @DisplayName("Credenciales invalidas")
    class Credenciales {

        @Test
        @DisplayName("401 AUTH_CREDENCIALES_INVALIDAS con un correo que no existe, y queda auditado")
        void correo_inexistente() throws Exception {
            // Arrange
            String correo = "nadie-" + UUID.randomUUID().toString().substring(0, 8) + "@ejemplo.com";

            // Act
            mvc.perform(iniciarSesion(correo, SoporteDatosPrueba.CLAVE))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("AUTH_CREDENCIALES_INVALIDAS"));

            // Assert
            assertThat(fallosAuditados(correo)).isEqualTo(1);
        }

        @Test
        @DisplayName("401 AUTH_CREDENCIALES_INVALIDAS con una clave incorrecta: mismo codigo, no delata la cuenta")
        void clave_incorrecta() throws Exception {
            // Arrange
            var ana = datos.crear("DESARROLLADOR", false);

            // Act + Assert
            mvc.perform(iniciarSesion(ana.correo(), "NoEsLaClave1!"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("AUTH_CREDENCIALES_INVALIDAS"));
        }

        @Test
        @DisplayName("401 con una cuenta creada por GitHub, que no tiene clave local")
        void cuenta_sin_clave_local() throws Exception {
            // Arrange
            var ana = datos.crear("DESARROLLADOR", false);
            jdbc.update("UPDATE usuario SET clave_hash = NULL WHERE id = ?", ana.id());

            // Act + Assert
            mvc.perform(iniciarSesion(ana.correo(), SoporteDatosPrueba.CLAVE))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("AUTH_CREDENCIALES_INVALIDAS"));
        }

        @Test
        @DisplayName("401 con un hash corrupto: se trata como credencial invalida, nunca como acceso")
        void hash_corrupto() throws Exception {
            // Arrange
            var ana = datos.crear("DESARROLLADOR", false);
            jdbc.update("UPDATE usuario SET clave_hash = 'no-es-un-hash' WHERE id = ?", ana.id());

            // Act + Assert
            mvc.perform(iniciarSesion(ana.correo(), SoporteDatosPrueba.CLAVE))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("AUTH_CREDENCIALES_INVALIDAS"));
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Bloqueo por intentos fallidos")
    class Bloqueo {

        @Test
        @DisplayName("el ultimo intento permitido bloquea la cuenta, y despues ni la clave correcta entra")
        void bloquea_al_agotar_los_intentos() throws Exception {
            // Arrange
            var ana = datos.crear("DESARROLLADOR", false);
            for (int i = 1; i < INTENTOS_MAXIMOS; i++) {
                mvc.perform(iniciarSesion(ana.correo(), "NoEsLaClave1!"))
                        .andExpect(jsonPath("$.errorCode").value("AUTH_CREDENCIALES_INVALIDAS"));
            }

            // Act: el intento que agota el cupo
            mvc.perform(iniciarSesion(ana.correo(), "NoEsLaClave1!"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("AUTH_CUENTA_BLOQUEADA"));

            // Assert
            mvc.perform(iniciarSesion(ana.correo(), SoporteDatosPrueba.CLAVE))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("AUTH_CUENTA_BLOQUEADA"));
            assertThat(datos.contarEventosAuditoria("CUENTA_BLOQUEADA", ana.id())).isEqualTo(2);
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Estado de la cuenta")
    class Estado {

        @Test
        @DisplayName("403 AUTH_CUENTA_DESACTIVADA aunque la clave sea correcta")
        void cuenta_desactivada() throws Exception {
            // Arrange
            var ana = datos.crear("DESARROLLADOR", false);
            jdbc.update("UPDATE usuario SET estado = 'DESACTIVADO' WHERE id = ?", ana.id());

            // Act + Assert
            mvc.perform(iniciarSesion(ana.correo(), SoporteDatosPrueba.CLAVE))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("AUTH_CUENTA_DESACTIVADA"));
        }
    }

    // ------------------------------------------------------------------

    private static RequestBuilder iniciarSesion(String correo, String clave) {
        return post("/api/v1/auth/inicio-sesion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\": \"" + correo + "\", \"clave\": \"" + clave + "\"}");
    }

    /** Los fallos se auditan sin actor, por el correo, para no permitir enumerar cuentas. */
    private int fallosAuditados(String correo) {
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE operacion = 'INICIO_SESION_FALLIDO' AND registro_id = ?",
                Integer.class, correo);
        return total == null ? 0 : total;
    }
}
