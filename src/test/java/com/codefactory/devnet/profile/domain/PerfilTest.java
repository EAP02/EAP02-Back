package com.codefactory.devnet.profile.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reglas del perfil tras unificar el stack en el catalogo (V4).
 */
@DisplayName("Perfil - stack declarado contra el catalogo")
class PerfilTest {

    private static final UUID ID = UUID.randomUUID();

    private static final TecnologiaDeclarada JAVA =
            new TecnologiaDeclarada((short) 1, NivelTecnologia.AVANZADO, (short) 5);
    private static final TecnologiaDeclarada SPRING =
            new TecnologiaDeclarada((short) 7, NivelTecnologia.INTERMEDIO, null);

    @Nested
    @DisplayName("Rehidratacion desde persistencia")
    class Rehidratacion {

        @Test
        @DisplayName("admite un perfil recien creado, todavia sin tecnologias")
        void perfil_vacio_es_valido_al_leerlo() {
            // Arrange + Act: es el estado de alguien que acaba de registrarse
            Perfil perfil = new Perfil(ID, "ana_dev", null, null, List.of(), null, null);

            // Assert: exigir tecnologias aqui reventaria al leer el perfil nuevo
            assertThat(perfil.tecnologias()).isEmpty();
        }

        @Test
        @DisplayName("una coleccion nula desde persistencia se lee como stack vacio")
        void tecnologias_nulas_son_vacias() {
            // Act
            Perfil perfil = new Perfil(ID, "ana_dev", null, null, null, null, null);

            // Assert
            assertThat(perfil.tecnologias()).isEmpty();
            assertThat(perfil.idsDeTecnologias()).isEmpty();
        }
    }

    @Nested
    @DisplayName("Edicion por el titular")
    class Edicion {

        @Test
        @DisplayName("exige al menos una tecnologia")
        void exige_una_tecnologia() {
            // Arrange
            Perfil perfil = perfilVacio();

            // Act + Assert
            assertThatThrownBy(() -> perfil.actualizar("Ana", "Bio suficiente", List.of(), null, null))
                    .isInstanceOf(PerfilInvalidoException.class);
        }

        @Test
        @DisplayName("una lista de tecnologias ausente es lo mismo que una vacia")
        void tecnologias_ausentes() {
            // Arrange
            Perfil perfil = perfilVacio();

            // Act + Assert
            assertThatThrownBy(() -> perfil.actualizar("Ana", "Bio suficiente", null, null, null))
                    .isInstanceOfSatisfying(PerfilInvalidoException.class,
                            e -> assertThat(e.campo()).isEqualTo("tecnologias"));
        }

        @Test
        @DisplayName("rechaza un elemento vacio dentro de la lista")
        void tecnologia_vacia() {
            // Arrange: List.of no admite nulos, que es justo lo que llega de un JSON con null
            Perfil perfil = perfilVacio();
            List<TecnologiaDeclarada> conHueco = Arrays.asList(JAVA, null);

            // Act + Assert
            assertThatThrownBy(() -> perfil.actualizar("Ana", "Bio suficiente", conHueco, null, null))
                    .isInstanceOfSatisfying(PerfilInvalidoException.class,
                            e -> assertThat(e.campo()).isEqualTo("tecnologias"));
        }

        @Test
        @DisplayName("el nombre y la biografia son obligatorios, y senala cual falta")
        void campos_obligatorios() {
            // Arrange
            Perfil perfil = perfilVacio();

            // Act + Assert
            assertThatThrownBy(() -> perfil.actualizar("   ", "Bio suficiente", List.of(JAVA), null, null))
                    .isInstanceOfSatisfying(PerfilInvalidoException.class,
                            e -> assertThat(e.campo()).isEqualTo("nombre"));
            assertThatThrownBy(() -> perfil.actualizar("Ana", null, List.of(JAVA), null, null))
                    .isInstanceOfSatisfying(PerfilInvalidoException.class,
                            e -> assertThat(e.campo()).isEqualTo("biografia"));
        }

