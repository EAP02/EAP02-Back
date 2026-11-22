package com.codefactory.devnet.administration.api;

import com.codefactory.devnet.administration.application.ConsultarReporteActividad;
import com.codefactory.devnet.administration.domain.ReporteActividad;
import com.codefactory.devnet.shared.api.RespuestaError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * HU-19: reporte de actividad y crecimiento. Solo lectura y solo administradores.
 *
 * <p>Es un GET aunque la primera consulta de un rango calcule y guarde las metricas de los
 * dias pendientes: ese calculo es una cache idempotente de datos derivados, no un cambio de
 * estado que el cliente pueda observar (ver V13 y el ADR-009).</p>
 */
@RestController
@RequestMapping("/api/v1/reportes")
@Tag(name = "Reportes", description = "Salud de la comunidad (solo administradores)")
public class ReporteActividadController {

    private final ConsultarReporteActividad consultar;

    public ReporteActividadController(ConsultarReporteActividad consultar) {
        this.consultar = consultar;
    }

    @GetMapping("/actividad")
    @PreAuthorize("hasAuthority('metricas:consultar')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Reporte de actividad y crecimiento de la comunidad",
            description = """
                    Para un rango de fechas: los **usuarios activos**, los **proyectos**,
                    **discusiones** y **comentarios** publicados, y el **crecimiento de nuevos
                    registros** agrupado por semana o por mes.

                    Un usuario activo es quien inicio o renovo sesion, publico, comento,
                    reacciono o envio un mensaje. Se cuenta **una vez** aunque haya estado activo
                    varios dias del rango.

                    Las fechas son dias de Colombia (America/Bogota), ambos extremos incluidos, y
                    son **obligatorias**. El rango maximo es de 92 dias. Las semanas van de lunes
                    a domingo; la primera y la ultima pueden quedar cortadas por el rango.

                    Si no hubo ninguna actividad, `sinActividad` es `true`, los totales son 0 y
                    `crecimiento` viene vacio.

                    Requiere el permiso `metricas:consultar` (solo ADMIN, con MFA inscrito).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reporte (puede ser un estado vacio)"),
            @ApiResponse(responseCode = "400",
                    description = "**RANGO_FECHAS_INVALIDO** (la fecha final es anterior a la inicial), "
                            + "**RANGO_FECHAS_REQUERIDO**, **RANGO_FECHAS_DEMASIADO_AMPLIO**, "
                            + "**AGRUPACION_INVALIDA** o una fecha con formato incorrecto",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/NoAutenticado"),
            @ApiResponse(responseCode = "403",
                    description = "Tu rol no puede ver el reporte. El intento queda registrado.",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public ReporteResponse actividad(
            @Parameter(description = "Desde este dia, inclusive (AAAA-MM-DD)", required = true)
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @Parameter(description = "Hasta este dia, inclusive (AAAA-MM-DD)", required = true)
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @Parameter(description = "SEMANA (por defecto) o MES")
            @RequestParam(required = false) String agrupacion) {

        return ReporteResponse.de(consultar.ejecutar(desde, hasta, agrupacion));
    }

    // ------------------------------------------------------------------
    // Respuesta
    // ------------------------------------------------------------------

    public record ReporteResponse(
            LocalDate desde,
            LocalDate hasta,
            String agrupacion,
            boolean sinActividad,
            int usuariosActivos,
            long proyectos,
            long discusiones,
            long comentarios,
            long nuevosUsuarios,
            List<PuntoResponse> crecimiento) {

        static ReporteResponse de(ReporteActividad r) {
            return new ReporteResponse(
                    r.rango().desde(), r.rango().hasta(), r.agrupacion().name(),
                    r.sinActividad(), r.usuariosActivos(), r.proyectos(), r.discusiones(),
                    r.comentarios(), r.usuariosNuevos(),
                    r.crecimiento().stream()
                            .map(c -> new PuntoResponse(c.periodo(), c.nuevos(), c.acumulado()))
                            .toList());
        }
    }

    /** @param periodo primer dia del periodo; @param acumulado nuevos desde el inicio del rango */
    public record PuntoResponse(LocalDate periodo, long nuevos, long acumulado) {
    }
}
