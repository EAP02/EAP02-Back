package com.codefactory.devnet.profile.api;

import com.codefactory.devnet.project.api.ProyectoPublicoConsulta.ProyectoPublico;
import java.util.List;
import java.util.UUID;

public interface PerfilPublicoConsulta {
    PerfilPublico obtener(UUID perfilId);

    record PerfilPublico(UUID id, String nombre, String biografia, String avatarUrl,
                         List<String> tecnologias, String githubUrl, String linkedinUrl,
                         List<ProyectoPublico> proyectosPublicados) { }
}
