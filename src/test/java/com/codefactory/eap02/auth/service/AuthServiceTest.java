package com.codefactory.eap02.auth.service;

import com.codefactory.eap02.auth.domain.LoginAttempt;
import com.codefactory.eap02.auth.domain.Usuario;
import com.codefactory.eap02.auth.repository.LoginAttemptRepository;
import com.codefactory.eap02.auth.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de AuthService.
 * Cobertura: CP-HU01-01, CP-HU01-02 (parcial, la validacion de formato/politica
 * vive en los DTO con Bean Validation, no en el service), CP-HU02-01, CP-HU02-02,
 * CP-HU02-03, CP-HU03-01, CP-HU03-02, CP-HU03-03.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private LoginAttemptRepository loginAttemptRepository;

    private AuthService authService;

    private static final String EMAIL = "login01@correo.test";
    private static final String PASSWORD_OK = "Prueba#2026x";
    private static final String PASSWORD_BAD = "IncorrectaX#1";

    @BeforeEach
    void setUp() {
        authService = new AuthService(usuarioRepository, passwordEncoder, jwtService, loginAttemptRepository);
    }

    private Usuario usuarioActivo() {
        Usuario u = new Usuario();
        u.setId(UUID.randomUUID());
        u.setEmail(EMAIL);
        u.setPasswordHash("hash");
        u.setFailedLoginAttempts(0);
        u.setLockedUntil(null);
        u.setCreatedAt(Instant.now());
        return u;
    }

    // ---------- CP-HU01-01 / CP-HU01-03: Registro ----------

    @Nested
    class Registro {

        @Test
        void registraUsuarioNuevoConCorreoNoExistente() {
            // NOTA: este test unitario solo prueba que el SERVICE delega el
            // hashing al PasswordEncoder inyectado y guarda el resultado tal cual.
            // No prueba que BCrypt produzca un hash valido: eso lo cubre el test
            // de integracion registraUsuarioConHashBCryptReal (sin mocks).
            when(usuarioRepository.existsByEmail("nuevo01@correo.test")).thenReturn(false);
            when(passwordEncoder.encode(PASSWORD_OK)).thenReturn("hashed-by-encoder");
            when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

            Usuario resultado = authService.register("nuevo01@correo.test", PASSWORD_OK);

            assertEquals("nuevo01@correo.test", resultado.getEmail());
            // El service debe usar EXACTAMENTE lo que devuelve el encoder, no su propia logica.
            assertEquals("hashed-by-encoder", resultado.getPasswordHash());
            verify(passwordEncoder).encode(PASSWORD_OK);
            verify(usuarioRepository).save(resultado);
        }

        @Test
        void rechazaRegistroConCorreoYaExistente() {
            when(usuarioRepository.existsByEmail("existente01@correo.test")).thenReturn(true);

            assertThrows(EmailAlreadyExistsException.class,
                    () -> authService.register("existente01@correo.test", PASSWORD_OK));

            verify(usuarioRepository, never()).save(any());
        }
    }

    // ---------- CP-HU02-01 / CP-HU02-02: Login credenciales ----------

    @Nested
    class Login {

        @Test
        void loginExitosoConCredencialesValidasEmiteToken() {
            Usuario u = usuarioActivo();
            when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(u));
            when(passwordEncoder.matches(PASSWORD_OK, u.getPasswordHash())).thenReturn(true);
            when(jwtService.generateToken(u)).thenReturn("jwt-token");

            LoginOutcome outcome = authService.login(EMAIL, PASSWORD_OK);

            assertTrue(outcome.success());
            assertFalse(outcome.locked());
            assertEquals("jwt-token", outcome.token());
            assertEquals(u, outcome.usuario());
        }

        @Test
        void loginConPasswordIncorrectaDevuelveInvalidCredentialsSinToken() {
            Usuario u = usuarioActivo();
            when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(u));
            when(passwordEncoder.matches(PASSWORD_BAD, u.getPasswordHash())).thenReturn(false);

            LoginOutcome outcome = authService.login(EMAIL, PASSWORD_BAD);

            assertFalse(outcome.success());
            assertFalse(outcome.locked());
            assertNull(outcome.token());
            verify(jwtService, never()).generateToken(any());
        }

        @Test
        void loginConUsuarioInexistenteDevuelveMismoResultadoQuePasswordIncorrecta() {
            // BUG ORIGINAL: este test comparaba contra LoginOutcome.invalidCredentials()
            // construido a mano, nunca ejecutaba el camino real de password incorrecta.
            // Un cambio que rompiera la equivalencia entre ambos caminos seguia pasando.
            // Ahora se ejecutan AMBOS caminos reales contra el service y se comparan entre si.
            Usuario u = usuarioActivo();
            when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(u));
            when(passwordEncoder.matches(PASSWORD_BAD, u.getPasswordHash())).thenReturn(false);
            when(usuarioRepository.findByEmail("inexistente99@correo.test")).thenReturn(Optional.empty());

            LoginOutcome outcomeInexistente = authService.login("inexistente99@correo.test", PASSWORD_OK);
            LoginOutcome outcomeClaveIncorrecta = authService.login(EMAIL, PASSWORD_BAD);

            assertEquals(outcomeClaveIncorrecta.success(), outcomeInexistente.success());
            assertEquals(outcomeClaveIncorrecta.locked(), outcomeInexistente.locked());
            assertEquals(outcomeClaveIncorrecta.token(), outcomeInexistente.token());
            assertEquals(outcomeClaveIncorrecta.usuario(), outcomeInexistente.usuario());
            assertEquals(outcomeClaveIncorrecta.retryAfterSeconds(), outcomeInexistente.retryAfterSeconds());
        }

        @Test
        void loginExitosoReiniciaContadorDeIntentosFallidosYDesbloqueo() {
            Usuario u = usuarioActivo();
            u.setFailedLoginAttempts(4);
            when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(u));
            when(passwordEncoder.matches(PASSWORD_OK, u.getPasswordHash())).thenReturn(true);
            when(jwtService.generateToken(u)).thenReturn("jwt-token");

            authService.login(EMAIL, PASSWORD_OK);

            assertEquals(0, u.getFailedLoginAttempts());
            assertNull(u.getLockedUntil());
            verify(usuarioRepository).save(u);
        }
    }

    // ---------- CP-HU02-03: Auditoria de intentos ----------

    @Nested
    class AuditoriaIntentos {

        @Test
        void cadaIntentoFallidoQuedaRegistradoSinPasswordEnTextoPlano() {
            Usuario u = usuarioActivo();
            when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(u));
            when(passwordEncoder.matches(PASSWORD_BAD, u.getPasswordHash())).thenReturn(false);

            authService.login(EMAIL, PASSWORD_BAD);

            ArgumentCaptor<LoginAttempt> captor = ArgumentCaptor.forClass(LoginAttempt.class);
            verify(loginAttemptRepository).save(captor.capture());
            LoginAttempt attempt = captor.getValue();

            assertEquals(EMAIL, attempt.getEmail());
            assertFalse(attempt.isSuccessful());
            assertEquals("INVALID_CREDENTIALS", attempt.getReason());
            // El objeto LoginAttempt no tiene campo password: verificacion estructural de que no se filtra.
        }

        @Test
        void loginExitosoRegistraIntentoComoSuccess() {
            Usuario u = usuarioActivo();
            when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(u));
            when(passwordEncoder.matches(PASSWORD_OK, u.getPasswordHash())).thenReturn(true);
            when(jwtService.generateToken(u)).thenReturn("jwt-token");

            authService.login(EMAIL, PASSWORD_OK);

            ArgumentCaptor<LoginAttempt> captor = ArgumentCaptor.forClass(LoginAttempt.class);
            verify(loginAttemptRepository).save(captor.capture());
            assertTrue(captor.getValue().isSuccessful());
            assertEquals("SUCCESS", captor.getValue().getReason());
        }

        @Test
        void loginConUsuarioInexistenteTambienQuedaRegistrado() {
            when(usuarioRepository.findByEmail("inexistente99@correo.test")).thenReturn(Optional.empty());

            authService.login("inexistente99@correo.test", PASSWORD_OK);

            verify(loginAttemptRepository).save(any(LoginAttempt.class));
        }
    }

    // ---------- CP-HU03-01 / CP-HU03-02 / CP-HU03-03: Bloqueo por intentos ----------

    @Nested
    class BloqueoPorIntentos {

        @Test
        void bloqueaCuentaAlAlcanzarElLimiteDeIntentosFallidos() {
            Usuario u = usuarioActivo();
            u.setFailedLoginAttempts(4); // al quinto fallo se bloquea
            when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(u));
            when(passwordEncoder.matches(PASSWORD_BAD, u.getPasswordHash())).thenReturn(false);

            authService.login(EMAIL, PASSWORD_BAD);

            assertEquals(5, u.getFailedLoginAttempts());
            assertNotNull(u.getLockedUntil());
            assertTrue(u.getLockedUntil().isAfter(Instant.now()));
        }

        @Test
        void noBloqueaCuentaPorDebajoDelLimite() {
            Usuario u = usuarioActivo();
            u.setFailedLoginAttempts(3); // pasa a 4to fallo, aun no bloquea
            when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(u));
            when(passwordEncoder.matches(PASSWORD_BAD, u.getPasswordHash())).thenReturn(false);

            authService.login(EMAIL, PASSWORD_BAD);

            assertEquals(4, u.getFailedLoginAttempts());
            assertNull(u.getLockedUntil());
        }

        @Test
        void cuentaBloqueadaRechazaInclusoConCredencialesCorrectas() {
            Usuario u = usuarioActivo();
            u.setLockedUntil(Instant.now().plus(Duration.ofMinutes(10)));
            when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(u));

            LoginOutcome outcome = authService.login(EMAIL, PASSWORD_OK);

            assertFalse(outcome.success());
            assertTrue(outcome.locked());
            assertNotNull(outcome.retryAfterSeconds());
            assertTrue(outcome.retryAfterSeconds() > 0);
            verify(passwordEncoder, never()).matches(any(), any());
            verify(jwtService, never()).generateToken(any());
        }

        @Test
        void cuentaBloqueadaRegistraIntentoConRazonAccountLocked() {
            Usuario u = usuarioActivo();
            u.setLockedUntil(Instant.now().plus(Duration.ofMinutes(10)));
            when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(u));

            authService.login(EMAIL, PASSWORD_OK);

            ArgumentCaptor<LoginAttempt> captor = ArgumentCaptor.forClass(LoginAttempt.class);
            verify(loginAttemptRepository).save(captor.capture());
            assertEquals("ACCOUNT_LOCKED", captor.getValue().getReason());
        }

        @Test
        void trasVencerElBloqueoPermiteLoginConCredencialesCorrectas() {
            Usuario u = usuarioActivo();
            u.setLockedUntil(Instant.now().minus(Duration.ofMinutes(1))); // ya vencido
            u.setFailedLoginAttempts(5);
            when(usuarioRepository.findByEmail(EMAIL)).thenReturn(Optional.of(u));
            when(passwordEncoder.matches(PASSWORD_OK, u.getPasswordHash())).thenReturn(true);
            when(jwtService.generateToken(u)).thenReturn("jwt-token");

            LoginOutcome outcome = authService.login(EMAIL, PASSWORD_OK);

            assertTrue(outcome.success());
            assertEquals(0, u.getFailedLoginAttempts());
            assertNull(u.getLockedUntil());
        }
    }
}
