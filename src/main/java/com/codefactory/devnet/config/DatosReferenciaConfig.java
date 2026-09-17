package com.codefactory.devnet.config;

import com.codefactory.devnet.identity.infrastructure.PermisoEntity;
import com.codefactory.devnet.identity.infrastructure.PermisoRepositorio;
import com.codefactory.devnet.identity.infrastructure.RolEntity;
import com.codefactory.devnet.identity.infrastructure.RolRepositorio;
import com.codefactory.devnet.project.infrastructure.TecnologiaEntity;
import com.codefactory.devnet.project.infrastructure.TecnologiaJpaRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

@Configuration
public class DatosReferenciaConfig {

    @Bean
    ApplicationRunner datosReferencia(ReferenciaInitializer initializer) {
        return args -> initializer.inicializar();
    }

    @Bean
    ReferenciaInitializer referenciaInitializer(PermisoRepositorio permisos, RolRepositorio roles,
                                                 TecnologiaJpaRepository tecnologias) {
        return new ReferenciaInitializer(permisos, roles, tecnologias);
    }

    public static class ReferenciaInitializer {
        private final PermisoRepositorio permisos;
        private final RolRepositorio roles;
        private final TecnologiaJpaRepository tecnologias;

        ReferenciaInitializer(PermisoRepositorio permisos, RolRepositorio roles,
                              TecnologiaJpaRepository tecnologias) {
            this.permisos = permisos;
            this.roles = roles;
            this.tecnologias = tecnologias;
        }

        @Transactional
        public void inicializar() {
            PermisoEntity crear = permisos.findByCodigo("publicacion:crear")
                    .orElseGet(() -> permisos.save(PermisoEntity.nuevo(
                            "publicacion:crear", "Crear publicaciones")));
            if (!roles.existsByCodigo("DESARROLLADOR")) {
                RolEntity rol = RolEntity.nuevo("DESARROLLADOR", "Miembro desarrollador", false);
                rol.agregarPermiso(crear);
                roles.save(rol);
            }
            tecnologias.findBySlug("java")
                    .orElseGet(() -> tecnologias.save(TecnologiaEntity.nueva("Java", "java", "LENGUAJE")));
            tecnologias.findBySlug("spring-boot")
                    .orElseGet(() -> tecnologias.save(TecnologiaEntity.nueva("Spring Boot", "spring-boot", "FRAMEWORK")));
        }
    }
}
