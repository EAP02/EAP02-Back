package com.codefactory.devnet.identity.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Igualdad y representacion de las entidades JPA de identidad.
 *
 * <p>La igualdad va por identificador: dos instancias sin persistir no son la misma
 * fila aunque tengan los mismos datos, y una coleccion de entidades no debe colapsarlas.</p>
 */
@DisplayName("Entidades de identidad")
class EntidadesIdentidadTest {

    private static final String HASH = "$argon2id$v=19$m=16384,t=2,p=1$secreto";

    @Nested
    @DisplayName("UsuarioEntity")
    class Usuario {

        @Test
        @DisplayName("toString nunca incluye el correo ni el hash de la clave")
        void to_string_sin_credenciales() {
            // Arrange
            UsuarioEntity usuario = UsuarioEntity.nueva("ana@ejemplo.com", "ana_dev", HASH);

            // Act
            String texto = usuario.toString();

            // Assert
            assertThat(texto).contains("ana_dev", "ACTIVO")
                    .doesNotContain(HASH)
                    .doesNotContain("ana@ejemplo.com");
        }

        @Test
        @DisplayName("es igual a si misma y distinta de otra cuenta con los mismos datos")
        void igualdad_por_identificador() {
            // Arrange
            UsuarioEntity ana = UsuarioEntity.nueva("ana@ejemplo.com", "ana_dev", HASH);
            UsuarioEntity mismaInstancia = ana;
            UsuarioEntity otra = UsuarioEntity.nueva("ana@ejemplo.com", "ana_dev", HASH);

            // Act + Assert
            assertThat(ana)
                    .isEqualTo(mismaInstancia)
                    .hasSameHashCodeAs(mismaInstancia)
                    .isNotEqualTo(otra)
                    .isNotEqualTo("ana_dev")
                    .isNotEqualTo(null);
        }
    }

    @Nested
    @DisplayName("RolEntity y PermisoEntity")
    class CatalogoDeSeguridad {

        @Test
        @DisplayName("sin identificador, solo son iguales a si mismas")
        void igualdad_sin_identificador() {
            // Arrange: el constructor protegido de JPA, que es como Hibernate las instancia
            RolEntity rol = new RolEntity();
            RolEntity mismoRol = rol;
            PermisoEntity permiso = new PermisoEntity();
            PermisoEntity mismoPermiso = permiso;

            // Act + Assert
            assertThat(rol).isEqualTo(mismoRol).isNotEqualTo(new RolEntity()).hasSameHashCodeAs(new RolEntity());
            assertThat(permiso).isEqualTo(mismoPermiso).isNotEqualTo(new PermisoEntity())
                    .hasSameHashCodeAs(new PermisoEntity());
        }
    }
}
