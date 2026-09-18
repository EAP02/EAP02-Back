package com.codefactory.devnet.profile.api;

import com.codefactory.devnet.profile.application.EditarAvatar;
import com.codefactory.devnet.profile.application.EditarPerfil;
import com.codefactory.devnet.profile.application.PerfilDetalle;
import com.codefactory.devnet.profile.domain.NivelTecnologia;
import com.codefactory.devnet.profile.domain.TecnologiaDeclarada;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/perfiles")
@Tag(name = "Perfiles", description = "Perfil tecnico del desarrollador")
public class PerfilController {

    private final EditarPerfil editarPerfil;
    private final EditarAvatar editarAvatar;
    private final PerfilPublicoConsulta consulta;

    public PerfilController(EditarPerfil editarPerfil, EditarAvatar editarAvatar,
                            PerfilPublicoConsulta consulta) {
        this.editarPerfil = editarPerfil;
        this.editarAvatar = editarAvatar;
        this.consulta = consulta;
    }

    @GetMapping("/{id}")
    @SecurityRequirements
    @Operation(summary = "Ver un perfil publico",
            description = "Lectura abierta: el perfil de un desarrollador es su carta de presentacion.")
    public PerfilPublicoConsulta.PerfilPublico obtener(@PathVariable UUID id) {
        return consulta.obtener(id);
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Editar el perfil propio",
            description = """
                    Solo el titular puede editar su perfil. Si el `id` de la ruta no
                    coincide con el del token, responde **403 PERFIL_AJENO**.

                    Las tecnologias se declaran contra el catalogo, por identificador,
                    y cada una exige nivel. Un identificador que no exista o que no
                    este aprobado devuelve **422**.
                    """)
    public PerfilDetalle editar(@PathVariable UUID id, @Valid @RequestBody PeticionEditar peticion) {
        return editarPerfil.ejecutar(id, peticion.nombre(), peticion.biografia(),
                peticion.aDominio(), peticion.githubUrl(), peticion.linkedinUrl());
    }

    @PatchMapping("/{id}/avatar")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cambiar el avatar propio",
            description = "Misma regla de propiedad que la edicion del perfil.")
    public PerfilDetalle editarAvatar(@PathVariable UUID id, @Valid @RequestBody PeticionAvatar peticion) {
        return editarAvatar.ejecutar(id, peticion.avatarUrl());
    }

    // ------------------------------------------------------------------

    @Schema(name = "PeticionEditarPerfil")
    public record PeticionEditar(

            @NotBlank @Size(max = 100)
            @Schema(example = "Ana Restrepo")
            String nombre,

            @NotBlank @Size(max = 1000)
            @Schema(example = "Backend developer con foco en sistemas distribuidos.")
            String biografia,

            @NotEmpty(message = "debes declarar al menos una tecnologia")
            @Size(max = 15, message = "como maximo 15 tecnologias")
            @Valid
            List<TecnologiaPeticion> tecnologias,

            @Size(max = 300)
            @Schema(example = "https://github.com/ana", nullable = true)
            String githubUrl,

            @Size(max = 300)
            @Schema(example = "https://linkedin.com/in/ana", nullable = true)
            String linkedinUrl
    ) {
        List<TecnologiaDeclarada> aDominio() {
            return tecnologias.stream()
                    .map(t -> new TecnologiaDeclarada(t.tecnologiaId(), t.nivel(), t.anios()))
                    .toList();
        }
    }

    /**
     * Una tecnologia del catalogo, con el nivel declarado.
     *
     * <p>Por identificador y no por texto libre: es lo que impide que React, ReactJS
     * y react.js acaben siendo tres tecnologias distintas. Los identificadores salen
     * del catalogo, consultable en {@code GET /api/v1/tecnologias}.</p>
     */
    @Schema(name = "TecnologiaDeclarada")
    public record TecnologiaPeticion(

            @NotNull(message = "el identificador de la tecnologia es obligatorio")
            @Schema(example = "1", description = "Identificador del catalogo de tecnologias")
            Short tecnologiaId,

            @NotNull(message = "indica tu nivel en esta tecnologia")
            @Schema(example = "AVANZADO")
            NivelTecnologia nivel,

            @Schema(example = "3", nullable = true, description = "Anios de experiencia")
            Short anios
    ) { }

    @Schema(name = "PeticionAvatar")
    public record PeticionAvatar(
            @NotBlank @Size(max = 500)
            @Schema(example = "https://cdn.devnet.test/avatares/ana.png")
            String avatarUrl
    ) { }
}