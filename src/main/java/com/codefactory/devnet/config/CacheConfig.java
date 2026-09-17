package com.codefactory.devnet.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Cache en memoria con Caffeine.
 *
 * <p>No hay Redis en el alcance. El lineamiento 5.2 condiciona incorporar una
 * tecnologia especializada a que "el caso de uso y la capacidad operativa lo
 * justifiquen", y con un solo contenedor y una linea base de 300 solicitudes por
 * minuto no lo justifican. Si la medicion del sprint 3 dice lo contrario, se
 * documenta en un ADR nuevo y se incorpora: la abstraccion {@code @Cacheable} no
 * cambia.</p>
 *
 * <p>Solo se cachea lo que se lee mucho y se escribe poco. Nada de contenido de
 * usuario: una publicacion cacheada seguiria visible despues de retirarse.</p>
 */
@Configuration
public class CacheConfig {

    public static final String CATALOGO_TECNOLOGIAS = "catalogoTecnologias";
    public static final String PERMISOS_POR_ROL = "permisosPorRol";

    @Bean
    public CacheManager gestorCache() {
        CaffeineCacheManager gestor = new CaffeineCacheManager();
        gestor.setCaffeine(Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(Duration.ofMinutes(30))
                .recordStats());      // expone metricas a Micrometer
        gestor.setCacheNames(List.of(CATALOGO_TECNOLOGIAS, PERMISOS_POR_ROL));

        // Sin caches dinamicas: si un @Cacheable nombra una cache no declarada
        // arriba, debe fallar en el arranque y no crearla en silencio.
        gestor.setAllowNullValues(false);
        return gestor;
    }
}