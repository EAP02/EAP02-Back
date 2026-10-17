package com.codefactory.devnet.profile.domain;

import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("HU-15 Seguimiento (dominio)")
class SeguimientoTest {

    @Test
    @DisplayName("seguir a otro miembro crea la relacion seguidor -> seguido")
    void seguir_a_otro_crea_la_relacion() {
        // Arrange
        UUID ana = UUID.randomUUID();
        UUID luis = UUID.randomUUID();

        // Act
        Seguimiento s = Seguimiento.nuevo(ana, luis);

        // Assert
        assertThat(s.seguidorId()).isEqualTo(ana);
        assertThat(s.seguidoId()).isEqualTo(luis);
        assertThat(s.creadoEn()).isNotNull();
    }

    @Test
    @DisplayName("un usuario no puede seguirse a si mismo: SEGUIMIENTO_PROPIO")
    void no_puede_seguirse_a_si_mismo() {
        // Arrange
        UUID ana = UUID.randomUUID();

        // Act + Assert
        assertThatThrownBy(() -> Seguimiento.nuevo(ana, ana))
                .isInstanceOf(ExcepcionNegocio.class)
                .extracting(e -> ((ExcepcionNegocio) e).codigo().codigo())
                .isEqualTo("SEGUIMIENTO_PROPIO");
    }

    @Test
    @DisplayName("la relacion no es simetrica: A -> B no es B -> A")
    void la_relacion_no_es_simetrica() {
        // Arrange
        UUID ana = UUID.randomUUID();
        UUID luis = UUID.randomUUID();

        // Act
        Seguimiento anaSigueALuis = Seguimiento.nuevo(ana, luis);

        // Assert
        assertThat(anaSigueALuis.seguidoId()).isNotEqualTo(ana);
    }
}
