package com.codefactory.devnet.project.domain;

import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * HU-08 (AB#18) - reglas de eliminacion de un proyecto.
 *
 * <p>Eliminar es archivar (regla R3). Pruebas unitarias puras de
 * {@link Proyecto#archivar}; quien puede hacerlo lo cubre {@code EditarProyectoTest},
 * porque la politica es la misma. El recorrido completo esta en
 * {@code ArchivarProyectoIT}.</p>
 *
 * <p>Estructura AAA (Arrange, Act, Assert).</p>
 */
@DisplayName("HU-08 Eliminar un proyecto")
class ArchivarProyectoTest {

    private static final String TITULO = "Sistema de Gestion";

    // ==================================================================
    @Nested
    @DisplayName("HU-08.3: la eliminacion requiere confirmacion previa")
    class Confirmacion {

        @Test
        @DisplayName("con el titulo exacto, el proyecto queda archivado")
        void archiva_con_confirmacion_correcta() {
            // Arrange
            Proyecto proyecto = proyectoEn(EstadoPublicacion.PUBLICADO);

            // Act
            Proyecto archivado = proyecto.archivar(TITULO);

            // Assert
            assertThat(archivado.estadoPublicacion()).isEqualTo(EstadoPublicacion.ARCHIVADO);
            assertThat(archivado.id()).isEqualTo(proyecto.id());
        }

        @Test
        @DisplayName("tolera espacios sobrantes alrededor del titulo")
        void tolera_espacios() {
            // Act
            Proyecto archivado = proyectoEn(EstadoPublicacion.PUBLICADO).archivar("  " + TITULO + " ");

            // Assert
            assertThat(archivado.estadoPublicacion()).isEqualTo(EstadoPublicacion.ARCHIVADO);
        }

        @ParameterizedTest(name = "confirmacion = [{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("sin confirmacion se rechaza con PROYECTO_CONFIRMACION_REQUERIDA")
        void rechaza_sin_confirmacion(String confirmacion) {
            // Arrange
            Proyecto proyecto = proyectoEn(EstadoPublicacion.PUBLICADO);

            // Act
            ExcepcionNegocio ex = catchThrowableOfType(ExcepcionNegocio.class,
                    () -> proyecto.archivar(confirmacion));

            // Assert
            assertThat(ex.codigo()).isEqualTo(CodigoErrorProyecto.PROYECTO_CONFIRMACION_REQUERIDA);
            assertThat(ex.codigo().estadoHttp()).isEqualTo(400);
        }

        @ParameterizedTest(name = "confirmacion = [{0}]")
        @ValueSource(strings = {"Otro proyecto", "sistema de gestion", "Sistema de Gestio"})
        @DisplayName("una confirmacion que no coincide se rechaza con PROYECTO_CONFIRMACION_INVALIDA")
        void rechaza_confirmacion_distinta(String confirmacion) {
            // Arrange
            Proyecto proyecto = proyectoEn(EstadoPublicacion.PUBLICADO);

            // Act
            ExcepcionNegocio ex = catchThrowableOfType(ExcepcionNegocio.class,
                    () -> proyecto.archivar(confirmacion));

            // Assert: distingue mayusculas a proposito, la confirmacion es exigente
            assertThat(ex.codigo()).isEqualTo(CodigoErrorProyecto.PROYECTO_CONFIRMACION_INVALIDA);
            assertThat(ex.codigo().estadoHttp()).isEqualTo(422);
        }

        @Test
        @DisplayName("no modifica el proyecto original: devuelve uno nuevo")
        void es_inmutable() {
            // Arrange
            Proyecto proyecto = proyectoEn(EstadoPublicacion.PUBLICADO);

            // Act
            proyecto.archivar(TITULO);

            // Assert
            assertThat(proyecto.estadoPublicacion()).isEqualTo(EstadoPublicacion.PUBLICADO);
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Transiciones permitidas por la maquina de estados")
    class Estados {

        @ParameterizedTest(name = "{0} -> ARCHIVADO")
        @EnumSource(value = EstadoPublicacion.class, names = {"PUBLICADO", "OCULTO"})
        @DisplayName("se puede archivar desde PUBLICADO y desde OCULTO")
        void estados_archivables(EstadoPublicacion estado) {
            // Act
            Proyecto archivado = proyectoEn(estado).archivar(TITULO);

            // Assert
            assertThat(archivado.estadoPublicacion()).isEqualTo(EstadoPublicacion.ARCHIVADO);
        }

        @Test
        @DisplayName("archivar algo ya archivado se rechaza con PROYECTO_NO_EDITABLE")
        void no_se_archiva_dos_veces() {
            // Arrange
            Proyecto archivado = proyectoEn(EstadoPublicacion.ARCHIVADO);

            // Act
            ExcepcionNegocio ex = catchThrowableOfType(ExcepcionNegocio.class,
                    () -> archivado.archivar(TITULO));

            // Assert
            assertThat(ex.codigo()).isEqualTo(CodigoErrorProyecto.PROYECTO_NO_EDITABLE);
        }

        @Test
        @DisplayName("comprueba el estado antes que la confirmacion")
        void el_estado_va_antes_que_la_confirmacion() {
            // Arrange
            Proyecto archivado = proyectoEn(EstadoPublicacion.ARCHIVADO);

            // Act: ni siquiera confirma
            ExcepcionNegocio ex = catchThrowableOfType(ExcepcionNegocio.class,
                    () -> archivado.archivar(null));

            // Assert: no se pide confirmar algo que no se puede hacer
            assertThat(ex.codigo()).isEqualTo(CodigoErrorProyecto.PROYECTO_NO_EDITABLE);
        }
    }

    // ==================================================================

    private static Proyecto proyectoEn(EstadoPublicacion estado) {
        return Proyecto.rehidratar(UUID.randomUUID(), UUID.randomUUID(), TITULO,
                "Sistema de gestion de inventarios para pequenos comercios.",
                "Sistema de gestion de inventarios.", Set.of((short) 1),
                null, EstadoProyecto.ESTABLE, "MIT", null, false,
                Instant.parse("2026-09-20T10:00:00Z"), estado);
    }
}
