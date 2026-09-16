package com.codefactory.devnet.shared.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pruebas del contrato de paginacion por cursor.
 *
 * <p>Estructura AAA (Arrange, Act, Assert), como pide el lineamiento 3.7 para
 * Calidad de Software.</p>
 */
@DisplayName("PaginaKeyset")
class PaginaKeysetTest {

    @Nested
    @DisplayName("al construir desde una consulta que pidio limite + 1")
    class Construccion {

        @Test
        @DisplayName("marca que hay mas y recorta cuando llegan filas de sobra")
        void recorta_y_marca_cuando_hay_pagina_siguiente() {
            // Arrange: la consulta pidio 3 y devolvio 4, la senal de que hay mas
            List<String> filas = List.of("a", "b", "c", "d");

            // Act
            PaginaKeyset<String> pagina = PaginaKeyset.de(filas, 3, s -> "cursor-" + s);

            // Assert
            assertThat(pagina.contenido()).containsExactly("a", "b", "c");
            assertThat(pagina.hayMas()).isTrue();
            assertThat(pagina.cursorSiguiente()).isEqualTo("cursor-c");
        }

        @Test
        @DisplayName("no expone cursor cuando es la ultima pagina")
        void sin_cursor_en_la_ultima_pagina() {
            // Arrange: llegaron menos filas que el limite
            List<String> filas = List.of("a", "b");

            // Act
            PaginaKeyset<String> pagina = PaginaKeyset.de(filas, 3, s -> "cursor-" + s);

            // Assert: un cursor aqui haria que el cliente pidiera una pagina vacia
            assertThat(pagina.contenido()).containsExactly("a", "b");
            assertThat(pagina.hayMas()).isFalse();
            assertThat(pagina.cursorSiguiente()).isNull();
        }

        @Test
        @DisplayName("maneja el caso de exactamente limite filas")
        void limite_exacto_no_es_pagina_siguiente() {
            // Arrange: justo el limite. No hay fila extra, luego no hay mas paginas.
            List<String> filas = List.of("a", "b", "c");

            // Act
            PaginaKeyset<String> pagina = PaginaKeyset.de(filas, 3, s -> "cursor-" + s);

            // Assert
            assertThat(pagina.hayMas()).isFalse();
            assertThat(pagina.cursorSiguiente()).isNull();
        }

        @Test
        @DisplayName("devuelve pagina vacia sin cursor cuando no hay resultados")
        void sin_resultados() {
            // Act
            PaginaKeyset<String> pagina = PaginaKeyset.de(List.of(), 3, s -> "cursor-" + s);

            // Assert
            assertThat(pagina.contenido()).isEmpty();
            assertThat(pagina.hayMas()).isFalse();
            assertThat(pagina.cursorSiguiente()).isNull();
        }
    }

    @Nested
    @DisplayName("invariantes")
    class Invariantes {

        @Test
        @DisplayName("normaliza contenido nulo a lista vacia")
        void contenido_nulo_se_vuelve_lista_vacia() {
            // Act
            PaginaKeyset<String> pagina = new PaginaKeyset<>(null, null, false);

            // Assert: el cliente nunca recibe null donde espera un arreglo
            assertThat(pagina.contenido()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("el contenido es inmutable")
        void el_contenido_no_se_puede_modificar() {
            // Arrange
            PaginaKeyset<String> pagina = new PaginaKeyset<>(List.of("a"), null, false);

            // Act + Assert
            assertThat(pagina.contenido()).isUnmodifiable();
        }

        @Test
        @DisplayName("mapear conserva cursor y bandera")
        void mapear_conserva_la_paginacion() {
            // Arrange
            PaginaKeyset<String> original = new PaginaKeyset<>(List.of("a", "b"), "c1", true);

            // Act
            PaginaKeyset<Integer> mapeada = original.mapear(String::length);

            // Assert
            assertThat(mapeada.contenido()).containsExactly(1, 1);
            assertThat(mapeada.cursorSiguiente()).isEqualTo("c1");
            assertThat(mapeada.hayMas()).isTrue();
        }
    }
}