        @Test
        @DisplayName("el avatar no puede quedar en blanco")
        void avatar_obligatorio() {
            // Arrange
            Perfil perfil = perfilVacio();

            // Act + Assert
            assertThatThrownBy(() -> perfil.actualizarAvatar(" "))
                    .isInstanceOfSatisfying(PerfilInvalidoException.class,
                            e -> assertThat(e.campo()).isEqualTo("avatarUrl"));
        }

        @Test
        @DisplayName("deduplica por tecnologia, no por el trio completo")
        void deduplica_por_tecnologia() {
            // Arrange: la misma tecnologia declarada dos veces con niveles distintos
            Perfil perfil = perfilVacio();
            TecnologiaDeclarada javaBasico =
                    new TecnologiaDeclarada((short) 1, NivelTecnologia.BASICO, null);

            // Act
            perfil.actualizar("Ana", "Bio suficiente", List.of(JAVA, javaBasico), null, null);

            // Assert: no son dos hechos, es una contradiccion. Gana el primero.
            assertThat(perfil.tecnologias()).hasSize(1);
            assertThat(perfil.tecnologias().get(0).nivel()).isEqualTo(NivelTecnologia.AVANZADO);
        }

        @Test
        @DisplayName("ordena por identificador para que la respuesta sea estable")
        void ordena_por_identificador() {
            // Arrange
            Perfil perfil = perfilVacio();

            // Act: se envian al reves
            perfil.actualizar("Ana", "Bio suficiente", List.of(SPRING, JAVA), null, null);

            // Assert: el orden de la respuesta no depende del de la peticion
            assertThat(perfil.tecnologias())
                    .extracting(TecnologiaDeclarada::tecnologiaId)
                    .containsExactly((short) 1, (short) 7);
        }

        @Test
        @DisplayName("rechaza mas de quince tecnologias")
        void rechaza_stack_excesivo() {
            // Arrange
            Perfil perfil = perfilVacio();
            List<TecnologiaDeclarada> demasiadas = java.util.stream.IntStream.rangeClosed(1, 16)
                    .mapToObj(i -> new TecnologiaDeclarada((short) i, NivelTecnologia.BASICO, null))
                    .toList();

            // Act + Assert
            assertThatThrownBy(() -> perfil.actualizar("Ana", "Bio suficiente", demasiadas, null, null))
                    .isInstanceOf(PerfilInvalidoException.class);
        }

        @Test
        @DisplayName("rechaza un enlace que no sea https")
        void rechaza_enlace_inseguro() {
            // Arrange
            Perfil perfil = perfilVacio();

            // Act + Assert: el CHECK de la V3 lo rechazaria igual, pero aqui sale
            // un 400 con el campo senalado en vez de un error de integridad
            assertThatThrownBy(() -> perfil.actualizar("Ana", "Bio suficiente",
                    List.of(JAVA), "http://github.com/ana", null))
                    .isInstanceOf(PerfilInvalidoException.class);
        }

        @Test
        @DisplayName("acepta enlaces ausentes: son opcionales")
        void enlaces_opcionales() {
            // Arrange
            Perfil perfil = perfilVacio();

            // Act
            perfil.actualizar("Ana", "Bio suficiente", List.of(JAVA), null, "   ");

            // Assert
            assertThat(perfil.githubUrl()).isNull();
            assertThat(perfil.linkedinUrl()).isNull();
        }
    }

    @Nested
    @DisplayName("Nivel declarado")
    class Nivel {

        @Test
        @DisplayName("el nivel es obligatorio")
        void nivel_obligatorio() {
            // Act + Assert
            assertThatThrownBy(() -> new TecnologiaDeclarada((short) 1, null, null))
                    .isInstanceOf(PerfilInvalidoException.class);
        }

        @Test
        @DisplayName("los anios, si vienen, deben ser plausibles")
        void anios_acotados() {
            // Act + Assert
            assertThatThrownBy(() -> new TecnologiaDeclarada((short) 1, NivelTecnologia.BASICO, (short) 99))
                    .isInstanceOf(PerfilInvalidoException.class);
        }
    }

    private static Perfil perfilVacio() {
        return new Perfil(ID, "ana_dev", null, null, List.of(), null, null);
    }
}