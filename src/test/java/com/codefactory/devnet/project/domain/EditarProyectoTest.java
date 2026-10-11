package com.codefactory.devnet.project.domain;

import com.codefactory.devnet.shared.api.DetalleError;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * HU-08 (AB#18) - reglas de edicion de un proyecto.
 *
 * <p>Pruebas unitarias puras, como {@code ProyectoTest}: ni Spring ni base de datos.
 * Cubren {@link Proyecto#editar} y {@link PoliticaProyecto}; el recorrido extremo a
 * extremo esta en {@code EditarProyectoIT}.</p>
 *
 * <p>Estructura AAA (Arrange, Act, Assert).</p>
 */
@DisplayName("HU-08 Editar un proyecto")
class EditarProyectoTest {

    private static final UUID AUTOR = UUID.randomUUID();
    private static final UUID OTRO = UUID.randomUUID();
    private static final String TITULO_NUEVO = "Motor de plantillas en Java 2.0";
    private static final String DESCRIPCION_NUEVA =
            "Motor de plantillas minimalista en Java 17, ahora con soporte para bucles.";
    private static final Set<Short> STACK_NUEVO = Set.of((short) 1, (short) 11);
    private static final Instant PUBLICADO_EN = Instant.parse("2026-09-20T10:00:00Z");

    // ==================================================================
    @Nested
    @DisplayName("HU-08.1: el autor edita y los cambios se conservan")
    class EdicionDelAutor {

        @Test
        @DisplayName("reemplaza el contenido por el nuevo")
        void reemplaza_el_contenido() {
            // Arrange
            Proyecto original = proyectoEn(EstadoPublicacion.PUBLICADO);

            // Act
            Proyecto editado = original.editar(TITULO_NUEVO, DESCRIPCION_NUEVA, null, STACK_NUEVO,
                    "https://github.com/ana/motor-v2", EstadoProyecto.ESTABLE, "Apache-2.0", null, true);

            // Assert
            assertThat(editado.titulo()).isEqualTo(TITULO_NUEVO);
            assertThat(editado.descripcion()).isEqualTo(DESCRIPCION_NUEVA);
            assertThat(editado.tecnologias()).containsExactlyInAnyOrderElementsOf(STACK_NUEVO);
            assertThat(editado.urlRepositorio()).isEqualTo("https://github.com/ana/motor-v2");
            assertThat(editado.estadoProyecto()).isEqualTo(EstadoProyecto.ESTABLE);
            assertThat(editado.licencia()).isEqualTo("Apache-2.0");
            assertThat(editado.buscaColaboradores()).isTrue();
        }

        @Test
        @DisplayName("conserva identificador, autor, fecha de publicacion y estado")
        void conserva_la_identidad() {
            // Arrange
            Proyecto original = proyectoEn(EstadoPublicacion.PUBLICADO);

            // Act
            Proyecto editado = editarConDatosValidos(original);

            // Assert: editar no es volver a publicar
            assertThat(editado.id()).isEqualTo(original.id());
            assertThat(editado.autorId()).isEqualTo(AUTOR);
            assertThat(editado.publicadoEn()).isEqualTo(PUBLICADO_EN);
            assertThat(editado.estadoPublicacion()).isEqualTo(EstadoPublicacion.PUBLICADO);
        }

        @Test
        @DisplayName("no modifica el proyecto original: devuelve uno nuevo")
        void es_inmutable() {
            // Arrange
            Proyecto original = proyectoEn(EstadoPublicacion.PUBLICADO);
            String tituloOriginal = original.titulo();

            // Act
            editarConDatosValidos(original);

            // Assert
            assertThat(original.titulo()).isEqualTo(tituloOriginal);
        }

        @Test
        @DisplayName("aplica la misma normalizacion que al publicar")
        void normaliza_como_al_publicar() {
            // Arrange
            Proyecto original = proyectoEn(EstadoPublicacion.PUBLICADO);

            // Act: espacios sobrantes, sin resumen, sin estado de proyecto
            Proyecto editado = original.editar("  " + TITULO_NUEVO + " ", " " + DESCRIPCION_NUEVA + "  ",
                    null, STACK_NUEVO, null, null, "  ", null, false);

            // Assert
            assertThat(editado.titulo()).isEqualTo(TITULO_NUEVO);
            assertThat(editado.resumen()).isEqualTo(DESCRIPCION_NUEVA);
            assertThat(editado.estadoProyecto()).isEqualTo(EstadoProyecto.IDEA);
            assertThat(editado.licencia()).isNull();
        }

        @Test
        @DisplayName("permite quitar el repositorio enlazado")
        void permite_quitar_el_repositorio() {
            // Arrange
            Proyecto original = proyectoEn(EstadoPublicacion.PUBLICADO);

            // Act: PUT completo, el campo que no se envia queda vacio
            Proyecto editado = original.editar(TITULO_NUEVO, DESCRIPCION_NUEVA, null, STACK_NUEVO,
                    null, EstadoProyecto.ESTABLE, null, null, false);

            // Assert
            assertThat(editado.urlRepositorio()).isNull();
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Los campos se validan con las mismas reglas que al publicar")
    class Validacion {

        @Test
        @DisplayName("informa de TODOS los campos invalidos de una sola vez")
        void acumula_todos_los_fallos() {
            // Arrange
            Proyecto original = proyectoEn(EstadoPublicacion.PUBLICADO);

            // Act
            ExcepcionNegocio ex = catchThrowableOfType(ExcepcionNegocio.class,
                    () -> original.editar("abc", "corta", null, Set.of(),
                            "http://github.com/inseguro", null, null, null, false));

            // Assert
            assertThat(ex.codigo()).isEqualTo(CodigoErrorProyecto.PROYECTO_DATOS_INVALIDOS);
            assertThat(ex.detalles())
                    .extracting(DetalleError::campo)
                    .containsExactlyInAnyOrder("titulo", "descripcion", "tecnologias", "urlRepositorio");
        }

        @Test
        @DisplayName("rechaza vaciar el titulo")
        void rechaza_titulo_vacio() {
            // Arrange
            Proyecto original = proyectoEn(EstadoPublicacion.PUBLICADO);

            // Act
            ExcepcionNegocio ex = catchThrowableOfType(ExcepcionNegocio.class,
                    () -> original.editar("   ", DESCRIPCION_NUEVA, null, STACK_NUEVO,
                            null, null, null, null, false));

            // Assert
            assertThat(ex.detalles()).extracting(DetalleError::campo).containsExactly("titulo");
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Solo un proyecto publicado admite edicion")
    class EstadoDeLaPublicacion {

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = EstadoPublicacion.class, names = {"OCULTO", "ARCHIVADO", "BORRADOR", "EN_REVISION"})
        @DisplayName("rechaza editar con PROYECTO_NO_EDITABLE")
        void rechaza_estados_no_editables(EstadoPublicacion estado) {
            // Arrange
            Proyecto original = proyectoEn(estado);

            // Act
            ExcepcionNegocio ex = catchThrowableOfType(ExcepcionNegocio.class,
                    () -> editarConDatosValidos(original));

            // Assert
            assertThat(ex.codigo()).isEqualTo(CodigoErrorProyecto.PROYECTO_NO_EDITABLE);
        }

        @Test
        @DisplayName("comprueba el estado antes que el contenido")
        void el_estado_va_antes_que_la_forma() {
            // Arrange
            Proyecto oculto = proyectoEn(EstadoPublicacion.OCULTO);

            // Act: contenido invalido sobre un proyecto que no admite cambios
            ExcepcionNegocio ex = catchThrowableOfType(ExcepcionNegocio.class,
                    () -> oculto.editar("abc", "corta", null, Set.of(), null, null, null, null, false));

            // Assert: no tiene sentido pedir que corrija campos de algo que no puede editar
            assertThat(ex.codigo()).isEqualTo(CodigoErrorProyecto.PROYECTO_NO_EDITABLE);
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("HU-08.2: solo el autor o un moderador puede modificar")
    class Politica {

        @Test
        @DisplayName("el autor puede modificar su proyecto")
        void el_autor_puede() {
            assertThat(PoliticaProyecto.puedeModificar(AUTOR, AUTOR, false)).isTrue();
        }

        @Test
        @DisplayName("otro usuario sin permiso de moderar no puede")
        void un_tercero_no_puede() {
            assertThat(PoliticaProyecto.puedeModificar(AUTOR, OTRO, false)).isFalse();
        }

        @Test
        @DisplayName("un moderador puede modificar un proyecto ajeno")
        void un_moderador_puede() {
            assertThat(PoliticaProyecto.puedeModificar(AUTOR, OTRO, true)).isTrue();
        }

        @Test
        @DisplayName("una peticion anonima nunca puede, aunque diga tener permisos")
        void anonimo_no_puede() {
            assertThat(PoliticaProyecto.puedeModificar(AUTOR, null, true)).isFalse();
        }

        @Test
        @DisplayName("la edicion de un moderador sobre contenido ajeno cuenta como intervencion")
        void detecta_la_intervencion_de_moderacion() {
            assertThat(PoliticaProyecto.esIntervencionDeModeracion(AUTOR, OTRO)).isTrue();
            assertThat(PoliticaProyecto.esIntervencionDeModeracion(AUTOR, AUTOR)).isFalse();
        }

        @Test
        @DisplayName("rechaza con 403 PROYECTO_AJENO si el proyecto es publico")
        void rechazo_sobre_proyecto_publico() {
            // Act
            ExcepcionNegocio ex = PoliticaProyecto.rechazoPara(proyectoEn(EstadoPublicacion.PUBLICADO));

            // Assert: su existencia ya es publica, ocultarla no aporta nada
            assertThat(ex.codigo()).isEqualTo(CodigoErrorProyecto.PROYECTO_AJENO);
            assertThat(ex.codigo().estadoHttp()).isEqualTo(403);
        }

        @Test
        @DisplayName("rechaza con 404 si el proyecto no es visible al publico")
        void rechazo_sobre_proyecto_oculto() {
            // Act
            ExcepcionNegocio ex = PoliticaProyecto.rechazoPara(proyectoEn(EstadoPublicacion.OCULTO));

            // Assert: un 403 confirmaria que existe algo que no deberia poder ver (ADR-005)
            assertThat(ex.codigo()).isEqualTo(CodigoErrorProyecto.PROYECTO_NO_ENCONTRADO);
        }
    }

    // ==================================================================

    private static Proyecto proyectoEn(EstadoPublicacion estado) {
        return Proyecto.rehidratar(UUID.randomUUID(), AUTOR, "Motor de plantillas en Java",
                "Un motor de plantillas minimalista escrito en Java 17 con soporte para expresiones.",
                "Un motor de plantillas minimalista.", Set.of((short) 1, (short) 7),
                "https://github.com/ana/motor", EstadoProyecto.EN_DESARROLLO, "MIT", null, false,
                PUBLICADO_EN, estado);
    }

    private static Proyecto editarConDatosValidos(Proyecto original) {
        return original.editar(TITULO_NUEVO, DESCRIPCION_NUEVA, null, STACK_NUEVO,
                null, EstadoProyecto.ESTABLE, "MIT", null, false);
    }
}
