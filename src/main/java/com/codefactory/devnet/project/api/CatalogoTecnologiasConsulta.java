package com.codefactory.devnet.project.api;

import java.util.List;

/**
 * Servicio publico que el modulo project ofrece a los demas: el catalogo de tecnologias.
 *
 * <p>{@code project} es el dueno del catalogo, pero las etiquetas no son solo de los
 * proyectos: las discusiones (HU-11) se etiquetan con las mismas tecnologias. Este
 * contrato es la unica via por la que otro modulo las consulta. La alternativa, leer la
 * tabla {@code tecnologia} por SQL desde fuera, saltaria la frontera por debajo, que es
 * justo lo que ArchUnit no puede ver (ADR-006).</p>
 */
public interface CatalogoTecnologiasConsulta {

    /** Las tecnologias aprobadas. Solo estas son etiquetas validas. */
    List<TecnologiaPublica> aprobadas();

    /**
     * @param id   identificador que se envia al etiquetar
     * @param slug valor legible, apto para filtros en una URL
     */
    record TecnologiaPublica(short id, String nombre, String slug) { }
}
