package com.codefactory.devnet.administration.application;

import com.codefactory.devnet.administration.domain.Agrupacion;
import com.codefactory.devnet.administration.domain.CodigoErrorAdministracion;
import com.codefactory.devnet.administration.domain.NuevosPorPeriodo;
import com.codefactory.devnet.administration.domain.RangoFechas;
import com.codefactory.devnet.administration.domain.RepositorioMetricas;
import com.codefactory.devnet.administration.domain.ReporteActividad;
import com.codefactory.devnet.administration.domain.Totales;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("HU-19 ConsultarReporteActividad")
class ConsultarReporteActividadTest {

    private static final LocalDate DESDE = LocalDate.of(2026, 11, 2);
    private static final LocalDate HASTA = LocalDate.of(2026, 11, 15);

    private RepositorioMetricas metricas;
    private ConsultarReporteActividad consultar;

    @BeforeEach
    void preparar() {
        metricas = mock(RepositorioMetricas.class);
        consultar = new ConsultarReporteActividad(metricas);
    }

    @Test
    @DisplayName("con un rango valido muestra usuarios activos, publicaciones, comentarios y crecimiento")
    void rango_valido() {
        // Arrange
        when(metricas.usuariosActivos(any())).thenReturn(12);
        when(metricas.totales(any())).thenReturn(new Totales(3, 5, 20, 7));
        when(metricas.nuevosPorPeriodo(any(), any())).thenReturn(List.of(
                new NuevosPorPeriodo(DESDE, 4), new NuevosPorPeriodo(LocalDate.of(2026, 11, 9), 3)));

        // Act
        ReporteActividad r = consultar.ejecutar(DESDE, HASTA, "SEMANA");

        // Assert
        assertThat(r.usuariosActivos()).isEqualTo(12);
        assertThat(r.proyectos()).isEqualTo(3);
        assertThat(r.discusiones()).isEqualTo(5);
        assertThat(r.comentarios()).isEqualTo(20);
        assertThat(r.crecimiento()).extracting(ReporteActividad.Crecimiento::acumulado).containsExactly(4L, 7L);
    }

    @Test
    @DisplayName("consolida lo pendiente ANTES de leer")
    void consolida_antes_de_leer() {
        // Arrange
        when(metricas.totales(any())).thenReturn(new Totales(0, 0, 0, 0));

        // Act
        consultar.ejecutar(DESDE, HASTA, null);

        // Assert
        InOrder orden = inOrder(metricas);
        orden.verify(metricas).consolidarPendientes(new RangoFechas(DESDE, HASTA));
        orden.verify(metricas).totales(new RangoFechas(DESDE, HASTA));
    }

    @Test
    @DisplayName("sin agrupacion, agrupa por semana; con MES, por mes")
    void agrupacion() {
        when(metricas.totales(any())).thenReturn(new Totales(0, 0, 0, 0));

        assertThat(consultar.ejecutar(DESDE, HASTA, null).agrupacion()).isEqualTo(Agrupacion.SEMANA);
        assertThat(consultar.ejecutar(DESDE, HASTA, "mes").agrupacion()).isEqualTo(Agrupacion.MES);
    }

    @Test
    @DisplayName("sin actividad devuelve un estado vacio")
    void sin_actividad() {
        // Arrange
        when(metricas.totales(any())).thenReturn(new Totales(0, 0, 0, 0));

        // Act
        ReporteActividad r = consultar.ejecutar(DESDE, HASTA, "SEMANA");

        // Assert
        assertThat(r.sinActividad()).isTrue();
        assertThat(r.crecimiento()).isEmpty();
    }

    @Test
    @DisplayName("una fecha final anterior a la inicial se rechaza sin tocar la base")
    void rango_invalido_no_toca_la_base() {
        assertThatThrownBy(() -> consultar.ejecutar(HASTA, DESDE, "SEMANA"))
                .isInstanceOfSatisfying(ExcepcionNegocio.class, ex ->
                        assertThat(ex.codigo()).isEqualTo(CodigoErrorAdministracion.RANGO_FECHAS_INVALIDO));

        verifyNoInteractions(metricas);
    }

    @Test
    @DisplayName("una agrupacion desconocida se rechaza sin tocar la base")
    void agrupacion_invalida_no_toca_la_base() {
        assertThatThrownBy(() -> consultar.ejecutar(DESDE, HASTA, "ANIO"))
                .isInstanceOfSatisfying(ExcepcionNegocio.class, ex ->
                        assertThat(ex.codigo()).isEqualTo(CodigoErrorAdministracion.AGRUPACION_INVALIDA));

        verifyNoInteractions(metricas);
    }
}
