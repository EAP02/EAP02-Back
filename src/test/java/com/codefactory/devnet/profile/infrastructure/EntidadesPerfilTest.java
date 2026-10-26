package com.codefactory.devnet.profile.infrastructure;

import com.codefactory.devnet.profile.domain.NivelTecnologia;
import com.codefactory.devnet.profile.domain.TecnologiaDeclarada;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Igualdad de las entidades JPA del perfil.
 *
 * <p>Importa porque las dos viven en colecciones que Hibernate compara: el stack es
 * un {@code Set} de embebibles, y un {@code equals} equivocado convertiria cambiar
 * un nivel en borrar e insertar la fila.</p>
 */
@DisplayName("Entidades de perfil")
class EntidadesPerfilTest {

    @Test
    @DisplayName("PerfilEntity: igual por usuario, distinta de otro usuario y de otros tipos")
    void perfil_igual_por_usuario() {
        // Arrange
        UUID usuario = UUID.randomUUID();
        PerfilEntity perfil = PerfilEntity.nuevo(usuario, "Ana", "Backend");
        PerfilEntity mismaInstancia = perfil;
        PerfilEntity mismaFila = PerfilEntity.nuevo(usuario, "Otro nombre", null);
        Object otroTipo = "Ana";

        // Act + Assert
        assertThat(perfil)
                .isEqualTo(mismaInstancia)
                .isEqualTo(mismaFila)
                .hasSameHashCodeAs(mismaFila)
                .isNotEqualTo(PerfilEntity.nuevo(UUID.randomUUID(), "Ana", "Backend"))
                .isNotEqualTo(otroTipo);
    }

    @Test
    @DisplayName("TecnologiaDeclaradaEmbeddable: la clave es la tecnologia, no el nivel")
    void tecnologia_igual_por_identificador() {
        // Arrange
        var java = TecnologiaDeclaradaEmbeddable.de(
                new TecnologiaDeclarada((short) 1, NivelTecnologia.BASICO, null));
        var mismaInstancia = java;
        var javaExperto = TecnologiaDeclaradaEmbeddable.de(
                new TecnologiaDeclarada((short) 1, NivelTecnologia.EXPERTO, (short) 8));
        var spring = TecnologiaDeclaradaEmbeddable.de(
                new TecnologiaDeclarada((short) 2, NivelTecnologia.BASICO, null));
        Object otroTipo = (short) 1;

        // Act + Assert
        assertThat(java)
                .isEqualTo(mismaInstancia)
                .isEqualTo(javaExperto)
                .hasSameHashCodeAs(javaExperto)
                .isNotEqualTo(spring)
                .isNotEqualTo(otroTipo);
    }
}
