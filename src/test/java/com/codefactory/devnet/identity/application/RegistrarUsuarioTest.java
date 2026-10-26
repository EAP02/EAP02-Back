package com.codefactory.devnet.identity.application;

import com.codefactory.devnet.identity.api.UsuarioRegistrado;
import com.codefactory.devnet.identity.domain.CodigoErrorIdentidad;
import com.codefactory.devnet.identity.domain.EstadoUsuario;
import com.codefactory.devnet.identity.domain.RepositorioUsuarios;
import com.codefactory.devnet.identity.domain.Usuario;
import com.codefactory.devnet.shared.api.CodigoErrorComun;
import com.codefactory.devnet.shared.api.DetalleError;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RegistrarUsuario")
class RegistrarUsuarioTest {

    private static final String CLAVE = "Prueba2026!";

    private RepositorioUsuarios usuarios;
    private ApplicationEventPublisher eventos;
    private RegistrarUsuario registrar;

    @BeforeEach
    void preparar() {
        usuarios = mock(RepositorioUsuarios.class);
        eventos = mock(ApplicationEventPublisher.class);
        PasswordEncoder codificador = mock(PasswordEncoder.class);
        when(codificador.encode(CLAVE)).thenReturn("hash");
        when(usuarios.crear(anyString(), anyString(), anyString()))
                .thenAnswer(i -> usuario(i.getArgument(1)));
        registrar = new RegistrarUsuario(usuarios, codificador, eventos);
    }

    // ==================================================================
    @Nested
    @DisplayName("cuando los datos son validos")
    class Valido {

        @Test
        @DisplayName("normaliza el correo y anuncia el registro para que se cree el perfil")
        void normaliza_y_publica_el_evento() {
            // Act
            var resultado = registrar.ejecutar("  Ana@Ejemplo.COM ", CLAVE, "ana_dev");

            // Assert
            assertThat(resultado.correo()).isEqualTo("ana@ejemplo.com");
            assertThat(resultado.nombreUsuario()).isEqualTo("ana_dev");
            verify(usuarios).crear("ana@ejemplo.com", "ana_dev", "hash");
            verify(eventos).publishEvent(any(UsuarioRegistrado.class));
        }

        @Test
        @DisplayName("sin nombre, lo deriva del correo descartando el punto que la base no admite")
        void deriva_el_nombre_del_correo() {
            // Act
            var resultado = registrar.ejecutar("ana.dev@ejemplo.com", CLAVE, null);

            // Assert
            assertThat(resultado.nombreUsuario()).isEqualTo("anadev");
        }

        @Test
        @DisplayName("descarta mayusculas, espacios y simbolos del nombre solicitado")
        void limpia_el_nombre_solicitado() {
            // Act
            var resultado = registrar.ejecutar("ana@ejemplo.com", CLAVE, " Ana Dev! ");

            // Assert
            assertThat(resultado.nombreUsuario()).isEqualTo("anadev");
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("cuando el nombre de usuario no cumple el formato de la base")
    class NombreInvalido {

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"ab", "-ana", "_ana", "una-cadena-de-mas-de-treinta-caracteres"})
        @DisplayName("400 VALIDACION_FALLIDA senalando nombreUsuario, sin llegar a la base")
        void rechaza_el_nombre(String nombre) {
            // Act
            ExcepcionNegocio ex = catchThrowableOfType(ExcepcionNegocio.class,
                    () -> registrar.ejecutar("ana@ejemplo.com", CLAVE, nombre));

            // Assert
            assertThat(ex.codigo()).isEqualTo(CodigoErrorComun.VALIDACION_FALLIDA);
            assertThat(ex.detalles()).extracting(DetalleError::campo).containsExactly("nombreUsuario");
            verify(usuarios, never()).crear(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("si el nombre derivado del correo no es valido, tambien 400: no se inventa uno")
        void nombre_derivado_invalido() {
            // Act
            ExcepcionNegocio ex = catchThrowableOfType(ExcepcionNegocio.class,
                    () -> registrar.ejecutar("a.b@ejemplo.com", CLAVE, null));

            // Assert
            assertThat(ex.codigo()).isEqualTo(CodigoErrorComun.VALIDACION_FALLIDA);
            assertThat(ex.detalles()).extracting(DetalleError::valor).containsExactly("ab");
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("cuando ya existe")
    class Duplicado {

        @Test
        @DisplayName("409 AUTH_CORREO_YA_REGISTRADO si el correo esta en uso")
        void correo_duplicado() {
            // Arrange
            when(usuarios.existeCorreo("ana@ejemplo.com")).thenReturn(true);

            // Act
            ExcepcionNegocio ex = catchThrowableOfType(ExcepcionNegocio.class,
                    () -> registrar.ejecutar("ANA@ejemplo.com", CLAVE, "ana_dev"));

            // Assert
            assertThat(ex.codigo()).isEqualTo(CodigoErrorIdentidad.AUTH_CORREO_YA_REGISTRADO);
            verify(eventos, never()).publishEvent(any());
        }

        @Test
        @DisplayName("409 AUTH_NOMBRE_USUARIO_YA_REGISTRADO si el nombre esta en uso")
        void nombre_duplicado() {
            // Arrange
            when(usuarios.existeNombreUsuario(eq("ana_dev"))).thenReturn(true);

            // Act
            ExcepcionNegocio ex = catchThrowableOfType(ExcepcionNegocio.class,
                    () -> registrar.ejecutar("ana@ejemplo.com", CLAVE, "ana_dev"));

            // Assert
            assertThat(ex.codigo()).isEqualTo(CodigoErrorIdentidad.AUTH_NOMBRE_USUARIO_YA_REGISTRADO);
            verify(usuarios, never()).crear(anyString(), anyString(), anyString());
        }
    }

    private static Usuario usuario(String nombreUsuario) {
        return new Usuario(UUID.randomUUID(), nombreUsuario, "hash", EstadoUsuario.ACTIVO,
                false, (short) 0, null, Set.of());
    }
}
