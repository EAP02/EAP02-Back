package com.codefactory.devnet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Punto de entrada de DevNet.
 *
 * <p>Monolito modular: un solo artefacto desplegable con siete modulos de negocio
 * de fronteras explicitas mas el kernel {@code shared}. Ver
 * {@code docs/adr/ADR-001-estilo-arquitectonico.md}.</p>
 *
 * <p>Los modulos no se importan entre si: sus contratos viven en
 * {@code shared.integration} y ArchUnit rompe el build si alguno cruza la frontera.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableCaching
@EnableJpaAuditing
public class DevNetApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevNetApplication.class, args);
    }
}