package com.codefactory.devnet.profile.domain;

import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Regla ABAC de propiedad del perfil.
 *
 * <p>Cubre el hueco que permitia a cualquier usuario autenticado editar el perfil de
 * otro pasando su identificador en la ruta (IDOR). Prueba unitaria pura: la regla
 * vive en el dominio y no necesita contexto de Spring.</p>
 */
@DisplayName("PoliticaPerfil - solo el titular edita su perfil")
class PoliticaPerfilTest {

    private static final UUID TITULAR = UUID.randomUUID();

    @Test
    @DisplayName("permite cuando el autenticado es el titular")
    void permite_al_titular() {
        // Act + Assert: no lanza
        PoliticaPerfil.exigirTitular(TITULAR, TITULAR);
    }

    @Test
    @DisplayName("rechaza con 403 cuando es el perfil de otra persona")
    void rechaza_perfil_ajeno() {
        // Arrange
        UUID otro = UUID.randomUUID();

        // Act
        ExcepcionNegocio ex = catchThrowableOfType(
                () -> PoliticaPerfil.exigirTitular(TITULAR, otro),
                ExcepcionNegocio.class);

        // Assert
        assertThat(ex.codigo()).isEqualTo(CodigoErrorPerfil.PERFIL_AJENO);
        assertThat(ex.codigo().estadoHttp()).isEqualTo(403);
    }

    @Test
    @DisplayName("rechaza cuando no hay sesion")
    void rechaza_anonimo() {
        // Arrange: peticion sin token; autenticado() devolvio vacio

        // Act
        ExcepcionNegocio ex = catchThrowableOfType(
                () -> PoliticaPerfil.exigirTitular(TITULAR, null),
                ExcepcionNegocio.class);

        // Assert: nunca se interpreta "sin identidad" como "es el titular"
        assertThat(ex.codigo()).isEqualTo(CodigoErrorPerfil.PERFIL_AJENO);
    }
}