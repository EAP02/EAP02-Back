package com.codefactory.devnet.profile.application;

import com.codefactory.devnet.profile.api.PerfilPublicoConsulta;
import com.codefactory.devnet.profile.domain.Perfil;
import com.codefactory.devnet.profile.domain.PerfilNoEncontradoException;
import com.codefactory.devnet.profile.domain.RepositorioPerfiles;
import com.codefactory.devnet.project.api.ProyectoPublicoConsulta;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class ObtenerPerfilPublico implements PerfilPublicoConsulta {
    private final RepositorioPerfiles perfiles;
    private final ProyectoPublicoConsulta proyectos;

    public ObtenerPerfilPublico(RepositorioPerfiles perfiles, ProyectoPublicoConsulta proyectos) {
        this.perfiles = perfiles;
        this.proyectos = proyectos;
    }

    @Override
    @Transactional(readOnly = true)
    public PerfilPublico obtener(UUID perfilId) {
        Perfil p = perfiles.porId(perfilId).orElseThrow(() -> new PerfilNoEncontradoException(perfilId));

        // La vista publica muestra nombres, no identificadores del catalogo: a quien
        // lee un perfil le importa "Spring Boot", no el 7. El dominio guarda ids
        // porque son los que dan integridad; la traduccion ocurre aqui.
        Map<Short, String> nombres = perfiles.nombresDeTecnologias(p.idsDeTecnologias());
        List<String> tecnologias = p.tecnologias().stream()
                .map(t -> nombres.get(t.tecnologiaId()))
                .filter(Objects::nonNull)
                .toList();

        return new PerfilPublico(p.id(), p.nombre(), p.biografia(), p.avatarUrl(), tecnologias,
                p.githubUrl(), p.linkedinUrl(), proyectos.publicadosPor(perfilId));
    }
}
