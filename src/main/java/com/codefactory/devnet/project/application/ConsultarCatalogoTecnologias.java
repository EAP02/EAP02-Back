package com.codefactory.devnet.project.application;

import com.codefactory.devnet.project.api.CatalogoTecnologiasConsulta;
import com.codefactory.devnet.project.domain.CatalogoTecnologias;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Implementacion del contrato {@link CatalogoTecnologiasConsulta}, como
 * {@code ConsultarProyectosPublicos} lo es de {@code ProyectoPublicoConsulta}.
 *
 * <p>Traduce el modelo interno al publico: los consumidores no ven el {@code Tecnologia}
 * del dominio, asi que este puede cambiar sin romperlos. Se apoya en el puerto cacheado,
 * de modo que consultar el catalogo desde otro modulo no cuesta una consulta.</p>
 */
@Service
public class ConsultarCatalogoTecnologias implements CatalogoTecnologiasConsulta {

    private final CatalogoTecnologias catalogo;

    public ConsultarCatalogoTecnologias(CatalogoTecnologias catalogo) {
        this.catalogo = catalogo;
    }

    @Override
    public List<TecnologiaPublica> aprobadas() {
        return catalogo.aprobadas().stream()
                .map(t -> new TecnologiaPublica(t.id(), t.nombre(), t.slug()))
                .toList();
    }
}
