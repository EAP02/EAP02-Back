package com.codefactory.devnet.discussion.domain;

import com.codefactory.devnet.shared.api.DetalleError;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * HU-11 - reglas de creacion de una discusion.
 *
 * <p>Pruebas unitarias puras: {@link Discusion} no conoce Spring ni JPA. El recorrido
 * completo, con la base y el hilo, esta en {@code CrearDiscusionIT}.</p>
 *
 * <p>Estructura AAA (Arrange, Act, Assert).</p>
 */
@DisplayName("HU-11 Crear una discusion")
class DiscusionTest {

    private static final UUID AUTOR = UUID.randomUUID();
    private static final String TITULO = "Spring Boot 4: vale la pena migrar ya?";
    private static final String CUERPO =
            "Estamos evaluando migrar desde Boot 3. Que problemas encontraron con Jackson 3?";
    private static final Set<Short> ETIQUETAS = Set.of((short) 1, (short) 7);

    // ==================================================================
    @Nested
    @DisplayName("Criterio 1: con datos validos queda publicada")
    class DatosValidos {

        @Test
        @DisplayName("conserva titulo, cuerpo, etiquetas y fecha de publicacion")
        void abre_con_datos_validos() {
            // Act
            Discusion discusion = Discusion.abrir(AUTOR, TITULO, CUERPO, CategoriaDiscusion.PREGUNTA, ETIQUETAS);

            // Assert
            assertThat(discusion.id()).isNotNull();
            assertThat(discusion.titulo()).isEqualTo(TITULO);
            assertThat(discusion.cuerpo()).isEqualTo(CUERPO);
            assertThat(discusion.categoria()).isEqualTo(CategoriaDiscusion.PREGUNTA);
            assertThat(discusion.tecnologias()).containsExactlyInAnyOrderElementsOf(ETIQUETAS);
            assertThat(discusion.publicadoEn()).isNotNull();
        }

        @Test
        @DisplayName("sin categoria, es un DEBATE")
        void categoria_por_defecto() {
            // Act
            Discusion discusion = Discusion.abrir(AUTOR, TITULO, CUERPO, null, ETIQUETAS);

            // Assert: la HU habla de "generar conversacion"
            assertThat(discusion.categoria()).isEqualTo(CategoriaDiscusion.DEBATE);
        }

        @Test
        @DisplayName("recorta espacios sobrantes en titulo y cuerpo")
        void recorta_espacios() {
            // Act
            Discusion discusion = Discusion.abrir(AUTOR, "  " + TITULO + " ", " " + CUERPO + "  ", null, ETIQUETAS);

            // Assert
            assertThat(discusion.titulo()).isEqualTo(TITULO);
            assertThat(discusion.cuerpo()).isEqualTo(CUERPO);
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Criterio 2: si falta un campo obligatorio, se rechaza")
    class CamposObligatorios {

        @ParameterizedTest(name = "cuerpo = [{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {"    "})
        @DisplayName("rechaza sin cuerpo, senalando el campo")
        void rechaza_sin_cuerpo(String cuerpo) {
            // Act: el ejemplo literal de la HU
            ExcepcionNegocio ex = catchThrowableOfType(ExcepcionNegocio.class,
                    () -> Discusion.abrir(AUTOR, TITULO, cuerpo, null, ETIQUETAS));

            // Assert
            assertThat(ex.codigo()).isEqualTo(CodigoErrorDiscusion.DISCUSION_DATOS_INVALIDOS);
            assertThat(ex.detalles())
                    .extracting(DetalleError::campo, DetalleError::razon)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple("cuerpo", "obligatorio"));
        }

        @Test
        @DisplayName("rechaza sin titulo")
        void rechaza_sin_titulo() {
            // Act
            ExcepcionNegocio ex = catchThrowableOfType(ExcepcionNegocio.class,
                    () -> Discusion.abrir(AUTOR, null, CUERPO, null, ETIQUETAS));

            // Assert
            assertThat(ex.detalles()).extracting(DetalleError::campo).containsExactly("titulo");
        }

        @Test
        @DisplayName("rechaza sin etiquetas: la discusion debe tratar sobre alguna tecnologia")
        void rechaza_sin_etiquetas() {
            // Act
            ExcepcionNegocio ex = catchThrowableOfType(ExcepcionNegocio.class,
                    () -> Discusion.abrir(AUTOR, TITULO, CUERPO, null, Set.of()));

            // Assert
            assertThat(ex.detalles()).extracting(DetalleError::campo).containsExactly("tecnologias");
        }

        @Test
        @DisplayName("rechaza mas de cinco etiquetas")
        void rechaza_demasiadas_etiquetas() {
            // Arrange
            Set<Short> seis = Set.of((short) 1, (short) 2, (short) 3, (short) 4, (short) 5, (short) 6);

            // Act
            ExcepcionNegocio ex = catchThrowableOfType(ExcepcionNegocio.class,
                    () -> Discusion.abrir(AUTOR, TITULO, CUERPO, null, seis));

            // Assert
            assertThat(ex.detalles()).extracting(DetalleError::campo).containsExactly("tecnologias");
        }

        @Test
        @DisplayName("informa de TODOS los campos invalidos de una sola vez")
        void acumula_todos_los_fallos() {
            // Act
            ExcepcionNegocio ex = catchThrowableOfType(ExcepcionNegocio.class,
                    () -> Discusion.abrir(AUTOR, "abc", "corto", null, null));

            // Assert
            assertThat(ex.detalles())
                    .extracting(DetalleError::campo)
                    .containsExactlyInAnyOrder("titulo", "cuerpo", "tecnologias");
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Criterio 3: queda asociada al miembro que la creo")
    class Autoria {

        @Test
        @DisplayName("conserva el autor recibido")
        void conserva_el_autor() {
            // Act
            Discusion discusion = Discusion.abrir(AUTOR, TITULO, CUERPO, null, ETIQUETAS);

            // Assert
            assertThat(discusion.autorId()).isEqualTo(AUTOR);
        }
    }
}
