package com.codefactory.devnet.project.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Igualdad de las entidades JPA de proyectos.
 *
 * <p>Todas comparan por identificador: dos instancias sin persistir no son la misma
 * fila aunque tengan los mismos datos.</p>
 */
@DisplayName("Entidades de proyecto")
class EntidadesProyectoTest {

    @Test
    @DisplayName("PublicacionEntity: igual por id, distinta de otro id y de otros tipos")
    void publicacion_igual_por_id() {
        // Arrange
        UUID id = UUID.randomUUID();
        Instant ahora = Instant.now();
        PublicacionEntity publicacion = publicacion(id, ahora);
        PublicacionEntity mismaInstancia = publicacion;
        Object otroTipo = id;

        // Act + Assert
        assertThat(publicacion)
                .isEqualTo(mismaInstancia)
                .isEqualTo(publicacion(id, ahora))
                .hasSameHashCodeAs(publicacion(id, ahora))
                .isNotEqualTo(publicacion(UUID.randomUUID(), ahora))
                .isNotEqualTo(otroTipo);
    }

    @Test
    @DisplayName("RepositorioEntity: cada enlace es una fila distinta aunque apunte a la misma URL")
    void repositorio_igual_por_id() {
        // Arrange
        UUID proyecto = UUID.randomUUID();
        RepositorioEntity repo = RepositorioEntity.de(proyecto, "https://github.com/ana/motor");
        RepositorioEntity mismaInstancia = repo;
        Object otroTipo = "https://github.com/ana/motor";

        // Act + Assert
        assertThat(repo)
                .isEqualTo(mismaInstancia)
                .isNotEqualTo(RepositorioEntity.de(proyecto, "https://github.com/ana/motor"))
                .isNotEqualTo(otroTipo);
    }

    @Test
    @DisplayName("sin identificador, PublicacionProyectoEntity y TecnologiaEntity solo son iguales a si mismas")
    void sin_identificador() {
        // Arrange: el constructor protegido de JPA, que es como Hibernate las instancia
        PublicacionProyectoEntity detalle = new PublicacionProyectoEntity();
        PublicacionProyectoEntity mismoDetalle = detalle;
        TecnologiaEntity tecnologia = new TecnologiaEntity();
        TecnologiaEntity mismaTecnologia = tecnologia;
        Object otroTipo = "java";

        // Act + Assert
        assertThat(detalle).isEqualTo(mismoDetalle).isNotEqualTo(new PublicacionProyectoEntity())
                .hasSameHashCodeAs(new PublicacionProyectoEntity());
        assertThat(tecnologia).isEqualTo(mismaTecnologia).isNotEqualTo(new TecnologiaEntity())
                .isNotEqualTo(otroTipo);
    }

    private static PublicacionEntity publicacion(UUID id, Instant publicadoEn) {
        return PublicacionEntity.proyectoPublicado(id, UUID.randomUUID(), "Motor de plantillas",
                "Un motor de plantillas minimalista.", publicadoEn);
    }
}
