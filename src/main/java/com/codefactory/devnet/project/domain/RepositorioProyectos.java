package com.codefactory.devnet.project.domain;

import com.codefactory.devnet.project.api.PaginaKeyset;

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
     * HU-09: proyectos publicados que cumplen los criterios, del mas reciente al mas
     * antiguo, paginados por cursor.
     *
     * <p>Mismo orden y mismo cursor que {@link #deAutor}: ningun resultado se repite ni
     * se salta entre paginas aunque alguien publique mientras se recorren.</p>
     *
     * @param texto        termino de texto completo, o {@code null} para no filtrar
     * @param tecnologiaId tecnologia que debe tener el proyecto, o {@code null}
     */
    PaginaKeyset<ProyectoResumen> buscar(String texto, Short tecnologiaId, String cursor, int limite);

    // ------------------------------------------------------------------
    // HU-08: edicion
    // ------------------------------------------------------------------

    /**
     * El proyecto completo como agregado de dominio, sea cual sea su estado.
     *
     * <p>A diferencia de {@link #porId}, que es una proyeccion de lectura con los
     * nombres de las tecnologias, este devuelve sus identificadores y el estado de la
     * publicacion: lo necesario para decidir si se puede modificar y para validarlo.</p>
     *
     * <p>No filtra por estado a proposito: decidir que hacer con un proyecto oculto o
     * archivado es una regla de negocio, no una consulta.</p>
     *
     * <p>Lo usan {@code EditarProyecto} y {@code ArchivarProyecto}: ambos necesitan el
     * agregado completo antes de decidir si el actor puede modificarlo.</p>
     */
    Optional<Proyecto> paraEditar(UUID proyectoId);

    /**
     * Persiste el contenido de un proyecto ya existente.
     *
     * <p>Como {@link #guardar}, escribe en varias tablas dentro de la misma
     * transaccion: {@code publicacion}, {@code publicacion_proyecto},
     * {@code publicacion_tecnologia} y {@code repositorio}. Si otra peticion modifico
     * el proyecto entre la lectura y la escritura, falla con
     * {@code CONFLICTO_CONCURRENCIA} en lugar de pisar sus cambios.</p>
     */
    void actualizar(Proyecto proyecto);

    /**
     * Como {@link #existeRepositorioConUrl}, pero ignorando los repositorios del propio
     * proyecto: al editar, conservar el enlace que ya se tenia no es un duplicado.
     */
    boolean existeRepositorioConUrlEnOtroProyecto(String url, UUID proyectoId);

    /**
     * Proyeccion de lectura. No expone la entidad JPA fuera de infraestructura.
     *
     * <p>Lleva {@code autorId} pero no el nombre del autor: ese dato pertenece al
     * modulo {@code identity} y se resuelve en la capa de aplicacion a traves de
     * {@code UsuarioDirectorio}. Unir aqui contra {@code usuario} seria saltarse la
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
