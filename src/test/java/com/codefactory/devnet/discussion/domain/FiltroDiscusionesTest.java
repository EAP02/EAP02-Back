package com.codefactory.devnet.discussion.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HU-12 - filtro del listado de discusiones y mensajes del estado vacio.
 *
 * <p>Pruebas unitarias puras. El listado contra la base esta en
 * {@code ListarDiscusionesIT}.</p>
 *
 * <p>Estructura AAA (Arrange, Act, Assert).</p>
 */
@DisplayName("HU-12 Filtro de discusiones")
class FiltroDiscusionesTest {

    @Nested
    @DisplayName("Normalizacion")
    class Normalizacion {

        @Test
        @DisplayName("pasa el slug a minusculas y recorta espacios")
        void normaliza_el_slug() {
            // Act
            FiltroDiscusiones filtro = FiltroDiscusiones.de("  Spring-Boot ");

            // Assert
            assertThat(filtro.tecnologiaSlug()).isEqualTo("spring-boot");
        }

        @ParameterizedTest(name = "tecnologia = [{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("sin etiqueta lista todas las discusiones")
        void sin_etiqueta(String tecnologia) {
            // Act
            FiltroDiscusiones filtro = FiltroDiscusiones.de(tecnologia);

            // Assert
            assertThat(filtro.hayTecnologia()).isFalse();
        }
    }

    @Nested
    @DisplayName("Criterio 3: el estado vacio orienta segun el filtro")
    class MensajeSinResultados {

        @Test
        @DisplayName("una etiqueta valida sin discusiones invita a abrir la primera")
        void etiqueta_sin_discusiones() {
            // Act
            String mensaje = FiltroDiscusiones.de("rust").mensajeSinResultados("Rust");

            // Assert
            assertThat(mensaje).contains("Rust", "Abre la primera");
        }

        @Test
        @DisplayName("una etiqueta que no existe dice que no esta en el catalogo")
        void etiqueta_inexistente() {
            // Act
            String mensaje = FiltroDiscusiones.de("cobol-2099").mensajeSinResultados(null);

            // Assert
            assertThat(mensaje).contains("cobol-2099", "no esta en el catalogo");
        }

        @Test
        @DisplayName("sin filtro ni discusiones, invita a empezar la conversacion")
        void sin_filtro() {
            // Act
            String mensaje = FiltroDiscusiones.de(null).mensajeSinResultados(null);

            // Assert
            assertThat(mensaje).contains("Abre la primera");
        }
    }
}
