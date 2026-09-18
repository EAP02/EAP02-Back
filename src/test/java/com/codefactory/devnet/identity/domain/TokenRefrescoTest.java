package com.codefactory.devnet.identity.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reglas del agregado {@link TokenRefresco}.
 *
 * <p>Pruebas unitarias puras: ni contexto de Spring ni base de datos. El recorrido
 * completo de la rotacion se verifica en {@code RefrescarSesionTest} y, de extremo a
 * extremo, en {@code RefrescoIT}.</p>
 */
@DisplayName("ADR-004 Token de refresco")
class TokenRefrescoTest {

    private static final Instant AHORA = Instant.parse("2026-09-18T12:00:00Z");
    private static final Duration SIETE_DIAS = Duration.ofDays(7);

    // ==================================================================
    @Nested
    @DisplayName("Vigencia")
    class Vigencia {

        @Test
        @DisplayName("recien emitido es utilizable")
        void recien_emitido_es_utilizable() {
            // Arrange
            TokenRefresco token = vigente();

            // Assert
            assertThat(token.esUtilizable(AHORA)).isTrue();
            assertThat(token.estaConsumido()).isFalse();
            assertThat(token.estaRevocado()).isFalse();
            assertThat(token.haExpirado(AHORA)).isFalse();
        }

        @Test
        @DisplayName("el instante exacto de expiracion ya cuenta como expirado")
        void la_frontera_de_expiracion_es_cerrada() {
            // Arrange
            TokenRefresco token = vigente();
            Instant momentoExacto = AHORA.plus(SIETE_DIAS);

            // Assert: expiraEn es el primer instante en que NO vale, no el ultimo en
            // que vale. Dejarlo abierto regalaria una ventana de un token entero.
            assertThat(token.haExpirado(momentoExacto)).isTrue();
            assertThat(token.haExpirado(momentoExacto.minusMillis(1))).isFalse();
            assertThat(token.esUtilizable(momentoExacto)).isFalse();
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Causas de rechazo")
    class Rechazo {

        @Test
        @DisplayName("consumido: es la senal de reuso")
        void consumido_no_es_utilizable() {
            // Arrange
            TokenRefresco token = new TokenRefresco(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    AHORA, AHORA.plus(SIETE_DIAS),
                    AHORA.plusSeconds(60),          // consumidoEn
                    null,
                    UUID.randomUUID());             // reemplazadoPor

            // Assert
            assertThat(token.estaConsumido()).isTrue();
            assertThat(token.esUtilizable(AHORA)).isFalse();
        }

        @Test
        @DisplayName("revocado: su familia cayo, o se cerro la sesion")
        void revocado_no_es_utilizable() {
            // Arrange
            TokenRefresco token = new TokenRefresco(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    AHORA, AHORA.plus(SIETE_DIAS),
                    null,
                    AHORA.plusSeconds(60),          // revocadoEn
                    null);

            // Assert
            assertThat(token.estaRevocado()).isTrue();
            assertThat(token.esUtilizable(AHORA)).isFalse();
        }

        @Test
        @DisplayName("un token revocado sigue revocado aunque no haya expirado")
        void revocado_manda_sobre_la_vigencia() {
            // Arrange: revocado hace un minuto, pero le quedan casi siete dias
            TokenRefresco token = new TokenRefresco(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    AHORA, AHORA.plus(SIETE_DIAS),
                    null, AHORA.plusSeconds(60), null);

            // Assert
            assertThat(token.haExpirado(AHORA.plusSeconds(120))).isFalse();
            assertThat(token.esUtilizable(AHORA.plusSeconds(120))).isFalse();
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Exposicion de datos")
    class Exposicion {

        @Test
        @DisplayName("el agregado no lleva el token ni su hash")
        void no_hay_nada_que_filtrar() {
            // Arrange
            TokenRefresco token = vigente();

            // Assert: este agregado acaba en logs y en mensajes de excepcion. Que no
            // exista un campo con el secreto es lo que hace imposible filtrarlo por
            // descuido; no depende de acordarse de sobrescribir toString().
            assertThat(TokenRefresco.class.getRecordComponents())
                    .extracting(java.lang.reflect.RecordComponent::getName)
                    .doesNotContain("tokenHash", "hash", "valor", "token");

            assertThat(token.toString()).doesNotContain("hash");
        }
    }

    // ------------------------------------------------------------------

    private static TokenRefresco vigente() {
        return new TokenRefresco(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                AHORA,
                AHORA.plus(SIETE_DIAS),
                null,
                null,
                null);
    }
}