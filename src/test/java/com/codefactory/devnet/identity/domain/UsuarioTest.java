package com.codefactory.devnet.identity.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU-06 (AB#15) - diferenciacion de permisos por rol.
 *
 * <p>Pruebas unitarias puras del dominio. Complementan a {@code ControlAccesoPorRolIT},
 * que verifica lo mismo a traves de HTTP.</p>
 */
@DisplayName("HU-06 Control de acceso por rol")
class UsuarioTest {

    private static final Rol DESARROLLADOR = new Rol("DESARROLLADOR", false,
            Set.of("publicacion:crear", "comentario:crear", "reaccion:crear"));

    private static final Rol MODERADOR = new Rol("MODERADOR", true,
            Set.of("publicacion:crear", "publicacion:moderar", "reporte:resolver"));

    private static final Rol ADMIN = new Rol("ADMIN", true,
            Set.of("publicacion:moderar", "usuario:gestionar-roles", "auditoria:consultar"));

    // ==================================================================
    @Nested
    @DisplayName("Diferenciacion de permisos entre roles")
    class Permisos {

        @Test
        @DisplayName("un desarrollador puede publicar pero NO moderar")
        void desarrollador_no_modera() {
            // Arrange
            Usuario usuario = con(DESARROLLADOR, false);

            // Assert
            assertThat(usuario.tienePermiso("publicacion:crear")).isTrue();
            assertThat(usuario.tienePermiso("publicacion:moderar")).isFalse();
        }

        @Test
        @DisplayName("un moderador con MFA inscrito SI puede moderar")
        void moderador_modera() {
            // Arrange
            Usuario usuario = con(MODERADOR, true);

            // Assert: criterio 2 de la HU
            assertThat(usuario.tienePermiso("publicacion:moderar")).isTrue();
            assertThat(usuario.tienePermiso("reporte:resolver")).isTrue();
        }

        @Test
        @DisplayName("un administrador puede consultar auditoria; un moderador no")
        void admin_y_moderador_se_diferencian() {
            // Assert
            assertThat(con(ADMIN, true).tienePermiso("auditoria:consultar")).isTrue();
            assertThat(con(MODERADOR, true).tienePermiso("auditoria:consultar")).isFalse();
        }

        @Test
        @DisplayName("acumula los permisos de todos los roles asignados")
        void acumula_permisos_de_varios_roles() {
            // Arrange: alguien con dos roles
            Usuario usuario = new Usuario(UUID.randomUUID(), "multi", "hash",
                    EstadoUsuario.ACTIVO, true, (short) 0, null,
                    Set.of(DESARROLLADOR, ADMIN));

            // Assert
            assertThat(usuario.permisosEfectivos())
                    .contains("comentario:crear", "auditoria:consultar");
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("MFA obligatorio para roles administrativos")
    class SegundoFactor {

        @Test
        @DisplayName("un moderador SIN MFA inscrito no ejerce NINGUN permiso")
        void moderador_sin_mfa_no_ejerce_nada() {
            // Arrange
            Usuario usuario = con(MODERADOR, false);

            // Assert: lineamiento 3.4. Se autentica, pero el token sale vacio.
            assertThat(usuario.tieneMfaPendiente()).isTrue();
            assertThat(usuario.permisosEfectivos()).isEmpty();
            assertThat(usuario.tienePermiso("publicacion:moderar")).isFalse();
        }

        @Test
        @DisplayName("un desarrollador sin MFA conserva sus permisos")
        void desarrollador_no_necesita_mfa() {
            // Arrange: su rol no lo exige

            // Assert
            Usuario usuario = con(DESARROLLADOR, false);
            assertThat(usuario.tieneMfaPendiente()).isFalse();
            assertThat(usuario.permisosEfectivos()).isNotEmpty();
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Bloqueo por intentos fallidos")
    class Bloqueo {

        @Test
        @DisplayName("bloquea al quinto intento fallido")
        void bloquea_al_quinto_intento() {
            // Arrange
            Usuario usuario = con(DESARROLLADOR, false);

            // Act: cuatro fallos no bloquean
            for (int i = 0; i < 4; i++) {
                assertThat(usuario.registrarIntentoFallido(5, Duration.ofMinutes(15))).isFalse();
            }
            boolean bloqueoAhora = usuario.registrarIntentoFallido(5, Duration.ofMinutes(15));

            // Assert
            assertThat(bloqueoAhora).isTrue();
            assertThat(usuario.estaBloqueada()).isTrue();
            assertThat(usuario.puedeAutenticarse()).isFalse();
        }

        @Test
        @DisplayName("un acceso correcto limpia contador y bloqueo")
        void acceso_correcto_limpia_el_estado() {
            // Arrange
            Usuario usuario = con(DESARROLLADOR, false);
            usuario.registrarIntentoFallido(5, Duration.ofMinutes(15));

            // Act
            usuario.registrarAccesoExitoso();

            // Assert
            assertThat(usuario.intentosFallidos()).isZero();
            assertThat(usuario.bloqueadoHasta()).isNull();
            assertThat(usuario.ultimoAccesoEn()).isNotNull();
        }

        @Test
        @DisplayName("un bloqueo vencido deja de aplicar")
        void bloqueo_vencido_no_aplica() {
            // Arrange: bloqueado hasta hace un minuto
            Usuario usuario = new Usuario(UUID.randomUUID(), "ana", "hash",
                    EstadoUsuario.ACTIVO, false, (short) 0,
                    Instant.now().minusSeconds(60), Set.of(DESARROLLADOR));

            // Assert
            assertThat(usuario.estaBloqueada()).isFalse();
            assertThat(usuario.puedeAutenticarse()).isTrue();
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Estado de la cuenta")
    class Estado {

        @Test
        @DisplayName("una cuenta suspendida lee pero no escribe")
        void suspendida_lee_pero_no_escribe() {
            // Arrange
            Usuario usuario = new Usuario(UUID.randomUUID(), "ana", "hash",
                    EstadoUsuario.SUSPENDIDO, false, (short) 0, null, Set.of(DESARROLLADOR));

            // Assert: la sancion limita la participacion, no vuelve invisible al usuario
            assertThat(usuario.puedeAutenticarse()).isTrue();
            assertThat(usuario.puedeEscribir()).isFalse();
        }

        @Test
        @DisplayName("una cuenta desactivada no puede ni autenticarse")
        void desactivada_no_autentica() {
            // Arrange
            Usuario usuario = new Usuario(UUID.randomUUID(), "ana", "hash",
                    EstadoUsuario.DESACTIVADO, false, (short) 0, null, Set.of(DESARROLLADOR));

            // Assert
            assertThat(usuario.puedeAutenticarse()).isFalse();
        }
    }

    // ==================================================================

    private static Usuario con(Rol rol, boolean mfaInscrito) {
        return new Usuario(UUID.randomUUID(), "usuario_prueba", "hash",
                EstadoUsuario.ACTIVO, mfaInscrito, (short) 0, null, Set.of(rol));
    }
}