package com.codefactory.devnet.project.application;

import com.codefactory.devnet.identity.api.UsuarioDirectorio;
import com.codefactory.devnet.project.api.PaginaKeyset;
import com.codefactory.devnet.project.domain.CatalogoTecnologias;
import com.codefactory.devnet.project.domain.CriteriosBusqueda;
import com.codefactory.devnet.project.domain.RepositorioProyectos;
import com.codefactory.devnet.project.domain.Tecnologia;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * HU-09 (AB#19): explorar y buscar proyectos.
 *
 * <p>Los tres criterios de aceptacion se cumplen aqui:</p>
 * <ol>
 *   <li>HU-09.1: la busqueda por texto devuelve una lista <b>paginada</b> de los
 *       proyectos que coinciden;</li>
 *   <li>HU-09.2: el filtro por tecnologia devuelve <b>solo</b> los proyectos con esa
 *       etiqueta;</li>
 *   <li>HU-09.3: sin coincidencias, la respuesta es un estado vacio con un mensaje que
 *       orienta segun lo que se busco.</li>
 * </ol>
 *
 * <p>Texto y tecnologia se pueden combinar, y sin ninguno de los dos es "explorar":
 * todos los proyectos publicados, del mas reciente al mas antiguo.</p>
 *
 * <p>Lectura publica, como la lista del autor: un visitante tambien descubre proyectos.
 * Solo aparecen los {@code PUBLICADO}; ocultos y archivados nunca.</p>
 */
@Service
public class BuscarProyectos {

    /** Los mismos topes que {@link ListarProyectosDeAutor}, por coherencia del contrato. */
    private static final int LIMITE_MAXIMO = 50;
    private static final int LIMITE_POR_DEFECTO = 20;

    private final RepositorioProyectos proyectos;
    private final CatalogoTecnologias catalogo;
    private final UsuarioDirectorio directorio;

    public BuscarProyectos(RepositorioProyectos proyectos,
                           CatalogoTecnologias catalogo,
                           UsuarioDirectorio directorio) {
        this.proyectos = proyectos;
        this.catalogo = catalogo;
        this.directorio = directorio;
    }

    @Transactional(readOnly = true)
    public ResultadoBusqueda ejecutar(String texto, String tecnologia, String cursor, Integer limite) {
        CriteriosBusqueda criterios = CriteriosBusqueda.de(texto, tecnologia);
        int tamano = limite == null ? LIMITE_POR_DEFECTO : Math.min(Math.max(limite, 1), LIMITE_MAXIMO);

        // HU-09.2. El filtro llega por slug y la base filtra por id. Si el slug no existe
        // en el catalogo no hay nada que consultar: ningun proyecto puede tener una
        // etiqueta que no existe. Se responde el estado vacio directamente, con un
        // mensaje que dice exactamente eso.
        Optional<Tecnologia> filtro = criterios.hayTecnologia()
                ? buscarEnCatalogo(criterios.tecnologiaSlug())
                : Optional.empty();
        if (criterios.hayTecnologia() && filtro.isEmpty()) {
            return ResultadoBusqueda.vacio(criterios.mensajeSinResultados(null));
        }

        // HU-09.1
        PaginaKeyset<RepositorioProyectos.ProyectoResumen> pagina = proyectos.buscar(
                criterios.texto(),
                filtro.map(Tecnologia::id).orElse(null),
                cursor,
                tamano);

        // HU-09.3
        if (pagina.contenido().isEmpty()) {
            return ResultadoBusqueda.vacio(
                    criterios.mensajeSinResultados(filtro.map(Tecnologia::nombre).orElse(null)));
        }

        return ResultadoBusqueda.de(pagina.mapear(conNombreDeAutor(pagina.contenido())));
    }

    private Optional<Tecnologia> buscarEnCatalogo(String slug) {
        // Se recorre la lista cacheada en vez de consultar por slug: el catalogo son
        // unas decenas de filas y ya esta en memoria.
        return catalogo.aprobadas().stream()
                .filter(t -> t.slug().equals(slug))
                .findFirst();
    }

    /**
     * El nombre del autor pertenece al modulo identity. A diferencia de la lista de un
     * solo autor, aqui cada proyecto puede ser de alguien distinto, asi que se resuelve
     * <b>una vez por autor distinto</b> de la pagina, no una vez por fila.
     */
    private java.util.function.Function<RepositorioProyectos.ProyectoResumen, ListarProyectosDeAutor.ProyectoListado>
            conNombreDeAutor(List<RepositorioProyectos.ProyectoResumen> filas) {

        Map<UUID, String> nombres = new HashMap<>();
        filas.stream()
                .map(RepositorioProyectos.ProyectoResumen::autorId)
                .distinct()
                .forEach(id -> nombres.put(id, directorio.buscar(id)
                        .map(UsuarioDirectorio.UsuarioResumen::nombreUsuario)
                        .orElse(null)));

        return r -> new ListarProyectosDeAutor.ProyectoListado(
                r.id(),
                r.autorId(),
                nombres.get(r.autorId()),
                r.titulo(),
                r.resumen(),
                r.estadoProyecto(),
                r.urlRepositorio(),
                r.buscaColaboradores(),
                r.tecnologias(),
                r.publicadoEn());
    }

    /**
     * Una pagina de resultados, o el estado vacio de HU-09.3.
     *
     * <p>Los mismos campos que {@code PaginaKeyset} mas {@code mensaje}, que solo viene
     * cuando no hay resultados. Asi el cliente no tiene que inventar el texto del estado
     * vacio: el servidor sabe por que esta vacio y lo dice.</p>
     *
     * <p>Reutiliza {@code ProyectoListado}: un proyecto se ve igual en la lista de su
     * autor que en una busqueda.</p>
     *
     * @param mensaje orientacion para el estado vacio; {@code null} si hay resultados
     */
    public record ResultadoBusqueda(
            List<ListarProyectosDeAutor.ProyectoListado> contenido,
            String cursorSiguiente,
            boolean hayMas,
            String mensaje
    ) {
        static ResultadoBusqueda de(PaginaKeyset<ListarProyectosDeAutor.ProyectoListado> pagina) {
            return new ResultadoBusqueda(pagina.contenido(), pagina.cursorSiguiente(), pagina.hayMas(), null);
        }

        static ResultadoBusqueda vacio(String mensaje) {
            return new ResultadoBusqueda(List.of(), null, false, mensaje);
        }
    }
}
