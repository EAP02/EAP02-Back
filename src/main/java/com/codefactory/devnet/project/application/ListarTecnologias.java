package com.codefactory.devnet.project.application;

import com.codefactory.devnet.project.domain.CatalogoTecnologias;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * HU-09.2: el catalogo de tecnologias que se ofrece como filtro.
 *
 * <p>Sin el, el filtro por tecnologia obliga al cliente a adivinar los slugs. Cierra
 * ademas el endpoint {@code GET /api/v1/tecnologias}, que estaba abierto en la cadena
 * de seguridad desde el sprint 1 pero no tenia controlador.</p>
 */
@Service
public class ListarTecnologias {

    private final CatalogoTecnologias catalogo;

    public ListarTecnologias(CatalogoTecnologias catalogo) {
        this.catalogo = catalogo;
    }

    public List<TecnologiaCatalogo> ejecutar() {
        return catalogo.aprobadas().stream()
                .map(t -> new TecnologiaCatalogo(t.id(), t.nombre(), t.slug(), t.categoria()))
                .toList();
    }

    /**
     * @param slug valor que se pasa en {@code GET /api/v1/proyectos?tecnologia=}
     * @param id   valor que se envia en {@code tecnologias} al publicar o editar
     */
    public record TecnologiaCatalogo(short id, String nombre, String slug, String categoria) { }
}
