package com.codefactory.devnet.interaction.api;

import com.codefactory.devnet.interaction.application.EliminarComentario;
import com.codefactory.devnet.interaction.application.ListarComentarios;
import com.codefactory.devnet.interaction.application.PublicarComentario;
import com.codefactory.devnet.shared.api.IpCliente;
import com.codefactory.devnet.shared.api.RespuestaError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * HU-13: comentar en proyectos y discusiones.
 *
 * <p>Se cuelga de {@code /publicaciones/{id}} y no de {@code /proyectos} ni
 * {@code /discusiones}: en el modelo de datos ambos son una publicacion, y asi un solo
 * endpoint sirve para los dos sin que {@code interaction} dependa de {@code project} ni
 * de {@code discussion}. Es el mismo recurso que ADR-002 ya preve para los reportes
 * ({@code /publicaciones/{id}/reportes}).</p>
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Comentarios", description = "Comentarios en proyectos y discusiones")
public class ComentarioController {

    private final PublicarComentario publicar;
    private final ListarComentarios listar;
    private final EliminarComentario eliminar;

    public ComentarioController(PublicarComentario publicar,
                                ListarComentarios listar,
                                EliminarComentario eliminar) {
        this.publicar = publicar;
        this.listar = listar;
        this.eliminar = eliminar;
    }

    @PostMapping("/publicaciones/{publicacionId}/comentarios")
    @PreAuthorize("hasAuthority('comentario:crear')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Comentar un proyecto o una discusion",
            description = """
                    Publica un comentario sobre una publicacion (proyecto o discusion). El
                    autor **siempre** sale del token: el cuerpo no lo admite.

                    Un comentario vacio, o solo con espacios, se rechaza con **400**.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Comentario publicado"),
            @ApiResponse(responseCode = "400", description = "Comentario vacio o demasiado largo",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/NoAutenticado"),
            @ApiResponse(responseCode = "403", description = "Tu rol no puede comentar",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "404", description = "La publicacion no existe o no esta publicada",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public ResponseEntity<RespuestaComentario> comentar(@PathVariable UUID publicacionId,
                                                        @Valid @RequestBody PeticionComentar peticion) {
        PublicarComentario.Resultado r = publicar.ejecutar(
                new PublicarComentario.Comando(publicacionId, peticion.contenido()));

        return ResponseEntity
                .created(URI.create("/api/v1/publicaciones/" + publicacionId + "/comentarios"))
                .body(new RespuestaComentario(r.id(), r.publicacionId(), r.autorId(), r.contenido(), r.creadoEn()));
    }

    @GetMapping("/publicaciones/{publicacionId}/comentarios")
    @SecurityRequirements
    @Operation(
            summary = "Listar los comentarios de una publicacion",
            description = """
                    Lectura publica, sin token. Del mas antiguo al mas reciente. Un
                    comentario eliminado deja de aparecer.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comentarios visibles"),
            @ApiResponse(responseCode = "404", description = "La publicacion no existe o no esta publicada",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public List<ListarComentarios.ComentarioVisible> listar(@PathVariable UUID publicacionId) {
        return listar.ejecutar(publicacionId);
    }

    /**
     * Dos capas de autorizacion, como en el resto de la API: {@code @PreAuthorize}
     * filtra por rol y {@code PoliticaComentario} decide si este actor puede eliminar
     * <i>este</i> comentario.
     */
    @DeleteMapping("/comentarios/{comentarioId}")
    @PreAuthorize("hasAnyAuthority('comentario:editar', 'comentario:eliminar-ajeno')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Eliminar un comentario",
            description = """
                    Lo elimina **su autor**, o quien tenga el permiso
                    `comentario:eliminar-ajeno` (ADMIN). Cualquier otro recibe **403** y el
                    intento queda registrado en auditoria. El comentario deja de
                    mostrarse; la fila se conserva con estado `ELIMINADO`.

                    Un comentario ya eliminado responde **404**.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Comentario eliminado"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/NoAutenticado"),
            @ApiResponse(responseCode = "403", description = "No eres el autor ni administrador",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "404", description = "No existe o ya fue eliminado",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public ResponseEntity<Void> eliminar(@PathVariable UUID comentarioId, HttpServletRequest http) {
        eliminar.ejecutar(new EliminarComentario.Comando(comentarioId, IpCliente.de(http)));
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------

    /**
     * La validacion de forma se repite aqui y en el dominio a proposito: esta rechaza
     * pronto lo evidente; el dominio es la regla que ninguna otra ruta puede saltarse.
     */
    @Schema(name = "PeticionComentar")
    public record PeticionComentar(
            @NotBlank(message = "el comentario no puede estar vacio")
            @Size(max = 5000, message = "el comentario no puede superar 5000 caracteres")
            @Schema(example = "Buen aporte, probe tu solucion y me funciono.")
            String contenido
    ) { }

    @Schema(name = "RespuestaComentario")
    public record RespuestaComentario(UUID id, UUID publicacionId, UUID autorId,
                                      String contenido, Instant creadoEn) { }
}
