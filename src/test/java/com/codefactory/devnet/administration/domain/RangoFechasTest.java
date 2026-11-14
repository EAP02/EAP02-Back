package com.codefactory.devnet.administration.domain;

import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RangoFechas (dominio)")
class RangoFechasTest {

    @Test
    @DisplayName("un rango con inicio antes del final es valido")
    void rango_valido() {
        // Act
        RangoFechas rango = new RangoFechas(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 13));

        // Assert
        assertThat(rango.desde()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(rango.hasta()).isEqualTo(LocalDate.of(2026, 11, 13));
    }

    @Test
    @DisplayName("un solo dia (inicio igual al final) es valido")
    void un_solo_dia() {
        LocalDate dia = LocalDate.of(2026, 11, 1);

        assertThat(new RangoFechas(dia, dia)).isNotNull();
    }

    @Test
    @DisplayName("la fecha final anterior a la inicial se rechaza con RANGO_FECHAS_INVALIDO")
    void final_anterior_a_inicial() {
        // Arrange
        LocalDate desde = LocalDate.of(2026, 11, 13);
        LocalDate hasta = LocalDate.of(2026, 11, 1);

        // Act + Assert
        assertThatThrownBy(() -> new RangoFechas(desde, hasta))
                .isInstanceOfSatisfying(ExcepcionNegocio.class, ex ->
                        assertThat(ex.codigo()).isEqualTo(CodigoErrorAdministracion.RANGO_FECHAS_INVALIDO));
    }

    @Test
    @DisplayName("un rango abierto (sin inicio o sin final) es valido y no tiene limite en ese lado")
    void rango_abierto() {
        RangoFechas sinInicio = new RangoFechas(null, LocalDate.of(2026, 11, 1));
        RangoFechas sinFinal = new RangoFechas(LocalDate.of(2026, 11, 1), null);

        assertThat(sinInicio.inicio()).isNull();
        assertThat(sinInicio.finExclusivo()).isNotNull();
        assertThat(sinFinal.inicio()).isNotNull();
        assertThat(sinFinal.finExclusivo()).isNull();
    }

    @Test
    @DisplayName("los dias se cuentan en hora de Colombia: el 1 de noviembre empieza a las 05:00 UTC")
    void limites_en_hora_de_colombia() {
        // Arrange
        RangoFechas rango = new RangoFechas(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1));

        // Assert: Bogota es UTC-5 todo el ano. El final es exclusivo: el dia siguiente a las 00:00.
        assertThat(rango.inicio()).isEqualTo(Instant.parse("2026-11-01T05:00:00Z"));
        assertThat(rango.finExclusivo()).isEqualTo(Instant.parse("2026-11-02T05:00:00Z"));
    }
}
