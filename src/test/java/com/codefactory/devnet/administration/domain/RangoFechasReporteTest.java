package com.codefactory.devnet.administration.domain;

import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("HU-19 RangoFechas.paraReporte (dominio)")
class RangoFechasReporteTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 11, 1);

    @Test
    @DisplayName("un rango valido se acepta")
    void rango_valido() {
        RangoFechas rango = RangoFechas.paraReporte(INICIO, LocalDate.of(2026, 11, 13));

        assertThat(rango.desde()).isEqualTo(INICIO);
        assertThat(rango.hasta()).isEqualTo(LocalDate.of(2026, 11, 13));
    }

    @Test
    @DisplayName("un solo dia es un rango valido")
    void un_dia() {
        assertThat(RangoFechas.paraReporte(INICIO, INICIO)).isNotNull();
    }

    @Test
    @DisplayName("la fecha final anterior a la inicial se rechaza con RANGO_FECHAS_INVALIDO")
    void final_anterior() {
        assertThatThrownBy(() -> RangoFechas.paraReporte(LocalDate.of(2026, 11, 13), INICIO))
                .isInstanceOfSatisfying(ExcepcionNegocio.class, ex ->
                        assertThat(ex.codigo()).isEqualTo(CodigoErrorAdministracion.RANGO_FECHAS_INVALIDO));
    }

    @Test
    @DisplayName("sin fecha inicial o sin final se rechaza con RANGO_FECHAS_REQUERIDO")
    void fechas_obligatorias() {
        assertThatThrownBy(() -> RangoFechas.paraReporte(null, INICIO))
                .isInstanceOfSatisfying(ExcepcionNegocio.class, ex ->
                        assertThat(ex.codigo()).isEqualTo(CodigoErrorAdministracion.RANGO_FECHAS_REQUERIDO));
        assertThatThrownBy(() -> RangoFechas.paraReporte(INICIO, null))
                .isInstanceOfSatisfying(ExcepcionNegocio.class, ex ->
                        assertThat(ex.codigo()).isEqualTo(CodigoErrorAdministracion.RANGO_FECHAS_REQUERIDO));
    }

    @Test
    @DisplayName("92 dias es el maximo: con 92 se acepta y con 93 se rechaza")
    void tope_de_dias() {
        // 1 de noviembre + 91 dias = 92 dias contando ambos extremos
        assertThat(RangoFechas.paraReporte(INICIO, INICIO.plusDays(91))).isNotNull();

        assertThatThrownBy(() -> RangoFechas.paraReporte(INICIO, INICIO.plusDays(92)))
                .isInstanceOfSatisfying(ExcepcionNegocio.class, ex ->
                        assertThat(ex.codigo()).isEqualTo(CodigoErrorAdministracion.RANGO_FECHAS_DEMASIADO_AMPLIO));
    }
}
