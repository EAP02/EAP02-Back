package com.codefactory.devnet.shared.api;

import com.codefactory.devnet.shared.audit.EventoAuditoria;
import com.codefactory.devnet.shared.audit.RegistroAuditoria;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Los casos del manejador global que no se pueden provocar desde fuera con una
 * peticion normal: conflictos de la base, errores inesperados y el actor de un
 * acceso denegado. Los que si se pueden provocar estan en {@code ErroresApiIT}.
 */
@DisplayName("ManejadorGlobalErrores")
class ManejadorGlobalErroresTest {

    private RegistroAuditoria auditoria;
    private ManejadorGlobalErrores manejador;
    private MockHttpServletRequest peticion;

    @BeforeEach
    void preparar() {
        auditoria = mock(RegistroAuditoria.class);
        manejador = new ManejadorGlobalErrores(auditoria);
        peticion = new MockHttpServletRequest("PUT", "/api/v1/proyectos/123");
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    // ==================================================================
    @Nested
    @DisplayName("conflictos y errores inesperados")
    class Conflictos {

        @Test
        @DisplayName("bloqueo optimista: 409 CONFLICTO_CONCURRENCIA")
        void concurrencia() {
            // Act
            var respuesta = manejador.concurrencia(new OptimisticLockingFailureException("version 3"), peticion);

            // Assert
            assertRespuesta(respuesta, 409, CodigoErrorComun.CONFLICTO_CONCURRENCIA);
        }

        @Test
        @DisplayName("violacion de integridad: 409 CONFLICTO_DATOS sin filtrar el detalle de la base")
        void integridad() {
            // Act
            var respuesta = manejador.integridad(
                    new DataIntegrityViolationException("Key (correo)=(ana@ejemplo.com) already exists"), peticion);

            // Assert
            assertRespuesta(respuesta, 409, CodigoErrorComun.CONFLICTO_DATOS);
            assertThat(respuesta.getBody().message()).doesNotContain("ana@ejemplo.com");
        }

        @Test
        @DisplayName("error no controlado: 500 ERROR_INTERNO sin exponer la excepcion")
        void inesperado() {
            // Act
            var respuesta = manejador.inesperado(new IllegalStateException("detalle interno"), peticion);

            // Assert
            assertRespuesta(respuesta, 500, CodigoErrorComun.ERROR_INTERNO);
            assertThat(respuesta.getBody().message()).doesNotContain("detalle interno");
            assertThat(respuesta.getBody().path()).isEqualTo("/api/v1/proyectos/123");
        }

        @Test
        @DisplayName("restriccion sobre un parametro: 400 VALIDACION_FALLIDA con el campo")
        void restriccion() {
            // Arrange
            ConstraintViolationException ex;
            try (ValidatorFactory fabrica = Validation.buildDefaultValidatorFactory()) {
                Validator validador = fabrica.getValidator();
                ex = new ConstraintViolationException(validador.validate(new Filtro(" ")));
            }

            // Act
            var respuesta = manejador.restriccion(ex, peticion);

            // Assert
            assertRespuesta(respuesta, 400, CodigoErrorComun.VALIDACION_FALLIDA);
            assertThat(respuesta.getBody().details()).extracting(DetalleError::campo).containsExactly("texto");
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("acceso denegado")
    class AccesoDenegado {

        @Test
        @DisplayName("audita el intento a nombre del usuario del token")
        void audita_al_actor() {
            // Arrange
            UUID actor = UUID.randomUUID();
            autenticarCon(actor.toString());

            // Act
            var respuesta = manejador.accesoDenegado(new AccessDeniedException("no"), peticion);

            // Assert
            assertRespuesta(respuesta, 403, CodigoErrorComun.ACCESO_DENEGADO);
            assertThat(eventoAuditado().actorId()).isEqualTo(actor);
        }

        @Test
        @DisplayName("sin sesion, audita con actor nulo")
        void anonimo() {
            // Act
            manejador.accesoDenegado(new AccessDeniedException("no"), peticion);

            // Assert
            assertThat(eventoAuditado().actorId()).isNull();
        }

        @Test
        @DisplayName("un sub que no es UUID no rompe la respuesta: audita con actor nulo")
        void sub_invalido() {
            // Arrange
            autenticarCon("no-es-un-uuid");

            // Act
            var respuesta = manejador.accesoDenegado(new AccessDeniedException("no"), peticion);

            // Assert
            assertRespuesta(respuesta, 403, CodigoErrorComun.ACCESO_DENEGADO);
            assertThat(eventoAuditado().actorId()).isNull();
        }
    }

    // ------------------------------------------------------------------

    private record Filtro(@NotBlank String texto) { }

    private static void assertRespuesta(ResponseEntity<RespuestaError> respuesta, int estado,
                                        CodigoErrorComun codigo) {
        assertThat(respuesta.getStatusCode().value()).isEqualTo(estado);
        assertThat(respuesta.getBody()).isNotNull();
        assertThat(respuesta.getBody().errorCode()).isEqualTo(codigo.codigo());
        assertThat(respuesta.getBody().message()).isEqualTo(codigo.mensajePorDefecto());
    }

    private static void autenticarCon(String sub) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(sub)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    private EventoAuditoria eventoAuditado() {
        ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoria).registrar(evento.capture());
        return evento.getValue();
    }
}
