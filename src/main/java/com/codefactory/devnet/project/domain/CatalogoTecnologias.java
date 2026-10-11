package com.codefactory.devnet.project.domain;

import java.util.List;

/**
 * Puerto de lectura del catalogo de tecnologias. Lo implementa {@code infrastructure}.
 *
 * <p>Separado de {@link RepositorioProyectos} porque el catalogo no es parte del
 * agregado proyecto: es un dato de referencia que los proyectos etiquetan, y tiene su
 * propio ciclo de vida (lo cura un moderador).</p>
 */
public interface CatalogoTecnologias {

    /**
     * Las tecnologias aprobadas, por categoria y nombre.
     *
     * <p>Solo las aprobadas: una tecnologia propuesta y aun sin curar no debe ofrecerse
     * como filtro ni como etiqueta.</p>
     */
    List<Tecnologia> aprobadas();
}
