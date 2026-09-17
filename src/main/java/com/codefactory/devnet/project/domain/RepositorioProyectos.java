package com.codefactory.devnet.project.domain;

import com.codefactory.devnet.shared.api.PaginaKeyset;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Puerto de persistencia de proyectos. Lo implementa {@code infrastructure}.
 */
public interface RepositorioProyectos {

    /**
     * Persiste un proyecto recien publicado.
     *
     * <p>Escribe en cuatro tablas dentro de la misma transaccion: {@code publicacion}
     * (supertipo), {@code publicacion_proyecto} (subtipo), {@code repositorio} si hay
     * enlace, y {@code publicacion_tecnologia}. O entran las cuatro o no entra
     * ninguna: un proyecto sin su subtipo violaria el invariante del discriminador.</p>
     */
    Proyecto guardar(Proyecto proyecto);

    Optional<ProyectoResumen> porId(UUID proyectoId);

    /**
     * Proyectos publicados de un autor, del mas reciente al mas antiguo.
     *
     * <p>HU-07, criterio 1: "visible en su lista". Pagina por cursor porque la lista
     * crece sin tope y esta ordenada por tiempo.</p>
     */
    PaginaKeyset<ProyectoResumen> deAutor(UUID autorId, String cursor, int limite);

    /** {@code true} si todas las tecnologias existen y estan aprobadas. */
    boolean tecnologiasValidas(Set<Short> ids);

    boolean existeRepositorioConUrl(String url);

    /**
     * Cambia el estado de la publicacion y deja constancia de la transicion.
     *
     * <p>Las dos escrituras van en la misma transaccion: si el historial no se puede
     * escribir, la transicion no ocurre. Sin esa garantia, una publicacion podria
     * aparecer oculta sin que conste quien la oculto ni por que.</p>
     *
     * @return {@code false} si la publicacion no existe
     */
    boolean transicionarEstado(UUID publicacionId, String estadoNuevo, UUID actorId, String motivo);

    /**
     * Proyeccion de lectura. No expone la entidad JPA fuera de infraestructura.
     *
     * <p>Lleva {@code autorId} pero no el nombre del autor: ese dato pertenece al
     * modulo {@code identity} y se resuelve en la capa de aplicacion a traves de
     * {@code IUsuarioDirectorio}. Unir aqui contra {@code usuario} seria saltarse la
     * frontera por debajo, via SQL, que es justo lo que ArchUnit no puede ver.</p>
     */
    record ProyectoResumen(
            UUID id,
            UUID autorId,
            String titulo,
            String resumen,
            String estadoProyecto,
            String urlRepositorio,
            boolean buscaColaboradores,
            Set<String> tecnologias,
            java.time.Instant publicadoEn
    ) { }
}