package com.codefactory.devnet.identity.application;

import com.codefactory.devnet.identity.domain.CodigoErrorIdentidad;
import com.codefactory.devnet.identity.domain.EmisorTokens;
import com.codefactory.devnet.identity.domain.EstadoUsuario;
import com.codefactory.devnet.identity.domain.GeneradorTokenRefresco;
import com.codefactory.devnet.identity.domain.RepositorioTokensRefresco;
import com.codefactory.devnet.identity.domain.RepositorioUsuarios;
import com.codefactory.devnet.identity.domain.Rol;
import com.codefactory.devnet.identity.domain.TokenRefresco;
import com.codefactory.devnet.identity.domain.Usuario;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import com.codefactory.devnet.shared.audit.EventoAuditoria;
import com.codefactory.devnet.shared.audit.RegistroAuditoria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ADR-004 - rotacion del token de refresco y deteccion de reuso.
 *
 * <p>Pruebas con dobles: lo que se verifica aqui es la <b>secuencia de decisiones</b>,
 * que es donde vive el riesgo. Que la rotacion sea atomica bajo concurrencia depende del
 * {@code UPDATE} condicional y solo se puede comprobar contra PostgreSQL, en
 * {@code RefrescoIT}.</p>
 */
@DisplayName("ADR-004 Refrescar sesion")
class RefrescarSesionTest {

    private static final String VALOR = "un-refresco-en-claro";
    private static final String HASH = "el-hash-del-refresco";
    private static final String IP = "203.0.113.7";
    private static final String AGENTE = "curl/8.0";

    private static final Rol DESARROLLADOR =
            new Rol("DESARROLLADOR", false, Set.of("publicacion:crear"));

    private RepositorioTokensRefresco tokens;
    private RepositorioUsuarios usuarios;
    private GeneradorTokenRefresco generador;
    private EmitirRefresco emisorRefresco;
    private EmisorTokens emisor;
    private RegistroAuditoria auditoria;

    private RefrescarSesion refrescar;

    @BeforeEach
    void preparar() {
        tokens = mock(RepositorioTokensRefresco.class);
        usuarios = mock(RepositorioUsuarios.class);
        generador = mock(GeneradorTokenRefresco.class);
        emisorRefresco = mock(EmitirRefresco.class);
        emisor = mock(EmisorTokens.class);
        auditoria = mock(RegistroAuditoria.class);

        refrescar = new RefrescarSesion(
                tokens, usuarios, generador, emisorRefresco, emisor, auditoria);

        when(generador.hashDe(VALOR)).thenReturn(HASH);
    }

    // ==================================================================
    @Nested
    @DisplayName("Rotacion correcta")
    class Rotacion {

        @Test
        @DisplayName("devuelve token de acceso nuevo y refresco nuevo")
        void rota_y_devuelve_ambos() {
            // Arrange
            TokenRefresco token = vigente();
            Usuario usuario = usuarioActivo(token.usuarioId());

            when(tokens.porHash(HASH)).thenReturn(Optional.of(token));
            when(usuarios.porId(token.usuarioId())).thenReturn(Optional.of(usuario));
            when(emisorRefresco.rotar(eq(token), any(), eq(IP), eq(AGENTE)))
                    .thenReturn(Optional.of("refresco-sucesor"));
            when(emisor.emitir(usuario)).thenReturn(
                    new EmisorTokens.TokenAcceso("jwt-nuevo", Instant.now(), 900));

            // Act
            RefrescarSesion.Resultado resultado = refrescar.ejecutar(VALOR, IP, AGENTE);

            // Assert
            assertThat(resultado.token().valor()).isEqualTo("jwt-nuevo");
            assertThat(resultado.refrescoEnClaro()).isEqualTo("refresco-sucesor");
            assertThat(resultado.usuario()).isEqualTo(usuario);

            // La familia NO se revoca en el camino feliz.
            verify(tokens, never()).revocarFamilia(any(), any());
        }

        @Test
        @DisplayName("el sucesor hereda la familia: es lo que sostiene la revocacion en cascada")
        void la_familia_se_hereda() {
            // Arrange
            TokenRefresco token = vigente();
            Usuario usuario = usuarioActivo(token.usuarioId());

            when(tokens.porHash(HASH)).thenReturn(Optional.of(token));
            when(usuarios.porId(token.usuarioId())).thenReturn(Optional.of(usuario));
            when(emisorRefresco.rotar(any(), any(), any(), any()))
                    .thenReturn(Optional.of("refresco-sucesor"));
            when(emisor.emitir(usuario)).thenReturn(
                    new EmisorTokens.TokenAcceso("jwt", Instant.now(), 900));

            // Act
            refrescar.ejecutar(VALOR, IP, AGENTE);

            // Assert: se le pasa a EmitirRefresco el token ANTERIOR, de donde sale la
            // familia. Si aqui se colara un token con familia nueva, revocar ante un
            // reuso solo alcanzaria al ultimo eslabon y la deteccion no serviria.
            ArgumentCaptor<TokenRefresco> capturado = ArgumentCaptor.forClass(TokenRefresco.class);
            verify(emisorRefresco).rotar(capturado.capture(), any(), eq(IP), eq(AGENTE));

            assertThat(capturado.getValue().familia()).isEqualTo(token.familia());
        }

