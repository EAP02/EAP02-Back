package com.codefactory.devnet.project.infrastructure;

import com.codefactory.devnet.project.domain.CatalogoTecnologias;
import com.codefactory.devnet.project.domain.Tecnologia;
import com.codefactory.devnet.shared.cache.CachesDevNet;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Adaptador JPA del puerto {@link CatalogoTecnologias}.
 *
 * <p>Cacheado en {@link CachesDevNet#CATALOGO_TECNOLOGIAS}, la cache que se declaro
 * precisamente para esto: el catalogo se lee en cada busqueda filtrada y en cada carga
 * del filtro, y cambia casi nunca. El precio es que una tecnologia recien aprobada
 * tarda en aparecer lo que dure la entrada en la cache ({@code CacheConfig}).</p>
 *
 * <p>Devuelve objetos de dominio inmutables y no entidades: lo que se guarda en la
 * cache no debe estar atado a una sesion de Hibernate.</p>
 */
@Component
public class CatalogoTecnologiasJpa implements CatalogoTecnologias {

    private final TecnologiaJpaRepository tecnologias;

    public CatalogoTecnologiasJpa(TecnologiaJpaRepository tecnologias) {
        this.tecnologias = tecnologias;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(CachesDevNet.CATALOGO_TECNOLOGIAS)
    public List<Tecnologia> aprobadas() {
        return tecnologias.findByAprobadaTrueOrderByCategoriaAscNombreAsc().stream()
                .map(t -> new Tecnologia(t.getId(), t.getNombre(), t.getSlug(), t.getCategoria()))
                .toList();
    }
}
