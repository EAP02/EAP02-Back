package com.codefactory.devnet.administration.domain;

import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("HU-20 FiltroAuditoria (dominio)")
class FiltroAuditoriaTest {

    private static final RangoFechas SIN_RANGO = new RangoFechas(null, null);

    @Test
    @DisplayName("sin criterios, el filtro no acota nada")
    void sin_criterios() {
        FiltroAuditoria filtro = FiltroAuditoria.de(null, null, null, SIN_RANGO);

        assertThat(filtro.operacion()).isNull();
        assertThat(filtro.actorId()).isNull();
        assertThat(filtro.tabla()).isNull();
    }

    @Test
    @DisplayName("la operacion se acepta sin importar mayusculas ni espacios y se normaliza")
    void normaliza_la_operacion() {
        FiltroAuditoria filtro = FiltroAuditoria.de("  inicio_sesion_fallido ", null, null, SIN_RANGO);

        assertThat(filtro.operacion()).isEqualTo("INICIO_SESION_FALLIDO");
    }

    @Test
    @DisplayName("un texto en blanco cuenta como 'sin filtro', no como un valor")
    void texto_en_blanco_es_ausencia() {
        FiltroAuditoria filtro = FiltroAuditoria.de("   ", null, "  ", SIN_RANGO);

        assertThat(filtro.operacion()).isNull();
        assertThat(filtro.tabla()).isNull();
    }

    @Test
    @DisplayName("una operacion desconocida se rechaza y el mensaje lista las validas")
    void operacion_desconocida() {
        assertThatThrownBy(() -> FiltroAuditoria.de("BORRAR_TODO", null, null, SIN_RANGO))
                .isInstanceOfSatisfying(ExcepcionNegocio.class, ex -> {
                    assertThat(ex.codigo()).isEqualTo(CodigoErrorAdministracion.AUDITORIA_OPERACION_INVALIDA);
                    assertThat(ex.getMessage()).contains("INICIO_SESION", "DELETE");
                });
    }

    @Test
    @DisplayName("conserva el actor, la tabla y el rango tal como llegan")
    void conserva_actor_tabla_y_rango() {
        UUID actor = UUID.randomUUID();
        RangoFechas rango = new RangoFechas(java.time.LocalDate.of(2026, 11, 1), null);

        FiltroAuditoria filtro = FiltroAuditoria.de("DELETE", actor, " comentario ", rango);

        assertThat(filtro.actorId()).isEqualTo(actor);
        assertThat(filtro.tabla()).isEqualTo("comentario");
        assertThat(filtro.rango()).isEqualTo(rango);
    }
}
