package com.codefactory.devnet.project.api;

import com.codefactory.devnet.project.application.ArchivarProyecto;
import com.codefactory.devnet.project.application.BuscarProyectos;
import com.codefactory.devnet.project.application.EditarProyecto;
import com.codefactory.devnet.project.application.ListarProyectosDeAutor;
import com.codefactory.devnet.project.application.OcultarPublicacion;
import com.codefactory.devnet.project.application.PublicarProyecto;
import com.codefactory.devnet.project.domain.EstadoProyecto;
import com.codefactory.devnet.shared.api.IpCliente;
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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
 * HU-08 (AB#18): edicion y eliminacion de proyectos.
 * HU-09 (AB#19): exploracion y busqueda de proyectos.
 */
@RestController
@RequestMapping("/api/v1/proyectos")
@Tag(name = "Proyectos", description = "Publicacion de proyectos y repositorios")
public class ProyectoController {

    private final PublicarProyecto publicar;
    private final ListarProyectosDeAutor listar;
    private final OcultarPublicacion ocultar;
    private final EditarProyecto editar;
    private final ArchivarProyecto archivar;
    private final BuscarProyectos buscar;

    public ProyectoController(PublicarProyecto publicar,
                              ListarProyectosDeAutor listar,
                              OcultarPublicacion ocultar,
                              EditarProyecto editar,
                              ArchivarProyecto archivar,
                              BuscarProyectos buscar) {
        this.publicar = publicar;
        this.listar = listar;
        this.ocultar = ocultar;
        this.editar = editar;
        this.archivar = archivar;
        this.buscar = buscar;
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
     * HU-09 (AB#19): explorar y buscar proyectos. Cubre HU-09.1, HU-09.2 y HU-09.3.
     *
     * <p>{@code GET} sobre la coleccion con parametros de consulta, y no un recurso
     * aparte como {@code /busqueda}: filtrar una coleccion es exactamente lo que
     * expresan los parametros de consulta en REST (ADR-002). Lectura publica: la ruta ya
     * esta abierta en {@code SeguridadConfig} para {@code GET /api/v1/proyectos/**}.</p>
     */
    @GetMapping
    @SecurityRequirements
    @Operation(
            summary = "Explorar y buscar proyectos",
            description = """
                    Proyectos publicados, del mas reciente al mas antiguo, paginados por
                    cursor. Los dos filtros son opcionales y se combinan:

                    - `q`: busqueda por texto en titulo y descripcion. Entiende espanol
                      ("plantilla" encuentra "plantillas") y la sintaxis de un buscador:
                      `"frase exacta"`, `-excluir`, `java or kotlin`.
                    - `tecnologia`: el `slug` de una tecnologia del catalogo
                      (`GET /api/v1/tecnologias`). Devuelve solo los proyectos con esa
                      etiqueta.

                    Sin filtros, lista todos los proyectos publicados.

                    **Sin resultados** la respuesta sigue siendo `200`, con `contenido`
                    vacio y un `mensaje` que orienta segun lo buscado. Con resultados,
                    `mensaje` es `null`.

                    Para la pagina siguiente, devuelve `cursorSiguiente` tal cual en el
                    parametro `cursor`.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina de resultados, o estado vacio con mensaje"),
            @ApiResponse(responseCode = "400", description = "Termino de busqueda de mas de 100 caracteres",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public BuscarProyectos.ResultadoBusqueda buscar(
            @Parameter(description = "Texto a buscar en titulo y descripcion (max. 100)", example = "plantillas")
            @RequestParam(required = false) String q,
            @Parameter(description = "Slug de la tecnologia del catalogo", example = "java")
            @RequestParam(required = false) String tecnologia,
            @Parameter(description = "Cursor devuelto por la pagina anterior")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "Tamano de pagina (1-50, por defecto 20)")
            @RequestParam(required = false) Integer limite) {

        return buscar.ejecutar(q, tecnologia, cursor, limite);
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

    /**
     * HU-08 (AB#18): editar un proyecto. Cubre HU-08.1 (AB#39) y HU-08.2 (AB#40).
     *
     * <p>Dos capas de autorizacion, como en el resto de la API. {@code @PreAuthorize}
     * filtra por rol: hace falta poder editar publicaciones o poder moderarlas. Despues
     * {@code PoliticaProyecto} decide si este actor puede editar <i>este</i> proyecto,
     * cosa que solo se sabe tras leerlo.</p>
     *
     * <p>{@code PUT} y no {@code PATCH}: se envia el proyecto completo, con las mismas
     * reglas que al publicar, igual que {@code PUT /api/v1/perfiles/{id}}.</p>
     */
    @PutMapping("/{proyectoId}")
    @PreAuthorize("hasAnyAuthority('publicacion:editar', 'publicacion:moderar')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Editar un proyecto",
            description = """
                    Reemplaza el contenido del proyecto. Se envia completo, como al
                    publicar: un campo opcional que no se envie vuelve a su valor por
                    defecto.

                    Pueden editarlo **su autor** y quien tenga el permiso
                    `publicacion:moderar` (MODERADOR y ADMIN). Cualquier otro recibe
                    **403 PROYECTO_AJENO** y el intento queda registrado en auditoria.
                    La edicion hecha por un moderador tambien queda auditada.

                    Solo se edita un proyecto **PUBLICADO**; uno oculto o archivado
                    responde **409 PROYECTO_NO_EDITABLE**.

                    El identificador, el autor y la fecha de publicacion no cambian.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Proyecto actualizado"),
            @ApiResponse(responseCode = "400", description = "Faltan titulo o descripcion, o no cumplen el formato",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/NoAutenticado"),
            @ApiResponse(responseCode = "403", description = "No eres el autor ni moderador, o tu rol no edita proyectos",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "404", description = "El proyecto no existe o no esta disponible para ti",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "409", description = "Estado que no admite edicion, repositorio ya enlazado a otro proyecto, o edicion concurrente",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "422", description = "Tecnologias inexistentes o no aprobadas",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public ProyectoEditado editar(@PathVariable UUID proyectoId,
                                  @Valid @RequestBody PeticionEditarProyecto peticion,
                                  HttpServletRequest http) {

        EditarProyecto.Resultado r = editar.ejecutar(new EditarProyecto.Comando(
                proyectoId,
                peticion.titulo(),
                peticion.descripcion(),
                peticion.resumen(),
                peticion.tecnologias(),
                peticion.urlRepositorio(),
                peticion.estadoProyecto(),
                peticion.licencia(),
                peticion.urlDemo(),
                Boolean.TRUE.equals(peticion.buscaColaboradores()),
                IpCliente.de(http)));

        return new ProyectoEditado(r.id(), r.autorId(), r.titulo(), r.descripcion(), r.resumen(),
                r.tecnologias(), r.urlRepositorio(), r.estadoProyecto(), r.licencia(), r.urlDemo(),
                r.buscaColaboradores(), r.publicadoEn());
    }

    /**
     * HU-08 (AB#18): eliminar un proyecto. Cubre HU-08.3 y HU-08.4.
     *
     * <p>Eliminar es archivar (regla R3), y archivar es una transicion de estado, asi
     * que se modela como sub-recurso de accion y no como {@code DELETE}: es exactamente
     * la ruta que fija el ADR-002 ({@code POST .../archivado}), igual que
     * {@code /ocultamiento}. Ademas, un {@code DELETE} con cuerpo, que la confirmacion
     * necesita, lo descartan algunos clientes y proxies.</p>
     *
     * <p>El cuerpo es opcional a nivel HTTP a proposito: si falta, el dominio responde
     * {@code PROYECTO_CONFIRMACION_REQUERIDA}, que le dice al cliente exactamente que
     * hacer, en vez de un {@code CUERPO_ILEGIBLE} generico.</p>
     */
    @PostMapping("/{proyectoId}/archivado")
    @PreAuthorize("hasAnyAuthority('publicacion:archivar', 'publicacion:moderar')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Eliminar (archivar) un proyecto",
            description = """
                    Retira el proyecto de forma **definitiva**: deja de verse en el perfil
                    y en la lista del autor, y no se puede volver a editar. La fila se
                    conserva en estado `ARCHIVADO`, con la transicion registrada en el
                    historial.

                    **Requiere confirmacion previa:** `confirmacionTitulo` debe ser el
                    titulo exacto del proyecto. Sin el, **400
                    PROYECTO_CONFIRMACION_REQUERIDA**; si no coincide, **422
                    PROYECTO_CONFIRMACION_INVALIDA**.

                    Pueden eliminarlo **su autor** y quien tenga el permiso
                    `publicacion:moderar`. Cualquier otro recibe **403 PROYECTO_AJENO** y
                    el intento queda registrado en auditoria.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Proyecto eliminado (archivado)"),
            @ApiResponse(responseCode = "400", description = "Falta la confirmacion",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/NoAutenticado"),
            @ApiResponse(responseCode = "403", description = "No eres el autor ni moderador, o tu rol no elimina proyectos",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "404", description = "El proyecto no existe o no esta disponible para ti",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "409", description = "El proyecto ya estaba eliminado",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "422", description = "La confirmacion no coincide con el titulo",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public ResponseEntity<Void> eliminar(@PathVariable UUID proyectoId,
                                         @RequestBody(required = false) PeticionEliminarProyecto peticion,
                                         HttpServletRequest http) {
        archivar.ejecutar(new ArchivarProyecto.Comando(
                proyectoId,
                peticion == null ? null : peticion.confirmacionTitulo(),
                IpCliente.de(http)));
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

    /**
     * Cuerpo de la edicion. Los mismos campos y anotaciones que
     * {@link PeticionPublicarProyecto}, sin {@code autorId}: el autor de un proyecto
     * existente no cambia, asi que no hay nada que declarar.
     *
     * <p>La validacion de forma se repite aqui y en el dominio a proposito, como en la
     * publicacion: esta rechaza pronto lo evidente; el dominio es la regla que ninguna
     * otra ruta puede saltarse.</p>
     */
    @Schema(name = "PeticionEditarProyecto")
    public record PeticionEditarProyecto(

            @NotBlank(message = "el titulo es obligatorio")
            @Size(min = 5, max = 200, message = "el titulo debe tener entre 5 y 200 caracteres")
            @Schema(example = "Motor de plantillas en Java")
            String titulo,

            @NotBlank(message = "la descripcion es obligatoria")
            @Size(min = 20, message = "la descripcion debe tener al menos 20 caracteres")
            @Schema(example = "Motor de plantillas minimalista en Java 17, ahora con soporte para bucles.")
            String descripcion,

            @Schema(description = "Opcional. Si falta, se deriva de la descripcion.", nullable = true)
            String resumen,

            @NotEmpty(message = "debes indicar al menos una tecnologia")
            @Size(min = 1, max = 5, message = "entre 1 y 5 tecnologias")
            @Schema(description = "Identificadores del catalogo de tecnologias", example = "[1, 7]")
            Set<Short> tecnologias,

            @Schema(description = "Debe ser https. Si se omite, el proyecto queda sin repositorio enlazado.",
                    example = "https://github.com/usuario/motor-plantillas", nullable = true)
            String urlRepositorio,

            @Schema(example = "ESTABLE", nullable = true)
            EstadoProyecto estadoProyecto,

            @Schema(example = "MIT", nullable = true)
            String licencia,

            @Schema(nullable = true)
            String urlDemo,

            @Schema(defaultValue = "false", nullable = true)
            Boolean buscaColaboradores
    ) { }

    /**
     * Confirmacion de la eliminacion (HU-08.3).
     *
     * <p>Sin {@code @NotBlank}: su ausencia la detecta el dominio, que responde con un
     * codigo propio y explicito en lugar del generico de validacion.</p>
     */
    @Schema(name = "PeticionEliminarProyecto")
    public record PeticionEliminarProyecto(
            @Schema(description = "El titulo exacto del proyecto, para confirmar que se quiere eliminar",
                    example = "Motor de plantillas en Java")
            String confirmacionTitulo
    ) { }

    /** El proyecto tal como quedo guardado, con los valores ya normalizados. */
    @Schema(name = "ProyectoEditado")
    public record ProyectoEditado(
            UUID id,
            UUID autorId,
            String titulo,
            String descripcion,
            String resumen,
            @Schema(description = "Identificadores del catalogo de tecnologias")
            Set<Short> tecnologias,
            String urlRepositorio,
            EstadoProyecto estadoProyecto,
            String licencia,
            String urlDemo,
            boolean buscaColaboradores,
            Instant publicadoEn
    ) { }
}
