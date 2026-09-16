package com.codefactory.devnet.project.api;

import com.codefactory.devnet.project.application.ListarProyectosDeAutor;
import com.codefactory.devnet.project.application.OcultarPublicacion;
import com.codefactory.devnet.project.application.PublicarProyecto;
import com.codefactory.devnet.project.domain.EstadoProyecto;
import com.codefactory.devnet.shared.api.PaginaKeyset;
import com.codefactory.devnet.shared.api.RespuestaError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * HU-07 (AB#17): publicacion y listado de proyectos.
 */
@RestController
@RequestMapping("/api/v1/proyectos")
@Tag(name = "Proyectos", description = "Publicacion de proyectos y repositorios")
public class ProyectoController {

    private final PublicarProyecto publicar;
    private final ListarProyectosDeAutor listar;
    private final OcultarPublicacion ocultar;

    public ProyectoController(PublicarProyecto publicar,
                              ListarProyectosDeAutor listar,
                              OcultarPublicacion ocultar) {
        this.publicar = publicar;
        this.listar = listar;
        this.ocultar = ocultar;
    }

    /**
     * HU-06 y HU-07 se encuentran en esta anotacion: publicar exige el permiso
     * {@code publicacion:crear}, que concede el rol DESARROLLADOR. Un VISITANTE
     * autenticado recibe 403 y el intento queda registrado en auditoria.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('publicacion:crear')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Publicar un proyecto",
            description = """
                    Publica un proyecto con titulo, descripcion, stack tecnologico y,
                    opcionalmente, enlace a repositorio.

                    El autor **siempre** se toma del token. Si el cuerpo trae un
                    `autorId` distinto, la peticion se rechaza con 403: no se ignora en
                    silencio, para que nadie crea estar publicando en nombre de otro.

                    Publicar con repositorio enlazado acredita +5 de reputacion.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Proyecto publicado"),
            @ApiResponse(responseCode = "400", description = "Faltan titulo o descripcion, o no cumplen el formato",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/NoAutenticado"),
            @ApiResponse(responseCode = "403", description = "Sin permiso, o intento de publicar a nombre ajeno",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "409", description = "El repositorio ya esta enlazado a otro proyecto",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "422", description = "Perfil incompleto o tecnologias no aprobadas",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public ResponseEntity<RespuestaProyectoPublicado> publicar(
            @Valid @RequestBody PeticionPublicarProyecto peticion) {

        PublicarProyecto.Resultado resultado = publicar.ejecutar(new PublicarProyecto.Comando(
                peticion.autorId(),
                peticion.titulo(),
                peticion.descripcion(),
                peticion.resumen(),
                peticion.tecnologias(),
                peticion.urlRepositorio(),
                peticion.estadoProyecto(),
                peticion.licencia(),
                peticion.urlDemo(),
                Boolean.TRUE.equals(peticion.buscaColaboradores())));

        // 201 con Location: el cliente sabe donde quedo sin tener que componer la URL.
        return ResponseEntity
                .created(URI.create("/api/v1/proyectos/" + resultado.id()))
                .body(new RespuestaProyectoPublicado(
                        resultado.id(), resultado.titulo(), resultado.publicadoEn()));
    }

    /**
     * HU-07, criterio 1: "visible en su lista".
     *
     * <p>Lectura publica, sin token: el perfil de un desarrollador es su carta de
     * presentacion.</p>
     */
    @GetMapping("/autor/{autorId}")
    @SecurityRequirements
    @Operation(
            summary = "Listar los proyectos de un autor",
            description = """
                    Del mas reciente al mas antiguo, paginado por cursor.

                    Para la pagina siguiente, devuelve `cursorSiguiente` tal cual en el
                    parametro `cursor`. No lo interpretes: es opaco a proposito.
                    """)
    public PaginaKeyset<ListarProyectosDeAutor.ProyectoListado> deAutor(
            @PathVariable UUID autorId,
            @Parameter(description = "Cursor devuelto por la pagina anterior")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "Tamano de pagina (1-50, por defecto 20)")
            @RequestParam(required = false) Integer limite) {

        return listar.ejecutar(autorId, cursor, limite);
    }

    /**
     * HU-06, criterio 2: la accion reservada al rol MODERADOR.
     *
     * <p>Modelada como sub-recurso de accion y no como {@code PATCH} del campo
     * {@code estado} (ADR-002): asi la autorizacion queda anclada a un endpoint
     * concreto, y ningun {@code PATCH} abierto puede provocar transiciones que la
     * maquina de estados no admite.</p>
     */
    @PostMapping("/{proyectoId}/ocultamiento")
    @PreAuthorize("hasAuthority('publicacion:moderar')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Ocultar un proyecto (solo moderacion)",
            description = """
                    Retira el proyecto de la vista publica. Exige el permiso
                    `publicacion:moderar`, que solo tienen MODERADOR y ADMIN.

                    Un usuario sin ese permiso recibe **403** y el intento queda
                    registrado en la tabla `auditoria` con su identificador, la ruta y
                    el `traceId` de la peticion.

                    El motivo es obligatorio y de al menos 20 caracteres: no hay
                    resolucion silenciosa. Queda en `historial_estado_publicacion`.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Proyecto ocultado"),
            @ApiResponse(responseCode = "400", description = "Motivo ausente o demasiado corto",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "403", description = "Sin el permiso publicacion:moderar",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "404", description = "El proyecto no existe",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public ResponseEntity<Void> ocultar(@PathVariable UUID proyectoId,
                                        @Valid @RequestBody PeticionOcultar peticion) {
        ocultar.ejecutar(proyectoId, peticion.motivo());
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------

    @Schema(name = "PeticionOcultar")
    public record PeticionOcultar(
            @NotBlank(message = "el motivo es obligatorio")
            @Size(min = 20, message = "el motivo debe tener al menos 20 caracteres")
            @Schema(example = "Contenido duplicado de otro proyecto ya publicado en la plataforma.")
            String motivo
    ) { }

    @Schema(name = "PeticionPublicarProyecto")
    public record PeticionPublicarProyecto(

            @Schema(description = "Opcional. Si viene y no coincide con el autenticado, se rechaza con 403.",
                    nullable = true)
            UUID autorId,

            @NotBlank(message = "el titulo es obligatorio")
            @Size(min = 5, max = 200, message = "el titulo debe tener entre 5 y 200 caracteres")
            @Schema(example = "Motor de plantillas en Java")
            String titulo,

            @NotBlank(message = "la descripcion es obligatoria")
            @Size(min = 20, message = "la descripcion debe tener al menos 20 caracteres")
            @Schema(example = "Un motor de plantillas minimalista escrito en Java 17 con soporte para expresiones.")
            String descripcion,

            @Schema(description = "Opcional. Si falta, se deriva de la descripcion.", nullable = true)
            String resumen,

            @NotEmpty(message = "debes indicar al menos una tecnologia")
            @Size(min = 1, max = 5, message = "entre 1 y 5 tecnologias")
            @Schema(description = "Identificadores del catalogo de tecnologias", example = "[1, 7]")
            Set<Short> tecnologias,

            @Schema(description = "Debe ser https", example = "https://github.com/usuario/motor-plantillas", nullable = true)
            String urlRepositorio,

            @Schema(example = "EN_DESARROLLO", nullable = true)
            EstadoProyecto estadoProyecto,

            @Schema(example = "MIT", nullable = true)
            String licencia,

            @Schema(nullable = true)
            String urlDemo,

            @Schema(defaultValue = "false", nullable = true)
            Boolean buscaColaboradores
    ) { }

    @Schema(name = "RespuestaProyectoPublicado")
    public record RespuestaProyectoPublicado(UUID id, String titulo, Instant publicadoEn) { }
}