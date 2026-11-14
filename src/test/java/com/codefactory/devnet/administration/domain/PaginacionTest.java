package com.codefactory.devnet.administration.domain;

import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Paginacion y Pagina (dominio)")
class PaginacionTest {

    @Test
    @DisplayName("el desplazamiento es pagina por tamano")
    void desplazamiento() {
        assertThat(new Paginacion(3, 20).desplazamiento()).isEqualTo(60);
    }

    @Test
    @DisplayName("los limites validos: tamano 1 y tamano 100, pagina 0")
    void limites_validos() {
        assertThat(new Paginacion(0, 1)).isNotNull();
        assertThat(new Paginacion(0, Paginacion.TAMANO_MAXIMO)).isNotNull();
    }

    @ParameterizedTest(name = "pagina {0}, tamano {1} se rechaza")
    @CsvSource({"-1,20", "0,0", "0,101", "0,-5"})
    @DisplayName("pagina negativa o tamano fuera de 1..100 se rechaza con PAGINACION_INVALIDA")
    void fuera_de_rango(int pagina, int tamano) {
        assertThatThrownBy(() -> new Paginacion(pagina, tamano))
                .isInstanceOfSatisfying(ExcepcionNegocio.class, ex ->
                        assertThat(ex.codigo()).isEqualTo(CodigoErrorAdministracion.PAGINACION_INVALIDA));
    }

    @Test
    @DisplayName("el total de paginas redondea hacia arriba y una pagina vacia tiene 0")
    void total_de_paginas() {
        assertThat(new Pagina<>(List.of(1, 2), 0, 2, 5).totalPaginas()).isEqualTo(3);
        assertThat(new Pagina<>(List.of(1, 2), 0, 2, 4).totalPaginas()).isEqualTo(2);
        assertThat(new Pagina<>(List.<Integer>of(), 0, 20, 0).totalPaginas()).isZero();
    }
}
