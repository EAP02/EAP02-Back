package com.codefactory.devnet.shared.cache;

/**
 * Nombres de las caches declaradas.
 *
 * <p>Son constantes y no un enum porque {@code @Cacheable} exige una constante de
 * compilacion en la anotacion.</p>
 *
 * <p><b>Viven en {@code shared} y no junto al {@code CacheConfig} que las declara.</b>
 * Quien anota un metodo con {@code @Cacheable} es un modulo de negocio; quien construye
 * el {@code CacheManager} es {@code config}. Si el nombre vive en {@code config},
 * cachear algo obliga al modulo a importar el paquete de configuracion, que es
 * justamente la arista que la regla 7 de {@code FronterasModularesTest} prohibe.</p>
 *
 * <p>La lista de aqui debe coincidir con la que {@code CacheConfig} pasa a
 * {@code setCacheNames}: un {@code @Cacheable} que nombre una cache no declarada falla
 * en el arranque, que es el comportamiento buscado.</p>
 */
public final class CachesDevNet {

    /** Catalogo de tecnologias aprobadas. Se lee en cada alta de perfil, cambia casi nunca. */
    public static final String CATALOGO_TECNOLOGIAS = "catalogoTecnologias";

    /** Permisos efectivos de cada rol. Se resuelve en cada emision de token. */
    public static final String PERMISOS_POR_ROL = "permisosPorRol";

    private CachesDevNet() {
    }
}