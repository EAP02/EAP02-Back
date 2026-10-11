package com.codefactory.devnet.discussion.api;

import com.codefactory.devnet.discussion.application.ConsultarDiscusion;
import com.codefactory.devnet.discussion.application.CrearDiscusion;
import com.codefactory.devnet.discussion.domain.CategoriaDiscusion;
import com.codefactory.devnet.shared.api.RespuestaError;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * HU-11: crear una discusion y verla con su autor y su hilo.
 */
@RestController
@RequestMapping("/api/v1/discusiones")
@Tag(name = "Discusiones", description = "Temas de conversacion sobre tecnologias")
public class DiscusionController {

    private final CrearDiscusion crear;
    private final ConsultarDiscusion consultar;

    public DiscusionController(CrearDiscusion crear, ConsultarDiscusion consultar) {
        this.crear = crear;
        this.consultar = consultar;
    }

    /**
     * El permiso es el mismo que para publicar un proyecto: en el catalogo de
     * {@code V1__baseline.sql}, {@code publicacion:crear} es "crear borradores de
     * proyecto o discusion". Lo tienen DESARROLLADOR y MODERADOR; un VISITANTE no.
     */
    @PostMapping
    @PreAuthorize("hasAuthority('publicacion:crear')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Crear una discusion",
            description = """
                    Abre un tema de conversacion sobre una o varias tecnologias. Queda
                    publicada al instante y asociada a quien la crea: el autor sale del
                    token.

                    - `titulo`: 5 a 200 caracteres.
                    - `cuerpo`: al menos 20 caracteres.
                    - `tecnologias`: de 1 a 5 identificadores del catalogo
                      (`GET /api/v1/tecnologias`).
                    - `categoria`: opcional, `DEBATE` por defecto.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Discusion publicada"),
            @ApiResponse(responseCode = "400", description = "Falta un campo obligatorio o no cumple el formato",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/NoAutenticado"),
            @ApiResponse(responseCode = "403", description = "Tu rol no puede publicar",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class))),
            @ApiResponse(responseCode = "422", description = "Tecnologias inexistentes o no aprobadas, o cuenta sin permiso de escritura",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public ResponseEntity<RespuestaDiscusionCreada> crear(@Valid @RequestBody PeticionCrearDiscusion peticion) {
        CrearDiscusion.Resultado r = crear.ejecutar(new CrearDiscusion.Comando(
                peticion.titulo(),
                peticion.cuerpo(),
                peticion.categoria(),
                peticion.tecnologias()));

        // 201 con Location: el cliente sabe donde leerla sin componer la URL.
        return ResponseEntity
                .created(URI.create("/api/v1/discusiones/" + r.id()))
                .body(new RespuestaDiscusionCreada(r.id(), r.titulo(), r.publicadoEn()));
    }

    @GetMapping("/{discusionId}")
    @SecurityRequirements
    @Operation(
            summary = "Ver una discusion",
            description = """
                    Lectura publica, sin token. Devuelve la discusion con su autor, sus
                    etiquetas y el hilo: cada usuario que ha comentado, una sola vez, por
                    orden de su primera intervencion. Una discusion recien creada tiene
                    el hilo vacio.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "La discusion"),
            @ApiResponse(responseCode = "404", description = "No existe o no esta publicada",
                    content = @Content(schema = @Schema(implementation = RespuestaError.class)))
    })
    public ConsultarDiscusion.DiscusionDetalle obtener(@PathVariable UUID discusionId) {
        return consultar.ejecutar(discusionId);
    }

    // ------------------------------------------------------------------

    /**
     * Cuerpo de la creacion. La validacion de forma se repite aqui y en el dominio a
     * proposito, como en la publicacion de proyectos: esta rechaza pronto lo evidente;
     * el dominio es la regla que ninguna otra ruta puede saltarse.
     */
    @Schema(name = "PeticionCrearDiscusion")
    public record PeticionCrearDiscusion(

            @NotBlank(message = "el titulo es obligatorio")
            @Size(min = 5, max = 200, message = "el titulo debe tener entre 5 y 200 caracteres")
            @Schema(example = "Spring Boot 4: vale la pena migrar ya?")
            String titulo,

            @NotBlank(message = "el cuerpo es obligatorio")
            @Size(min = 20, message = "el cuerpo debe tener al menos 20 caracteres")
            @Schema(example = "Estamos evaluando migrar desde Boot 3. Que problemas encontraron con Jackson 3?")
            String cuerpo,

            @Schema(description = "Opcional. DEBATE si no se indica.", example = "DEBATE", nullable = true)
            CategoriaDiscusion categoria,

            @NotEmpty(message = "debes indicar al menos una tecnologia")
            @Size(min = 1, max = 5, message = "entre 1 y 5 tecnologias")
            @Schema(description = "Identificadores del catalogo de tecnologias", example = "[1, 7]")
            Set<Short> tecnologias
    ) { }

    @Schema(name = "RespuestaDiscusionCreada")
    public record RespuestaDiscusionCreada(UUID id, String titulo, Instant publicadoEn) { }
}
