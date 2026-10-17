package com.codefactory.devnet.interaction.api;

import com.codefactory.devnet.interaction.application.ConsultarReacciones;
import com.codefactory.devnet.interaction.application.QuitarReaccion;
import com.codefactory.devnet.interaction.application.Reaccionar;
import com.codefactory.devnet.interaction.domain.TipoReaccion;
import com.codefactory.devnet.shared.api.RespuestaError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * HU-14: reaccionar (dar "me gusta") a proyectos y discusiones.
 *
 * <p>Como los comentarios, cuelga de {@code /publicaciones/{id}}. La reaccion del usuario
 * es UN recurso ({@code .../reaccion}, en singular): se crea o reemplaza con PUT y se
 * quita con DELETE, ambos idempotentes. Eso hace imposible duplicarla desde la API,
 * ademas de la clave primaria compuesta de la base.</p>
 */
@RestController
@RequestMapping("/api/v1/publicaciones/{publicacionId}")
@Tag(name = "Reacciones", description = "Reacciones (me gusta) a proyectos y discusiones")
public class ReaccionController {

    private final Reaccionar reaccionar;
    private final QuitarReaccion quitar;
    private final ConsultarReacciones consultar;

    public ReaccionController(Reaccionar reaccionar, QuitarReaccion quitar, ConsultarReacciones consultar) {
        this.reaccionar = reaccionar;
        this.quitar = quitar;
        this.consultar = consultar;
    }

    @PutMapping("/reaccion")
    @PreAuthorize("hasAuthority('reaccion:crear')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Reaccionar a una publicacion",
            description = """
                    Deja **tu** reaccion en la publicacion (el usuario sale del token). Si no
                    tenias una, el contador sube en uno. Si ya tenias una, no se duplica: si
                    envias otro tipo se reemplaza, y si es el mismo no cambia nada.

                    El cuerpo es opcional; sin `tipo` se registra `ME_GUSTA`. Tipos:
                    `ME_GUSTA`, `UTIL`, `INNOVADOR`.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reaccion registrada; devuelve el total actualizado"),
            @ApiResponse(responseCode = "400", description = "Tipo de reaccion no valido",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/NoAutenticado"),
            @ApiResponse(responseCode = "403", description = "Tu rol no puede reaccionar",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "404", description = "La publicacion no existe o no esta publicada",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public RespuestaReaccion reaccionar(@PathVariable UUID publicacionId,
                                        @RequestBody(required = false) PeticionReaccion peticion) {
        String tipo = peticion == null ? null : peticion.tipo();
        Reaccionar.Resultado r = reaccionar.ejecutar(new Reaccionar.Comando(publicacionId, tipo));
        return new RespuestaReaccion(r.publicacionId(), r.tipo(), r.total());
    }

    @DeleteMapping("/reaccion")
    @PreAuthorize("hasAuthority('reaccion:crear')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Quitar mi reaccion",
            description = """
                    Quita **tu** reaccion; el contador baja en uno. Si no habias reaccionado,
                    no pasa nada y el contador no se toca (idempotente).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reaccion quitada; devuelve el total actualizado"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/NoAutenticado"),
            @ApiResponse(responseCode = "403", description = "Tu rol no puede reaccionar",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "404", description = "La publicacion no existe o no esta publicada",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public RespuestaTotal quitarReaccion(@PathVariable UUID publicacionId) {
        return new RespuestaTotal(publicacionId, quitar.ejecutar(publicacionId));
    }

    @GetMapping("/reacciones")
    @SecurityRequirements
    @Operation(
            summary = "Ver las reacciones de una publicacion",
            description = "Lectura publica, sin token. Total y desglose por tipo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Total y desglose por tipo"),
            @ApiResponse(responseCode = "404", description = "La publicacion no existe o no esta publicada",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public ConsultarReacciones.Resumen ver(@PathVariable UUID publicacionId) {
        return consultar.ejecutar(publicacionId);
    }

    // ------------------------------------------------------------------

    @Schema(name = "PeticionReaccion")
    public record PeticionReaccion(
            @Schema(example = "ME_GUSTA", allowableValues = {"ME_GUSTA", "UTIL", "INNOVADOR"})
            String tipo
    ) { }

    @Schema(name = "RespuestaReaccion")
    public record RespuestaReaccion(UUID publicacionId, TipoReaccion tipo, int total) { }

    @Schema(name = "RespuestaTotalReacciones")
    public record RespuestaTotal(UUID publicacionId, int total) { }
}
