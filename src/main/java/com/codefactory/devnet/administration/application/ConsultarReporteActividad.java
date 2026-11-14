package com.codefactory.devnet.administration.application;

import com.codefactory.devnet.administration.domain.Agrupacion;
import com.codefactory.devnet.administration.domain.RangoFechas;
import com.codefactory.devnet.administration.domain.RepositorioMetricas;
import com.codefactory.devnet.administration.domain.ReporteActividad;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * HU-19: reporte de actividad y crecimiento de la comunidad. Solo administradores (la
 * autorizacion vive en el controlador, con {@code metricas:consultar}).
 *
 * <p>Valida primero, consolida despues, lee al final. Un rango invalido no debe llegar a la base,
 * y la lectura va tras consolidar porque {@code metricas_diarias} se llena bajo demanda (V13):
 * este caso de uso escribe, por eso no es {@code readOnly}. Lo que escribe es un calculo
 * idempotente y determinista, no un cambio de estado de negocio.</p>
 */
@Service
public class ConsultarReporteActividad {

    private final RepositorioMetricas metricas;

    public ConsultarReporteActividad(RepositorioMetricas metricas) {
        this.metricas = metricas;
    }

    @Transactional
    public ReporteActividad ejecutar(LocalDate desde, LocalDate hasta, String agrupacion) {
        RangoFechas rango = RangoFechas.paraReporte(desde, hasta);
        Agrupacion modo = Agrupacion.de(agrupacion);

        metricas.consolidarPendientes(rango);

        return ReporteActividad.de(rango, modo,
                metricas.usuariosActivos(rango),
                metricas.totales(rango),
                metricas.nuevosPorPeriodo(rango, modo));
    }
}
