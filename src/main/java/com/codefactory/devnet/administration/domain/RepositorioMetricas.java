package com.codefactory.devnet.administration.domain;

import java.util.List;

/** Puerto de lectura de las metricas de la comunidad (HU-19). */
public interface RepositorioMetricas {

    /** Calcula los dias del rango que aun no estan consolidados o se calcularon antes de cerrar (V13). */
    void consolidarPendientes(RangoFechas rango);

    Totales totales(RangoFechas rango);

    /** Usuarios DISTINTOS con actividad en el rango; no es la suma de los activos de cada dia. */
    int usuariosActivos(RangoFechas rango);

    /** Del periodo mas antiguo al mas reciente. */
    List<NuevosPorPeriodo> nuevosPorPeriodo(RangoFechas rango, Agrupacion agrupacion);
}
