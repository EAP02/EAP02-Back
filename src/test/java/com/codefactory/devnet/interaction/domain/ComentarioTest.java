package com.codefactory.devnet.interaction.domain;

import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * HU-13 sobre el dominio, sin Spring ni base de datos: las reglas de negocio de los
 * comentarios se prueban como las de {@code ProyectoTest}.
 */
@DisplayName("HU-13 Comentarios (dominio)")
class ComentarioTest {

    private final UUID publicacion = UUID.randomUUID();
    private final UUID autor = UUID.randomUUID();

    @Test
    @DisplayName("un comentario valido queda publicado y asociado a su autor y a la publicacion")
    void comentario_valido_queda_asociado() {
        // Act
        Comentario c = Comentario.nuevo(publicacion, autor, "  Buen aporte  ");

        // Assert
        assertThat(c.autorId()).isEqualTo(autor);
        assertThat(c.publicacionId()).isEqualTo(publicacion);
        assertThat(c.contenido()).isEqualTo("Buen aporte");
        assertThat(c.estaVisible()).isTrue();
    }

    @Test
    @DisplayName("un comentario vacio se rechaza")
    void comentario_vacio_se_rechaza() {
        // Act + Assert
        assertThatThrownBy(() -> Comentario.nuevo(publicacion, autor, ""))
                .isInstanceOf(ExcepcionNegocio.class)
                .extracting(e -> ((ExcepcionNegocio) e).codigo())
                .isEqualTo(CodigoErrorInteraccion.COMENTARIO_VACIO);
    }

    @Test
    @DisplayName("un comentario solo con espacios tambien se rechaza")
    void comentario_solo_espacios_se_rechaza() {
        // Act + Assert
        assertThatThrownBy(() -> Comentario.nuevo(publicacion, autor, "   \n  "))
                .isInstanceOf(ExcepcionNegocio.class)
                .extracting(e -> ((ExcepcionNegocio) e).codigo())
                .isEqualTo(CodigoErrorInteraccion.COMENTARIO_VACIO);
    }

    @Test
    @DisplayName("un comentario nulo se rechaza")
    void comentario_nulo_se_rechaza() {
        // Act + Assert
        assertThatThrownBy(() -> Comentario.nuevo(publicacion, autor, null))
                .isInstanceOf(ExcepcionNegocio.class);
    }

    @Test
    @DisplayName("un comentario que supera la longitud maxima se rechaza")
    void comentario_demasiado_largo_se_rechaza() {
        // Arrange
        String largo = "a".repeat(Comentario.LONGITUD_MAXIMA + 1);

        // Act + Assert
        assertThatThrownBy(() -> Comentario.nuevo(publicacion, autor, largo))
                .isInstanceOf(ExcepcionNegocio.class)
                .extracting(e -> ((ExcepcionNegocio) e).codigo())
                .isEqualTo(CodigoErrorInteraccion.COMENTARIO_DEMASIADO_LARGO);
    }

    @Test
    @DisplayName("eliminar deja el comentario en ELIMINADO y deja de estar visible")
    void eliminado_deja_de_estar_visible() {
        // Arrange
        Comentario c = Comentario.nuevo(publicacion, autor, "Para borrar");

        // Act
        Comentario eliminado = c.eliminado();

        // Assert
        assertThat(eliminado.estado()).isEqualTo(Comentario.Estado.ELIMINADO);
        assertThat(eliminado.estaVisible()).isFalse();
        assertThat(eliminado.id()).isEqualTo(c.id());
    }

    @Test
    @DisplayName("el autor puede eliminar su propio comentario")
    void autor_puede_eliminar_lo_suyo() {
        // Act + Assert
        assertThat(PoliticaComentario.puedeEliminar(autor, autor, false)).isTrue();
    }

    @Test
    @DisplayName("quien no es autor ni administrador no puede eliminarlo")
    void tercero_no_puede_eliminar() {
        // Act + Assert
        assertThat(PoliticaComentario.puedeEliminar(autor, UUID.randomUUID(), false)).isFalse();
    }

    @Test
    @DisplayName("un administrador puede eliminar comentarios ajenos y queda como intervencion")
    void administrador_puede_eliminar_ajenos() {
        // Arrange
        UUID admin = UUID.randomUUID();

        // Act + Assert
        assertThat(PoliticaComentario.puedeEliminar(autor, admin, true)).isTrue();
        assertThat(PoliticaComentario.esIntervencionDeAdministrador(autor, admin)).isTrue();
        assertThat(PoliticaComentario.esIntervencionDeAdministrador(autor, autor)).isFalse();
    }

    @Test
    @DisplayName("una peticion anonima nunca puede eliminar")
    void anonimo_no_puede_eliminar() {
        // Act + Assert
        assertThat(PoliticaComentario.puedeEliminar(autor, null, true)).isFalse();
    }
}