        @Test
        @DisplayName("queda registrado en auditoria")
        void audita_la_rotacion() {
            // Arrange
            TokenRefresco token = vigente();
            Usuario usuario = usuarioActivo(token.usuarioId());

            when(tokens.porHash(HASH)).thenReturn(Optional.of(token));
            when(usuarios.porId(token.usuarioId())).thenReturn(Optional.of(usuario));
            when(emisorRefresco.rotar(any(), any(), any(), any()))
                    .thenReturn(Optional.of("sucesor"));
            when(emisor.emitir(usuario)).thenReturn(
                    new EmisorTokens.TokenAcceso("jwt", Instant.now(), 900));

            // Act
            refrescar.ejecutar(VALOR, IP, AGENTE);

            // Assert
            ArgumentCaptor<EventoAuditoria> evento = ArgumentCaptor.forClass(EventoAuditoria.class);
            verify(auditoria).registrar(evento.capture());

            assertThat(evento.getValue().operacion())
                    .isEqualTo(EventoAuditoria.Operacion.REFRESCO_ROTADO);
            assertThat(evento.getValue().registroId()).isEqualTo(token.familia().toString());
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Deteccion de reuso")
    class Reuso {

        @Test
        @DisplayName("un token consumido revoca la familia ANTES de lanzar")
        void consumido_revoca_antes_de_lanzar() {
            // Arrange
            TokenRefresco consumido = con(Instant.now().minusSeconds(60), null);
            when(tokens.porHash(HASH)).thenReturn(Optional.of(consumido));

            // Act
            assertThatThrownBy(() -> refrescar.ejecutar(VALOR, IP, AGENTE))
                    .isInstanceOf(ExcepcionNegocio.class)
                    .hasFieldOrPropertyWithValue("codigo", CodigoErrorIdentidad.AUTH_REFRESCO_REUSADO);

            // Assert: el ORDEN es la prueba. Si la revocacion ocurriera despues de
            // lanzar, no ocurriria nunca; y si viajara en la transaccion de la
            // peticion, el rollback la desharia. De ahi el REQUIRES_NEW del adaptador.
            var orden = inOrder(tokens, auditoria);
            orden.verify(tokens).revocarFamilia(eq(consumido.familia()), any());
            orden.verify(auditoria).registrar(any());

            // No se llega a emitir nada.
            verify(emisorRefresco, never()).rotar(any(), any(), any(), any());
            verify(emisor, never()).emitir(any());
        }

        @Test
        @DisplayName("perder la carrera al rotar tambien es reuso")
        void rotacion_vacia_revoca_la_familia() {
            // Arrange: dos peticiones simultaneas con el mismo refresco. Esta pasa el
            // control de 'consumido' y pierde el UPDATE condicional.
            TokenRefresco token = vigente();
            Usuario usuario = usuarioActivo(token.usuarioId());

            when(tokens.porHash(HASH)).thenReturn(Optional.of(token));
            when(usuarios.porId(token.usuarioId())).thenReturn(Optional.of(usuario));
            when(emisorRefresco.rotar(any(), any(), any(), any())).thenReturn(Optional.empty());

            // Act
            assertThatThrownBy(() -> refrescar.ejecutar(VALOR, IP, AGENTE))
                    .isInstanceOf(ExcepcionNegocio.class)
                    .hasFieldOrPropertyWithValue("codigo", CodigoErrorIdentidad.AUTH_REFRESCO_REUSADO);

            // Assert
            verify(tokens).revocarFamilia(eq(token.familia()), any());
            verify(emisor, never()).emitir(any());
        }

        @Test
        @DisplayName("un token ya revocado NO se vuelve a revocar ni se audita como reuso")
        void revocado_no_repite_la_alarma() {
            // Arrange
            TokenRefresco revocado = con(null, Instant.now().minusSeconds(60));
            when(tokens.porHash(HASH)).thenReturn(Optional.of(revocado));

            // Act
            assertThatThrownBy(() -> refrescar.ejecutar(VALOR, IP, AGENTE))
                    .hasFieldOrPropertyWithValue("codigo", CodigoErrorIdentidad.AUTH_REFRESCO_REVOCADO);

            // Assert: es la consecuencia esperada de un reuso anterior o de un cierre
            // de sesion. Auditarlo de nuevo llenaria la cola de alarmas con ecos del
            // mismo incidente.
            verify(tokens, never()).revocarFamilia(any(), any());
            verify(auditoria, never()).registrar(any());
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Rechazos sin alarma")
    class Rechazos {

        @Test
        @DisplayName("sin cookie: AUTH_REFRESCO_AUSENTE")
        void sin_cookie() {
            assertThatThrownBy(() -> refrescar.ejecutar(null, IP, AGENTE))
                    .hasFieldOrPropertyWithValue("codigo", CodigoErrorIdentidad.AUTH_REFRESCO_AUSENTE);

            assertThatThrownBy(() -> refrescar.ejecutar("   ", IP, AGENTE))
                    .hasFieldOrPropertyWithValue("codigo", CodigoErrorIdentidad.AUTH_REFRESCO_AUSENTE);

            verify(tokens, never()).porHash(any());
        }

        @Test
        @DisplayName("token inexistente: AUTH_REFRESCO_INVALIDO")
        void token_desconocido() {
            when(tokens.porHash(HASH)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> refrescar.ejecutar(VALOR, IP, AGENTE))
                    .hasFieldOrPropertyWithValue("codigo", CodigoErrorIdentidad.AUTH_REFRESCO_INVALIDO);
        }

        @Test
        @DisplayName("token vencido: AUTH_REFRESCO_EXPIRADO")
        void token_expirado() {
            // Arrange: emitido hace ocho dias, con vigencia de siete
            Instant hace8dias = Instant.now().minus(Duration.ofDays(8));
            TokenRefresco expirado = new TokenRefresco(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    hace8dias, hace8dias.plus(Duration.ofDays(7)),
                    null, null, null);

            when(tokens.porHash(HASH)).thenReturn(Optional.of(expirado));

            // Act + Assert
            assertThatThrownBy(() -> refrescar.ejecutar(VALOR, IP, AGENTE))
                    .hasFieldOrPropertyWithValue("codigo", CodigoErrorIdentidad.AUTH_REFRESCO_EXPIRADO);

            verify(tokens, never()).revocarFamilia(any(), any());
        }
    }

    // ==================================================================
    @Nested
    @DisplayName("Estado de la cuenta")
    class Cuenta {

        @Test
        @DisplayName("una cuenta desactivada deja de renovar y pierde la familia")
        void desactivada_no_renueva() {
            // Arrange
            TokenRefresco token = vigente();
            Usuario desactivado = new Usuario(token.usuarioId(), "alguien", "hash",
                    EstadoUsuario.DESACTIVADO, false, (short) 0, null, Set.of(DESARROLLADOR));

            when(tokens.porHash(HASH)).thenReturn(Optional.of(token));
            when(usuarios.porId(token.usuarioId())).thenReturn(Optional.of(desactivado));

            // Act + Assert
            assertThatThrownBy(() -> refrescar.ejecutar(VALOR, IP, AGENTE))
                    .hasFieldOrPropertyWithValue("codigo", CodigoErrorIdentidad.AUTH_CUENTA_DESACTIVADA);

            // Esta es la razon de ser del refresco como mecanismo de revocacion: el JWT
            // de 15 minutos no puede reevaluar el estado de la cuenta, y este si.
            verify(tokens).revocarFamilia(eq(token.familia()), any());
            verify(emisor, never()).emitir(any());
        }

        @Test
        @DisplayName("un usuario que ya no existe no filtra que el token era valido")
        void usuario_inexistente() {
            TokenRefresco token = vigente();
            when(tokens.porHash(HASH)).thenReturn(Optional.of(token));
            when(usuarios.porId(token.usuarioId())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> refrescar.ejecutar(VALOR, IP, AGENTE))
                    .hasFieldOrPropertyWithValue("codigo", CodigoErrorIdentidad.AUTH_REFRESCO_INVALIDO);
        }
    }

    // ------------------------------------------------------------------

    private static TokenRefresco vigente() {
        return con(null, null);
    }

    private static TokenRefresco con(Instant consumidoEn, Instant revocadoEn) {
        Instant ahora = Instant.now();
        return new TokenRefresco(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                ahora.minus(Duration.ofHours(1)),
                ahora.plus(Duration.ofDays(6)),
                consumidoEn,
                revocadoEn,
                null);
    }

    private static Usuario usuarioActivo(UUID id) {
        return new Usuario(id, "usuario_prueba", "hash",
                EstadoUsuario.ACTIVO, false, (short) 0, null, Set.of(DESARROLLADOR));
    }
}