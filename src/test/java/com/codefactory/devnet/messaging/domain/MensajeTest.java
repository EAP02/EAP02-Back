package com.codefactory.devnet.messaging.domain;

import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MensajeTest {
    @Test
    void acepta_texto_y_conserva_su_contenido() {
        assertThat(Mensaje.validarContenido(" Hola ")).isEqualTo(" Hola ");
    }

    @Test
    void rechaza_vacio_espacios_y_exceso_de_longitud_sin_revelar_el_texto() {
        for (String contenido : new String[]{null, "", "  \t  ", "x".repeat(4001)}) {
            assertThatThrownBy(() -> Mensaje.validarContenido(contenido))
                    .isInstanceOf(ExcepcionNegocio.class)
                    .hasMessage(CodigoErrorMensajeria.MENSAJE_CONTENIDO_INVALIDO.mensajePorDefecto());
        }
    }
}
