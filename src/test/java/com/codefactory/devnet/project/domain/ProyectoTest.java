package com.codefactory.devnet.project.domain;

import com.codefactory.devnet.shared.api.DetalleError;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * HU-07 (AB#17) - reglas de publicacion de un proyecto.
 *
 * <p>Pruebas unitarias puras: sin contexto de Spring y sin base de datos. Eso es
 * posible porque {@link Proyecto} no conoce el framework, y es la razon practica de
 * la regla 2 de {@code FronterasModularesTest}.</p>
 *
 * <p>Estructura AAA (Arrange, Act, Assert).</p>
 */
@DisplayName("HU-07 Publicar un proyecto")
class ProyectoTest {

    private static final UUID AUTOR = UUID.randomUUID();
    private static final String TITULO_VALIDO = "Motor de plantillas en Java";
    private static final String DESCRIPCION_VALIDA =
            "Un motor de plantillas minimalista escrito en Java 17 con soporte para expresiones.";
    private static final Set<Short> STACK = Set.of((short) 1, (short) 7);

    // ==================================================================
    @Nested
    @DisplayName("Criterio 1: con datos validos queda asociado al autor")
    class CriterioUno {

        @Test
        @DisplayName("publica y conserva el autor, el titulo y el stack")
        void publica_con_datos_validos() {
            // Act
            Proyecto proyecto = publicarCon(TITULO_VALIDO, DESCRIPCION_VALIDA, STACK, null);

            // Assert
            assertThat(proyecto.autorId()).isEqualTo(AUTOR);
            assertThat(proyecto.titulo()).isEqualTo(TITULO_VALIDO);
            assertThat(proyecto.tecnologias()).containsExactlyInAnyOrderElementsOf(STACK);
            assertThat(proyecto.publicadoEn()).isNotNull();
            assertThat(proyecto.id()).isNotNull();
        }

        @Test
        @DisplayName("deriva el resumen de la descripcion cuando no se envia")
        void deriva_el_resumen() {
            // Arrange: el resumen no lo pide la HU, pero la columna es NOT NULL

            // Act
            Proyecto proyecto = publicarCon(TITULO_VALIDO, DESCRIPCION_VALIDA, STACK, null);

            // Assert: se deriva en vez de rechazar por un campo que el usuario
            // no sabe que existe
            assertThat(proyecto.resumen()).isEqualTo(DESCRIPCION_VALIDA);
        }

        @Test
        @DisplayName("normaliza espacios sobrantes en titulo y descripcion")
        void recorta_espacios() {
            // Act
            Proyecto proyecto = publicarCon("   " + TITULO_VALIDO + "  ",
                    "  " + DESCRIPCION_VALIDA + " ", STACK, null);

            // Assert
            assertThat(proyecto.titulo()).isEqualTo(TITULO_VALIDO);
            assertThat(proyecto.descripcion()).isEqualTo(DESCRIPCION_VALIDA);
        }

        @Test
        @DisplayName("acepta el estado IDEA por defecto")
        void estado_por_defecto() {
            // Act
            Proyecto proyecto = Proyecto.publicar(AUTOR, TITULO_VALIDO, DESCRIPCION_VALIDA,
                    null, STACK, null, null, null, null, false);

            // Assert
            assertThat(proyecto.estadoProyecto()).isEqualTo(EstadoProyecto.IDEA);
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Criterio 2: los campos obligatorios se validan")
    class CriterioDos {

        @Test
        @DisplayName("rechaza sin titulo")
        void rechaza_sin_titulo() {
            // Act + Assert
            ExcepcionNegocio ex = catchThrowableOfType(
                    () -> publicarCon(null, DESCRIPCION_VALIDA, STACK, null),
                    ExcepcionNegocio.class);

            assertThat(ex.codigo()).isEqualTo(CodigoErrorProyecto.PROYECTO_DATOS_INVALIDOS);
            assertThat(ex.detalles()).extracting(DetalleError::campo).contains("titulo");
        }

        @Test
        @DisplayName("rechaza sin descripcion")
        void rechaza_sin_descripcion() {
            // Act + Assert
            ExcepcionNegocio ex = catchThrowableOfType(
                    () -> publicarCon(TITULO_VALIDO, "   ", STACK, null),
                    ExcepcionNegocio.class);

            assertThat(ex.detalles()).extracting(DetalleError::campo).contains("descripcion");
        }

        @Test
        @DisplayName("informa de TODOS los campos invalidos de una sola vez")
        void acumula_todos_los_fallos() {
            // Arrange: titulo corto, descripcion corta y sin tecnologias

            // Act
            ExcepcionNegocio ex = catchThrowableOfType(
                    () -> publicarCon("abc", "corta", Set.of(), null),
                    ExcepcionNegocio.class);

            // Assert: un cliente con tres errores debe enterarse de los tres en una
            // respuesta, no descubrirlos de uno en uno
            assertThat(ex.detalles())
                    .extracting(DetalleError::campo)
                    .containsExactlyInAnyOrder("titulo", "descripcion", "tecnologias");
        }

        @Test
        @DisplayName("rechaza mas de cinco tecnologias")
        void rechaza_stack_excesivo() {
            // Arrange
            Set<Short> demasiadas = Set.of((short) 1, (short) 2, (short) 3,
                    (short) 4, (short) 5, (short) 6);

            // Act + Assert
            assertThatThrownBy(() -> publicarCon(TITULO_VALIDO, DESCRIPCION_VALIDA, demasiadas, null))
                    .isInstanceOf(ExcepcionNegocio.class);
        }

        @Test
        @DisplayName("rechaza un repositorio que no sea https")
        void rechaza_repositorio_inseguro() {
            // Act
            ExcepcionNegocio ex = catchThrowableOfType(
                    () -> publicarCon(TITULO_VALIDO, DESCRIPCION_VALIDA, STACK,
                            "http://github.com/usuario/repo"),
                    ExcepcionNegocio.class);

            // Assert: http permitiria degradar la conexion de quien siga el enlace
            assertThat(ex.detalles()).extracting(DetalleError::razon).contains("debe_ser_https");
        }

        @Test
        @DisplayName("acepta que no haya repositorio: es opcional")
        void repositorio_opcional() {
            // Act
            Proyecto proyecto = publicarCon(TITULO_VALIDO, DESCRIPCION_VALIDA, STACK, null);

            // Assert
            assertThat(proyecto.urlRepositorio()).isNull();
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Criterio 3: solo el autor puede publicar en su nombre")
    class CriterioTres {

        @Test
        @DisplayName("rechaza publicar declarando otro autor")
        void rechaza_autor_ajeno() {
            // Arrange
            UUID otro = UUID.randomUUID();

            // Act
            ExcepcionNegocio ex = catchThrowableOfType(
                    () -> Proyecto.exigirAutoriaPropia(otro, AUTOR),
                    ExcepcionNegocio.class);

            // Assert
            assertThat(ex.codigo()).isEqualTo(CodigoErrorProyecto.PROYECTO_AUTOR_AJENO);
        }

        @Test
        @DisplayName("permite cuando el autor declarado coincide")
        void permite_autor_propio() {
            // Act + Assert: no lanza
            Proyecto.exigirAutoriaPropia(AUTOR, AUTOR);
        }

        @Test
        @DisplayName("permite cuando el cliente no declara autor")
        void permite_sin_autor_declarado() {
            // Arrange: el campo es opcional; el autor real sale siempre del token

            // Act + Assert: no lanza
            Proyecto.exigirAutoriaPropia(null, AUTOR);
        }
    }

    // ==================================================================

    private static Proyecto publicarCon(String titulo, String descripcion,
                                        Set<Short> tecnologias, String urlRepositorio) {
        return Proyecto.publicar(AUTOR, titulo, descripcion, null, tecnologias,
                urlRepositorio, EstadoProyecto.EN_DESARROLLO, "MIT", null, false);
    }
}