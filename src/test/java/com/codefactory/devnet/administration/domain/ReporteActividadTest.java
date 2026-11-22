package com.codefactory.devnet.administration.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HU-19 ReporteActividad (dominio)")
class ReporteActividadTest {

    private static final RangoFechas RANGO = new RangoFechas(LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 15));

    @Test
    @DisplayName("el crecimiento muestra los nuevos de cada periodo y el acumulado")
    void crecimiento_con_acumulado() {
        // Arrange
        List<NuevosPorPeriodo> semanas = List.of(
                new NuevosPorPeriodo(LocalDate.of(2026, 11, 2), 3),
                new NuevosPorPeriodo(LocalDate.of(2026, 11, 9), 0),
                new NuevosPorPeriodo(LocalDate.of(2026, 11, 16), 4));

        // Act
        ReporteActividad r = ReporteActividad.de(RANGO, Agrupacion.SEMANA, 5,
                new Totales(1, 2, 3, 7), semanas);

        // Assert
        assertThat(r.crecimiento()).extracting(ReporteActividad.Crecimiento::acumulado)
                .containsExactly(3L, 3L, 7L);
        assertThat(r.crecimiento()).extracting(ReporteActividad.Crecimiento::nuevos)
                .containsExactly(3L, 0L, 4L);
        assertThat(r.sinActividad()).isFalse();
    }

    @Test
    @DisplayName("sin ninguna actividad es un estado vacio: totales en 0 y serie vacia")
    void estado_vacio() {
        // Arrange: la base devuelve periodos en cero para cada dia consolidado
        List<NuevosPorPeriodo> ceros = List.of(new NuevosPorPeriodo(LocalDate.of(2026, 11, 2), 0));

        // Act
        ReporteActividad r = ReporteActividad.de(RANGO, Agrupacion.SEMANA, 0, new Totales(0, 0, 0, 0), ceros);

        // Assert
        assertThat(r.sinActividad()).isTrue();
        assertThat(r.crecimiento()).isEmpty();
        assertThat(r.usuariosActivos()).isZero();
    }

    @Test
    @DisplayName("con un solo comentario ya hay actividad")
    void un_comentario_basta() {
        ReporteActividad r = ReporteActividad.de(RANGO, Agrupacion.MES, 0, new Totales(0, 0, 1, 0), List.of());

        assertThat(r.sinActividad()).isFalse();
    }

    @Test
    @DisplayName("con actividad pero sin registros nuevos la serie queda como la devuelva la base")
    void actividad_sin_registros() {
        ReporteActividad r = ReporteActividad.de(RANGO, Agrupacion.SEMANA, 2, new Totales(0, 1, 0, 0),
                List.of(new NuevosPorPeriodo(LocalDate.of(2026, 11, 2), 0)));

        assertThat(r.crecimiento()).hasSize(1);
        assertThat(r.crecimiento().get(0).acumulado()).isZero();
    }
}
