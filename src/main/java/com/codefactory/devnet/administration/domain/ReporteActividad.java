package com.codefactory.devnet.administration.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Reporte de actividad y crecimiento de la comunidad en un rango (HU-19).
 *
 * @param usuariosActivos usuarios distintos con actividad en el rango
 * @param crecimiento     registros nuevos por periodo; vacio si no hubo actividad
 */
public record ReporteActividad(
        RangoFechas rango,
        Agrupacion agrupacion,
        int usuariosActivos,
        long proyectos,
        long discusiones,
        long comentarios,
        long usuariosNuevos,
        List<Crecimiento> crecimiento
) {

    public ReporteActividad {
        crecimiento = List.copyOf(crecimiento);
    }

    /** @param acumulado registros nuevos desde el inicio del rango hasta el final de este periodo */
    public record Crecimiento(java.time.LocalDate periodo, long nuevos, long acumulado) {
    }

    /**
     * Arma el reporte. Si el rango no tuvo ninguna actividad, la serie va vacia: es el estado
     * vacio de la pantalla, y una lista de periodos en cero no le dice nada a nadie.
     */
    public static ReporteActividad de(RangoFechas rango, Agrupacion agrupacion, int usuariosActivos,
                                      Totales totales, List<NuevosPorPeriodo> porPeriodo) {
        boolean vacio = usuariosActivos == 0 && totales.proyectos() == 0 && totales.discusiones() == 0
                && totales.comentarios() == 0 && totales.usuariosNuevos() == 0;

        List<Crecimiento> serie = new ArrayList<>();
        if (!vacio) {
            long acumulado = 0;
            for (NuevosPorPeriodo p : porPeriodo) {
                acumulado += p.nuevos();
                serie.add(new Crecimiento(p.periodo(), p.nuevos(), acumulado));
            }
        }
        return new ReporteActividad(rango, agrupacion, usuariosActivos, totales.proyectos(),
                totales.discusiones(), totales.comentarios(), totales.usuariosNuevos(), serie);
    }

    /** Ni usuarios activos, ni publicaciones, ni comentarios, ni registros nuevos en el rango. */
    public boolean sinActividad() {
        return usuariosActivos == 0 && proyectos == 0 && discusiones == 0
                && comentarios == 0 && usuariosNuevos == 0;
    }
}
