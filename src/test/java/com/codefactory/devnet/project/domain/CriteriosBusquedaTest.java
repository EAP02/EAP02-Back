package com.codefactory.devnet.project.domain;

import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * HU-09 (AB#19) - criterios de busqueda de proyectos y mensajes del estado vacio.
 *
 * <p>Pruebas unitarias puras. Que la consulta devuelva lo correcto contra PostgreSQL lo
 * verifica {@code BuscarProyectosIT}.</p>
 *
 * <p>Estructura AAA (Arrange, Act, Assert).</p>
 */
@DisplayName("HU-09 Criterios de busqueda")
class CriteriosBusquedaTest {

    // ==================================================================
    @Nested
    @DisplayName("Normalizacion de lo que llega del cliente")
    class Normalizacion {

        @Test
        @DisplayName("recorta espacios sobrantes del texto")
        void recorta_el_texto() {
            // Act
            CriteriosBusqueda criterios = CriteriosBusqueda.de("  motor de plantillas ", null);

            // Assert
            assertThat(criterios.texto()).isEqualTo("motor de plantillas");
        }

        @ParameterizedTest(name = "texto = [{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("un texto vacio equivale a no buscar texto")
        void texto_vacio_es_sin_texto(String texto) {
            // Act
            CriteriosBusqueda criterios = CriteriosBusqueda.de(texto, null);

            // Assert
            assertThat(criterios.hayTexto()).isFalse();
        }

        @Test
        @DisplayName("acepta la tecnologia en mayusculas: el slug se pasa a minusculas")
        void normaliza_el_slug() {
            // Act
            CriteriosBusqueda criterios = CriteriosBusqueda.de(null, " Spring-Boot ");

            // Assert
            assertThat(criterios.tecnologiaSlug()).isEqualTo("spring-boot");
        }

        @Test
        @DisplayName("sin ningun criterio es explorar, y es valido")
        void sin_criterios_es_explorar() {
            // Act
            CriteriosBusqueda criterios = CriteriosBusqueda.de(null, null);

            // Assert
            assertThat(criterios.hayTexto()).isFalse();
            assertThat(criterios.hayTecnologia()).isFalse();
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Validacion")
    class Validacion {

        @Test
        @DisplayName("acepta un texto de exactamente 100 caracteres")
        void acepta_el_maximo() {
            // Act
            CriteriosBusqueda criterios = CriteriosBusqueda.de("a".repeat(CriteriosBusqueda.TEXTO_MAX), null);

            // Assert
            assertThat(criterios.texto()).hasSize(CriteriosBusqueda.TEXTO_MAX);
        }

        @Test
        @DisplayName("rechaza un texto de mas de 100 caracteres con PROYECTO_BUSQUEDA_INVALIDA")
        void rechaza_texto_excesivo() {
            // Act
            ExcepcionNegocio ex = catchThrowableOfType(ExcepcionNegocio.class,
                    () -> CriteriosBusqueda.de("a".repeat(CriteriosBusqueda.TEXTO_MAX + 1), null));

            // Assert
            assertThat(ex.codigo()).isEqualTo(CodigoErrorProyecto.PROYECTO_BUSQUEDA_INVALIDA);
            assertThat(ex.codigo().estadoHttp()).isEqualTo(400);
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("HU-09.3: el mensaje del estado vacio orienta segun lo buscado")
    class MensajeSinResultados {

        @Test
        @DisplayName("con texto y tecnologia, sugiere quitar el filtro")
        void texto_y_tecnologia() {
            // Arrange
            CriteriosBusqueda criterios = CriteriosBusqueda.de("inventarios", "rust");

            // Act
            String mensaje = criterios.mensajeSinResultados("Rust");

            // Assert
            assertThat(mensaje).contains("inventarios", "Rust", "quita el filtro");
        }

        @Test
        @DisplayName("con solo texto, sugiere otras palabras")
        void solo_texto() {
            // Act
            String mensaje = CriteriosBusqueda.de("inventarios", null).mensajeSinResultados(null);

            // Assert
            assertThat(mensaje).contains("inventarios", "otras palabras");
        }

        @Test
        @DisplayName("con solo tecnologia, sugiere explorar otras")
        void solo_tecnologia() {
            // Act
            String mensaje = CriteriosBusqueda.de(null, "rust").mensajeSinResultados("Rust");

            // Assert
            assertThat(mensaje).contains("Rust", "otras tecnologias");
        }

        @Test
        @DisplayName("con una tecnologia que no existe, dice que no esta en el catalogo")
        void tecnologia_inexistente() {
            // Act: el nombre llega nulo porque el slug no esta en el catalogo
            String mensaje = CriteriosBusqueda.de("inventarios", "cobol-2099").mensajeSinResultados(null);

            // Assert: ese es el problema real, no el texto
            assertThat(mensaje).contains("cobol-2099", "no esta en el catalogo");
        }

        @Test
        @DisplayName("sin criterios, invita a publicar el primero")
        void sin_criterios() {
            // Act
            String mensaje = CriteriosBusqueda.de(null, null).mensajeSinResultados(null);

            // Assert
            assertThat(mensaje).contains("publicar el primero");
        }
    }
}
