package com.codefactory.devnet.project.application;

import com.codefactory.devnet.project.api.ProyectoPublicoConsulta;
import com.codefactory.devnet.project.domain.RepositorioProyectos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ConsultarProyectosPublicos implements ProyectoPublicoConsulta {
    private final RepositorioProyectos proyectos;

    public ConsultarProyectosPublicos(RepositorioProyectos proyectos) {
        this.proyectos = proyectos;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProyectoPublico> publicadosPor(UUID autorId) {
        return proyectos.deAutor(autorId, null, 50).contenido().stream()
                .map(p -> new ProyectoPublico(p.id(), p.titulo(), p.resumen(), p.urlRepositorio()))
                .toList();
    }
}
