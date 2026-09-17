package com.codefactory.devnet.project.application;

import com.codefactory.devnet.project.domain.RepositorioProyectos;
import com.codefactory.devnet.project.api.PaginaKeyset;
import com.codefactory.devnet.identity.api.UsuarioDirectorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * HU-07, criterio 1: el proyecto queda "visible en su lista".
 *
 * <p>Lectura publica: un visitante puede ver los proyectos de cualquier miembro. Es
 * lo que hace del perfil una carta de presentacion, que es el valor que el caso
 * persigue.</p>
 */
@Service
public class ListarProyectosDeAutor {

    /** Tope duro de pagina: sin el, un cliente podria pedir la coleccion entera. */
    private static final int LIMITE_MAXIMO = 50;
    private static final int LIMITE_POR_DEFECTO = 20;

    private final RepositorioProyectos proyectos;
    private final UsuarioDirectorio directorio;

    public ListarProyectosDeAutor(RepositorioProyectos proyectos, UsuarioDirectorio directorio) {
        this.proyectos = proyectos;
        this.directorio = directorio;
    }

    @Transactional(readOnly = true)
    public PaginaKeyset<ProyectoListado> ejecutar(UUID autorId, String cursor, Integer limite) {
        int tamano = limite == null ? LIMITE_POR_DEFECTO : Math.min(Math.max(limite, 1), LIMITE_MAXIMO);

        // El nombre del autor pertenece al modulo identity y se resuelve una sola vez
        // para toda la pagina: todos los proyectos son del mismo autor, asi que no hay
        // consulta por fila.
        String nombreAutor = directorio.buscar(autorId)
                .map(UsuarioDirectorio.UsuarioResumen::nombreUsuario)
                .orElse(null);

        return proyectos.deAutor(autorId, cursor, tamano)
                .mapear(r -> new ProyectoListado(
                        r.id(),
                        r.autorId(),
                        nombreAutor,
                        r.titulo(),
                        r.resumen(),
                        r.estadoProyecto(),
                        r.urlRepositorio(),
                        r.buscaColaboradores(),
                        r.tecnologias(),
                        r.publicadoEn()));
    }

    public record ProyectoListado(
            UUID id,
            UUID autorId,
            String autorNombreUsuario,
            String titulo,
            String resumen,
            String estadoProyecto,
            String urlRepositorio,
            boolean buscaColaboradores,
            java.util.Set<String> tecnologias,
            java.time.Instant publicadoEn
    ) { }
}
