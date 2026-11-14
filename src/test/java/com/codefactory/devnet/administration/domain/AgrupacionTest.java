package com.codefactory.devnet.administration.domain;

import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("HU-19 Agrupacion (dominio)")
class AgrupacionTest {

    @Test
    @DisplayName("sin valor, o en blanco, agrupa por semana")
    void por_defecto_semana() {
        assertThat(Agrupacion.de(null)).isEqualTo(Agrupacion.SEMANA);
        assertThat(Agrupacion.de("  ")).isEqualTo(Agrupacion.SEMANA);
    }

    @Test
    @DisplayName("acepta SEMANA y MES sin importar mayusculas ni espacios")
    void acepta_los_dos_valores() {
        assertThat(Agrupacion.de(" mes ")).isEqualTo(Agrupacion.MES);
        assertThat(Agrupacion.de("Semana")).isEqualTo(Agrupacion.SEMANA);
    }

    @Test
    @DisplayName("un valor desconocido se rechaza con AGRUPACION_INVALIDA")
    void desconocido() {
        assertThatThrownBy(() -> Agrupacion.de("ANIO"))
                .isInstanceOfSatisfying(ExcepcionNegocio.class, ex ->
                        assertThat(ex.codigo()).isEqualTo(CodigoErrorAdministracion.AGRUPACION_INVALIDA));
    }
}
