package com.codefactory.devnet;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Infraestructura efimera para las pruebas de integracion.
 *
 * <p>{@code @ServiceConnection} sustituye la configuracion manual de
 * {@code spring.datasource.*}: Spring Boot toma la URL, el usuario y la clave del
 * contenedor ya arrancado. Por eso {@code application-test.yml} no declara conexion.</p>
 *
 * <p>Es PostgreSQL real, no H2. El modelo usa columnas generadas {@code tsvector},
 * indices parciales, CTE recursivos y restricciones {@code CHECK} con expresiones:
 * una base en memoria con otro dialecto ocultaria justo los errores que estas
 * pruebas existen para encontrar.</p>
 */
@TestConfiguration(proxyBeanMethods = false)
public class ConfiguracionContenedores {

    /** Misma version que docker-compose y que el proyecto Supabase. */
    private static final DockerImageName IMAGEN = DockerImageName.parse("postgres:16-alpine");

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>(IMAGEN)
                .withDatabaseName("devnet")
                .withUsername("devnet")
                .withPassword("devnet")
                // Reutiliza el contenedor entre ejecuciones locales si el usuario
                // activa testcontainers.reuse.enable en su ~/.testcontainers.properties.
                // En el pipeline se ignora y cada ejecucion arranca limpia.
                .withReuse(true);
    }
}