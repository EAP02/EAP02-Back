package com.codefactory.devnet.discussion.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU-12 - extracto del cuerpo que se muestra en el listado.
 *
 * <p>Prueba unitaria pura de la unica regla del caso de uso que no depende de la base.</p>
 */
@DisplayName("HU-12 Extracto del listado")
class ListarDiscusionesTest {

    @Test
    @DisplayName("un cuerpo corto se muestra completo")
    void cuerpo_corto() {
        // Arrange
        String cuerpo = "Cuerpo corto de una discusion de prueba.";

        // Act + Assert
        assertThat(ListarDiscusiones.extracto(cuerpo)).isEqualTo(cuerpo);
    }

    @Test
    @DisplayName("un cuerpo largo se recorta a 200 caracteres con puntos suspensivos")
    void cuerpo_largo() {
        // Arrange
        String cuerpo = "a".repeat(500);

        // Act
        String extracto = ListarDiscusiones.extracto(cuerpo);

        // Assert
        assertThat(extracto).hasSize(ListarDiscusiones.EXTRACTO_MAX).endsWith("...");
    }
}
