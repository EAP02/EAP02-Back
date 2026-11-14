package com.codefactory.devnet.administration.api;

import com.codefactory.devnet.administration.application.ConsultarAuditoria;
import com.codefactory.devnet.administration.domain.Pagina;
import com.codefactory.devnet.shared.api.RespuestaError;
import com.fasterxml.jackson.annotation.JsonRawValue;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * HU-20: registro de auditoria. Solo lectura y solo administradores.
 *
 * <p>No hay POST, PUT ni DELETE: el registro lo escribe el sistema (la aplicacion y los
 * triggers de V10) y nadie lo edita por la API. Que un administrador pudiera borrar su
 * propio rastro deshace el proposito del registro.</p>
 */
@RestController
@RequestMapping("/api/v1/auditoria")
@Tag(name = "Auditoria", description = "Registro de acciones sensibles (solo administradores)")
public class AuditoriaController {

    private final ConsultarAuditoria consultar;

    public AuditoriaController(ConsultarAuditoria consultar) {
        this.consultar = consultar;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('auditoria:consultar')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Consultar el registro de auditoria",
            description = """
                    Lista, del mas reciente al mas antiguo, las acciones sensibles de la
                    plataforma: inicios de sesion (exitosos y fallidos), cambios de rol y de
                    permisos, eliminaciones de contenido, accesos denegados.

                    Cada registro dice **quien** (`actor`), **que** (`accion`), **cuando**
                    (`fecha`) y **sobre que objeto** (`objeto`). Nunca contiene contrasenas
                    ni tokens.

                    Todos los filtros son opcionales y se combinan. Las fechas son dias de
                    Colombia (America/Bogota), ambos extremos incluidos. Si no hay
                    coincidencias, `contenido` viene vacio y `total` es 0.

                    Requiere el permiso `auditoria:consultar` (solo ADMIN, con MFA inscrito).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina de registros (puede estar vacia)"),
            @ApiResponse(responseCode = "400",
                    description = "**RANGO_FECHAS_INVALIDO**, **AUDITORIA_OPERACION_INVALIDA**, "
                            + "**PAGINACION_INVALIDA** o un parametro con formato incorrecto",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/NoAutenticado"),
            @ApiResponse(responseCode = "403",
                    description = "Tu rol no puede consultar la auditoria. El intento queda registrado.",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public PaginaResponse consultar(
            @Parameter(description = "INSERT, UPDATE, DELETE, ACCESO_DENEGADO, INICIO_SESION, "
                    + "INICIO_SESION_FALLIDO, CUENTA_BLOQUEADA, REFRESCO_ROTADO, REFRESCO_REUSADO, "
                    + "CIERRE_SESION, MENSAJE_ENVIADO")
            @RequestParam(required = false) String operacion,
            @Parameter(description = "Id de quien hizo la accion")
            @RequestParam(required = false) UUID actorId,
            @Parameter(description = "Objeto afectado: usuario, usuario_rol, comentario, seguridad...")
            @RequestParam(required = false) String tabla,
            @Parameter(description = "Desde este dia, inclusive (AAAA-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @Parameter(description = "Hasta este dia, inclusive (AAAA-MM-DD)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @Parameter(description = "Numero de pagina, desde 0")
            @RequestParam(defaultValue = "0") int pagina,
            @Parameter(description = "Registros por pagina, de 1 a 100")
            @RequestParam(defaultValue = "20") int tamano) {

        Pagina<ConsultarAuditoria.Registro> resultado =
                consultar.ejecutar(operacion, actorId, tabla, desde, hasta, pagina, tamano);
        return PaginaResponse.de(resultado);
    }

    // ------------------------------------------------------------------
    // Respuesta
    // ------------------------------------------------------------------

    public record PaginaResponse(List<RegistroResponse> contenido, int pagina, int tamano,
                                 long total, int totalPaginas) {

        static PaginaResponse de(Pagina<ConsultarAuditoria.Registro> p) {
            return new PaginaResponse(
                    p.contenido().stream().map(RegistroResponse::de).toList(),
                    p.pagina(), p.tamano(), p.total(), p.totalPaginas());
        }
    }

    public record RegistroResponse(
            long id,
            Instant fecha,
            String accion,
            ObjetoResponse objeto,
            ActorResponse actor,
            String traceId,
            String ip,
            String usuarioBd,
            @JsonRawValue @Schema(type = "object", nullable = true,
                    description = "Estado previo del objeto (solo columnas permitidas)")
            String datosAntes,
            @JsonRawValue @Schema(type = "object", nullable = true,
                    description = "Estado posterior del objeto (solo columnas permitidas)")
            String datosDespues) {

        static RegistroResponse de(ConsultarAuditoria.Registro r) {
            ActorResponse actor = r.actorId() == null
                    ? null : new ActorResponse(r.actorId(), r.actorNombre());
            return new RegistroResponse(r.id(), r.fecha(), r.accion(),
                    new ObjetoResponse(r.tabla(), r.registroId()), actor,
                    r.traceId(), r.ip(), r.usuarioBd(), r.datosAntes(), r.datosDespues());
        }
    }

    /** Sobre que se actuo. En un inicio de sesion fallido, {@code registroId} es el correo intentado. */
    public record ObjetoResponse(String tabla, String registroId) {
    }

    /** Quien actuo. Es {@code null} en peticiones anonimas y en cambios hechos por consola. */
    public record ActorResponse(UUID id, String nombreUsuario) {
    }
}
