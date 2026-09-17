package com.codefactory.devnet.profile.api;

import com.codefactory.devnet.profile.application.EditarAvatar;
import com.codefactory.devnet.profile.application.EditarPerfil;
import com.codefactory.devnet.profile.domain.Perfil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/perfiles")
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
    public PerfilPublicoConsulta.PerfilPublico obtener(@PathVariable UUID id) {
        return consulta.obtener(id);
    }

    @PutMapping("/{id}")
    public PerfilRespuesta editar(@PathVariable UUID id, @Valid @RequestBody PeticionEditar peticion) {
        return PerfilRespuesta.de(editarPerfil.ejecutar(id, peticion.nombre(), peticion.biografia(),
                peticion.tecnologias(), peticion.githubUrl(), peticion.linkedinUrl()));
    }

    @PatchMapping("/{id}/avatar")
    public PerfilRespuesta editarAvatar(@PathVariable UUID id, @Valid @RequestBody PeticionAvatar peticion) {
        return PerfilRespuesta.de(editarAvatar.ejecutar(id, peticion.avatarUrl()));
    }

    public record PeticionEditar(
            @NotBlank @Size(max = 100) String nombre,
            @NotBlank @Size(max = 1000) String biografia,
            @NotEmpty List<@NotBlank @Size(max = 80) String> tecnologias,
            @Size(max = 300) String githubUrl,
            @Size(max = 300) String linkedinUrl
    ) { }

    public record PeticionAvatar(@NotBlank @Size(max = 500) String avatarUrl) { }

    public record PerfilRespuesta(UUID id, String nombre, String biografia, String avatarUrl,
                                  List<String> tecnologias, String githubUrl, String linkedinUrl) {
        static PerfilRespuesta de(Perfil p) {
            return new PerfilRespuesta(p.id(), p.nombre(), p.biografia(), p.avatarUrl(),
                    p.tecnologias(), p.githubUrl(), p.linkedinUrl());
        }
    }
}
