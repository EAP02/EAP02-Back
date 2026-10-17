package com.codefactory.devnet.interaction.domain;

import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("HU-14 TipoReaccion (dominio)")
class TipoReaccionTest {

    @Test
    @DisplayName("sin tipo, la reaccion es un ME_GUSTA")
    void sin_tipo_es_me_gusta() {
        // Arrange + Act + Assert
        assertThat(TipoReaccion.de(null)).isEqualTo(TipoReaccion.ME_GUSTA);
        assertThat(TipoReaccion.de("   ")).isEqualTo(TipoReaccion.ME_GUSTA);
    }

    @Test
    @DisplayName("acepta los tres tipos de la base, sin importar mayusculas ni espacios")
    void acepta_los_tipos_validos() {
        assertThat(TipoReaccion.de("util")).isEqualTo(TipoReaccion.UTIL);
        assertThat(TipoReaccion.de(" INNOVADOR ")).isEqualTo(TipoReaccion.INNOVADOR);
        assertThat(TipoReaccion.de("Me_Gusta")).isEqualTo(TipoReaccion.ME_GUSTA);
    }

    @Test
    @DisplayName("un tipo desconocido se rechaza con REACCION_TIPO_INVALIDO")
    void tipo_desconocido_se_rechaza() {
        // Act + Assert
        assertThatThrownBy(() -> TipoReaccion.de("ODIO"))
                .isInstanceOf(ExcepcionNegocio.class)
                .extracting(e -> ((ExcepcionNegocio) e).codigo().codigo())
                .isEqualTo("REACCION_TIPO_INVALIDO");
    }
}
