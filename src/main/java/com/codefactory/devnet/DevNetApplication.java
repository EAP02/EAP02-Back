package com.codefactory.devnet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Punto de entrada único del monolito modular DevNet. */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableCaching
@EnableJpaAuditing
public class DevNetApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevNetApplication.class, args);
    }
}
