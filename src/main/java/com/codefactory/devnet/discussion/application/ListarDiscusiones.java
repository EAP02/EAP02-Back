package com.codefactory.devnet.discussion.application;

import com.codefactory.devnet.discussion.domain.CategoriaDiscusion;
import com.codefactory.devnet.discussion.domain.FiltroDiscusiones;
import com.codefactory.devnet.discussion.domain.RepositorioDiscusiones;
import com.codefactory.devnet.identity.api.UsuarioDirectorio;
import com.codefactory.devnet.project.api.CatalogoTecnologiasConsulta;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * HU-12: listar y filtrar discusiones por tema.
 *
 * <p>Los tres criterios de aceptacion se cumplen aqui:</p>
 * <ol>
 *   <li>el filtro por etiqueta devuelve <b>solo</b> discusiones con esa etiqueta;</li>
 *   <li>el listado es paginado y <b>no repite elementos entre paginas</b>: cursor sobre
 *       {@code (publicado_en, id)}, nunca {@code OFFSET};</li>
 *   <li>si un filtro no tiene discusiones, la respuesta es un estado vacio con un
 *       mensaje que orienta.</li>
 * </ol>
 *
 * <p>Mismo contrato que la busqueda de proyectos (HU-09): mismo orden, mismos topes de
 * pagina, mismo filtro por slug y misma forma del estado vacio. Quien ya usa uno sabe
 * usar el otro.</p>
 *
 * <p>Lectura publica.</p>
 */
@Service
public class ListarDiscusiones {

    private static final int LIMITE_MAXIMO = 50;
    private static final int LIMITE_POR_DEFECTO = 20;

    /** Lo justo para decidir si interesa; el cuerpo completo esta en el detalle. */
    static final int EXTRACTO_MAX = 200;

    private final RepositorioDiscusiones discusiones;
    private final CatalogoTecnologiasConsulta catalogo;
    private final UsuarioDirectorio directorio;

    public ListarDiscusiones(RepositorioDiscusiones discusiones,
                             CatalogoTecnologiasConsulta catalogo,
                             UsuarioDirectorio directorio) {
        this.discusiones = discusiones;
        this.catalogo = catalogo;
        this.directorio = directorio;
    }

    @Transactional(readOnly = true)
    public Listado ejecutar(String tecnologia, String cursor, Integer limite) {
        FiltroDiscusiones filtro = FiltroDiscusiones.de(tecnologia);
        int tamano = limite == null ? LIMITE_POR_DEFECTO : Math.min(Math.max(limite, 1), LIMITE_MAXIMO);

        // Un solo viaje al catalogo, cacheado en project: sirve para resolver el slug del
        // filtro y para dar nombre a las etiquetas de cada fila.
        Map<Short, CatalogoTecnologiasConsulta.TecnologiaPublica> catalogoPorId = catalogo.aprobadas().stream()
                .collect(Collectors.toMap(CatalogoTecnologiasConsulta.TecnologiaPublica::id, Function.identity()));

        // Criterio 1. Una etiqueta que no existe no puede tener discusiones: estado vacio
        // directo, sin consultar, con un mensaje que dice exactamente eso.
        Optional<CatalogoTecnologiasConsulta.TecnologiaPublica> etiqueta = filtro.hayTecnologia()
                ? catalogoPorId.values().stream().filter(t -> t.slug().equals(filtro.tecnologiaSlug())).findFirst()
                : Optional.empty();
        if (filtro.hayTecnologia() && etiqueta.isEmpty()) {
            return Listado.vacio(filtro.mensajeSinResultados(null));
        }

        // Criterio 2
        RepositorioDiscusiones.Pagina pagina = discusiones.listar(
                etiqueta.map(CatalogoTecnologiasConsulta.TecnologiaPublica::id).orElse(null),
                cursor,
                tamano);

        // Criterio 3
        if (pagina.contenido().isEmpty()) {
            return Listado.vacio(filtro.mensajeSinResultados(
                    etiqueta.map(CatalogoTecnologiasConsulta.TecnologiaPublica::nombre).orElse(null)));
        }

        Map<UUID, String> autores = nombresDeAutores(pagina.contenido());
        List<DiscusionListada> contenido = pagina.contenido().stream()
                .map(d -> new DiscusionListada(
                        d.id(),
                        d.titulo(),
                        extracto(d.cuerpo()),
                        d.categoria(),
                        d.resuelta(),
                        d.tecnologias().stream()
                                .map(catalogoPorId::get)
                                .filter(Objects::nonNull)
                                .map(t -> new ConsultarDiscusion.Etiqueta(t.id(), t.nombre(), t.slug()))
                                .toList(),
                        new ConsultarDiscusion.Autor(d.autorId(), autores.get(d.autorId())),
                        d.publicadoEn()))
                .toList();

        return new Listado(contenido, pagina.cursorSiguiente(), pagina.hayMas(), null);
    }

    /** Una consulta a identity por autor distinto de la pagina, no una por fila. */
    private Map<UUID, String> nombresDeAutores(List<RepositorioDiscusiones.DiscusionPublicada> filas) {
        Map<UUID, String> nombres = new HashMap<>();
        filas.stream()
                .map(RepositorioDiscusiones.DiscusionPublicada::autorId)
                .distinct()
                .forEach(id -> nombres.put(id, directorio.buscar(id)
                        .map(UsuarioDirectorio.UsuarioResumen::nombreUsuario)
                        .orElse(null)));
        return nombres;
    }

    static String extracto(String cuerpo) {
        return cuerpo.length() <= EXTRACTO_MAX
                ? cuerpo
                : cuerpo.substring(0, EXTRACTO_MAX - 3).stripTrailing() + "...";
    }

    /**
     * Una discusion en el listado. Reutiliza {@code Autor} y {@code Etiqueta} del detalle:
     * se ven igual en los dos sitios.
     */
    public record DiscusionListada(
            UUID id,
            String titulo,
            String extracto,
            CategoriaDiscusion categoria,
            boolean resuelta,
            List<ConsultarDiscusion.Etiqueta> tecnologias,
            ConsultarDiscusion.Autor autor,
            Instant publicadoEn
    ) { }

    /**
     * Una pagina del listado, o el estado vacio. Misma forma que el resultado de la
     * busqueda de proyectos.
     *
     * @param mensaje orientacion para el estado vacio; {@code null} si hay resultados
     */
    public record Listado(
            List<DiscusionListada> contenido,
            String cursorSiguiente,
            boolean hayMas,
            String mensaje
    ) {
        static Listado vacio(String mensaje) {
            return new Listado(List.of(), null, false, mensaje);
        }
    }
}
