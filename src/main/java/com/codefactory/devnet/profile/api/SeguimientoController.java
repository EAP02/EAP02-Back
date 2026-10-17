package com.codefactory.devnet.profile.api;

import com.codefactory.devnet.profile.application.DejarDeSeguirUsuario;
import com.codefactory.devnet.profile.application.ListarSeguimientos;
import com.codefactory.devnet.profile.application.SeguirUsuario;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * HU-15: seguir a otros miembros.
 *
 * <p>Cuelga de {@code /perfiles/{id}}: el id de un perfil es el de su usuario. El
 * seguimiento es UN recurso ({@code .../seguimiento}): PUT y DELETE sobre el perfil
 * del seguido, ambos idempotentes, y quien sigue sale del token. Las listas
 * ({@code seguidos}, {@code seguidores}) caen bajo el {@code GET /api/v1/perfiles/**}
 * publico que ya existe.</p>
 */
@RestController
@RequestMapping("/api/v1/perfiles/{id}")
@Tag(name = "Seguimiento", description = "Seguir a otros miembros")
public class SeguimientoController {

    private final SeguirUsuario seguir;
    private final DejarDeSeguirUsuario dejarDeSeguir;
    private final ListarSeguimientos listar;

    public SeguimientoController(SeguirUsuario seguir,
                                 DejarDeSeguirUsuario dejarDeSeguir,
                                 ListarSeguimientos listar) {
        this.seguir = seguir;
        this.dejarDeSeguir = dejarDeSeguir;
        this.listar = listar;
    }

    @PutMapping("/seguimiento")
    @PreAuthorize("hasAuthority('usuario:seguir')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Seguir a un miembro",
            description = """
                    **Tu** sigues al miembro cuyo id va en la ruta (tu id sale del token). Pasa
                    a aparecer en tu lista de seguidos. Seguir a quien ya sigues no duplica
                    nada.

                    No puedes seguirte a ti mismo (**422 SEGUIMIENTO_PROPIO**).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Ahora lo sigues"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/NoAutenticado"),
            @ApiResponse(responseCode = "403", description = "Tu rol no puede seguir",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "404", description = "El miembro no existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "422", description = "Intentas seguirte a ti mismo",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public ResponseEntity<Void> seguir(@PathVariable UUID id) {
        seguir.ejecutar(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/seguimiento")
    @PreAuthorize("hasAuthority('usuario:seguir')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Dejar de seguir a un miembro",
            description = """
                    Deja de aparecer en tu lista de seguidos. Si no lo seguias, no pasa nada
                    (idempotente).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Ya no lo sigues"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/NoAutenticado"),
            @ApiResponse(responseCode = "403", description = "Tu rol no puede seguir",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public ResponseEntity<Void> dejarDeSeguir(@PathVariable UUID id) {
        dejarDeSeguir.ejecutar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/seguidos")
    @SecurityRequirements
    @Operation(
            summary = "A quienes sigue un miembro",
            description = "Lectura publica, sin token. Del mas reciente al mas antiguo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de seguidos"),
            @ApiResponse(responseCode = "404", description = "El miembro no existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public List<ListarSeguimientos.Miembro> seguidos(@PathVariable UUID id) {
        return listar.seguidos(id);
    }

    @GetMapping("/seguidores")
    @SecurityRequirements
    @Operation(
            summary = "Quienes siguen a un miembro",
            description = "Lectura publica, sin token. Del mas reciente al mas antiguo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de seguidores"),
            @ApiResponse(responseCode = "404", description = "El miembro no existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public List<ListarSeguimientos.Miembro> seguidores(@PathVariable UUID id) {
        return listar.seguidores(id);
    }
}
