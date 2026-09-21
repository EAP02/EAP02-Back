package com.codefactory.eap02.auth.controller;

import com.codefactory.eap02.auth.domain.LoginAttempt;
import com.codefactory.eap02.auth.domain.Usuario;
import com.codefactory.eap02.auth.repository.LoginAttemptRepository;
import com.codefactory.eap02.auth.repository.UsuarioRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


/**
 * Pruebas de integracion sobre AuthController + AuthService + JPA (H2, sin mocks).
 * Cobertura: CP-HU01-01, CP-HU01-02, CP-HU01-03, CP-HU02-01, CP-HU02-02, CP-HU02-03,
 * CP-HU03-01.
 *
 * Revision aplicada sobre la version anterior (ver Test Suite Review):
 * - loginConUsuarioInexistenteDevuelveMismoErrorQueClaveIncorrecta ahora compara
 *   dos ejecuciones REALES via HTTP en vez de un objeto construido a mano.
 * - El token JWT se decodifica y valida (firma, subject, claims, expiracion) en vez
 *   de solo comprobar que no esta vacio; se agrega un test de firma alterada.
 * - Se verifica el hash de password con el PasswordEncoder REAL (BCrypt), confirmando
 *   forma y que la contrasena original verifica contra el.
 * - Los 400 de validacion ahora verifican que el campo mencionado en el error sea el
 *   esperado (email o password), no solo el codigo de estado.
 * - Se agrega un test de concurrencia real (registros simultaneos con el mismo correo)
 *   que solo pasa si existe una restriccion UNIQUE efectiva en la base de datos.
 * - El estado de bloqueo se verifica releyendo la entidad desde la BD, no solo desde
 *   la respuesta HTTP de la misma peticion.
 * - Se agregan escenarios end-to-end explicitos: registro->login->JWT valido,
 *   fallos repetidos->bloqueo->clave correcta rechazada, y bloqueo vencido->login ok.
 */

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JsonMapper objectMapper;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private LoginAttemptRepository loginAttemptRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Value("${jwt.secret}")
    private String jwtSecret;


    private static final String REGISTER_URL = "/api/v1/auth/register";
    private static final String LOGIN_URL = "/api/v1/auth/login";

    @BeforeEach
    void limpiarDatos() {
        loginAttemptRepository.deleteAll();
        usuarioRepository.deleteAll();
    }

    private void crearUsuario(String email, String rawPassword) {
        var u = new com.codefactory.eap02.auth.domain.Usuario();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(rawPassword));
        usuarioRepository.save(u);
    }

    // ---------- CP-HU01-01 ----------

    @Nested
    class Registro {

        @Test
        void registraCuentaConDatosValidos() throws Exception {
            var body = Map.of("email", "nuevo01@correo.test", "password", "Prueba#2026x");

            mockMvc.perform(post(REGISTER_URL)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.email").value("nuevo01@correo.test"));

            // Verifica persistencia REAL leyendo de vuelta desde la BD, no solo el existsByEmail
            // (que podria pasar si el registro fallara silenciosamente pero el flag quedara mal puesto).
            Usuario guardado = usuarioRepository.findByEmail("nuevo01@correo.test").orElse(null);
            assertNotNull(guardado, "El usuario debe existir en la base de datos tras el registro");
            assertEquals("nuevo01@correo.test", guardado.getEmail());
            assertEquals(1, usuarioRepository.count());
        }

        @Test
        void registraUsuarioConHashBCryptRealNoTextoPlanoYVerificable() throws Exception {
            // Prueba de integracion SIN mocks: usa el PasswordEncoder real (BCrypt) para
            // confirmar que el hash guardado es un BCrypt valido y que la contrasena
            // original puede verificarse contra el, no solo que "no es igual al texto plano".
            var body = Map.of("email", "hash01@correo.test", "password", "Prueba#2026x");

            mockMvc.perform(post(REGISTER_URL)
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(body)));

            Usuario guardado = usuarioRepository.findByEmail("hash01@correo.test").orElseThrow();
            String hash = guardado.getPasswordHash();

            assertNotEquals("Prueba#2026x", hash);
            assertTrue(hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$"),
                    "El hash debe tener forma de BCrypt, era: " + hash);
            assertTrue(passwordEncoder.matches("Prueba#2026x", hash),
                    "La contrasena original debe verificar correctamente contra el hash guardado");
            assertFalse(passwordEncoder.matches("otraCosa#1", hash),
                    "Una contrasena distinta no debe verificar contra el hash");
        }

        @Test
        void rechazaRegistroConCorreoYaExistente() throws Exception {
            crearUsuario("existente01@correo.test", "Prueba#2026x");
            var body = Map.of("email", "existente01@correo.test", "password", "OtraClave#1");

            mockMvc.perform(post(REGISTER_URL)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("EMAIL_ALREADY_EXISTS"));

            assertEquals(1, usuarioRepository.count());
        }

        @Test
        void dosRegistrosSimultaneosConMismoCorreoSoloCreanUnaCuenta() throws Exception {
            // Cobertura de concurrencia (ausente en la version anterior): el test secuencial
            // de correo duplicado no prueba nada sobre una condicion de carrera real, porque
            // el existsByEmail() del primer request ya devuelve true para el segundo cuando
            // se ejecutan uno despues del otro. Aqui se disparan en paralelo para forzar que
            // ambos pasen el chequeo existsByEmail() antes de que cualquiera haga save(),
            // lo cual solo es seguro si hay una restriccion UNIQUE real en la base de datos.
            var body = Map.of("email", "concurrente01@correo.test", "password", "Prueba#2026x");
            String json = objectMapper.writeValueAsString(body);
            int hilos = 8;

            ExecutorService pool = Executors.newFixedThreadPool(hilos);
            List<Callable<Integer>> tareas = new ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                tareas.add(() -> mockMvc.perform(post(REGISTER_URL)
                                .contentType("application/json")
                                .content(json))
                        .andReturn().getResponse().getStatus());
            }

            List<Future<Integer>> resultados = pool.invokeAll(tareas);
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);

            long exitosos = 0;
            for (Future<Integer> f : resultados) {
                int status = f.get();
                assertTrue(status == 201 || status == 409,
                        "Cada respuesta debe ser 201 (creado) o 409 (conflicto), fue: " + status);
                if (status == 201) exitosos++;
            }

            assertEquals(1, exitosos, "Exactamente una peticion concurrente debe haber creado la cuenta");
            assertEquals(1, usuarioRepository.count(),
                    "La base de datos no debe terminar con cuentas duplicadas para el mismo correo. "
                            + "Si este test falla, falta una restriccion UNIQUE efectiva a nivel de base de datos "
                            + "(el chequeo existsByEmail() en el service no es suficiente bajo concurrencia).");
        }

        @Test
        void rechazaRegistroConCorreoDeFormatoInvalido() throws Exception {
            // ANTES: solo status().isBadRequest(), que tambien pasaria si el 400 viniera
            // de un JSON malformado, un campo faltante, o cualquier otro motivo no
            // relacionado con el formato del correo. AHORA se verifica que el campo con
            // error sea especificamente "email", para confirmar que fue ESA regla la que
            // disparo el rechazo.
            var body = Map.of("email", "usuario.correo.test", "password", "Prueba#2026x");

            String respuesta = mockMvc.perform(post(REGISTER_URL)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest())
                    .andReturn().getResponse().getContentAsString();

            assertTrue(respuesta.toLowerCase().contains("email"),
                    "El error 400 debe mencionar el campo 'email' como causa; respuesta: " + respuesta);
            assertFalse(usuarioRepository.existsByEmail("usuario.correo.test"));
        }

        @Test
        void rechazaRegistroConPasswordMuyCorta() throws Exception {
            // ANTES: solo verificaba 400 + que no se creo la cuenta con ESE correo exacto.
            // AHORA ademas confirma que el error senala especificamente el campo "password"
            // (y no, por ejemplo, que el JSON este mal formado) y que no se crea NINGUNA
            // cuenta como efecto colateral.
            var body = Map.of("email", "formato02@correo.test", "password", "Ab#1");

            String respuesta = mockMvc.perform(post(REGISTER_URL)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest())
                    .andReturn().getResponse().getContentAsString();

            assertTrue(respuesta.toLowerCase().contains("password"),
                    "El error 400 debe mencionar el campo 'password' como causa; respuesta: " + respuesta);
            assertEquals(0, usuarioRepository.count());
        }

        @Test
        void servidorRechazaPeticionConCamposVacios() throws Exception {
            var body = Map.of("email", "", "password", "");

            mockMvc.perform(post(REGISTER_URL)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ---------- CP-HU02-01 / CP-HU02-02 / CP-HU02-03 ----------

    @Nested
    class Login {

        @BeforeEach
        void seedUsuario() {
            crearUsuario("login01@correo.test", "Prueba#2026x");
        }

        @Test
        void loginConCredencialesValidasEmiteToken() throws Exception {
            var body = Map.of("email", "login01@correo.test", "password", "Prueba#2026x");

            mockMvc.perform(post(LOGIN_URL)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andExpect(jsonPath("$.usuario.email").value("login01@correo.test"));
        }

        @Test
        void tokenEmitidoEsUnJwtValidoFirmadoConLaClaveDeLaAppYConClaimsCorrectos() throws Exception {
            // ANTES: solo se probaba token.isNotEmpty(), lo cual pasa con cualquier string.
            // AHORA: se decodifica el JWT con la MISMA clave de firma que usa la app (via
            // JwtService/application-test.properties) y se valida su estructura y claims.
            // Esto falla si, por ejemplo, el token se firma con una clave distinta, no
            // incluye el subject/email correcto, o no es un JWT en absoluto.
            var body = Map.of("email", "login01@correo.test", "password", "Prueba#2026x");

            String respuesta = mockMvc.perform(post(LOGIN_URL)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            JsonNode json = objectMapper.readTree(respuesta);
            String token = json.get("token").asString();
            String usuarioId = json.get("usuario").get("id").asString();

            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
            var claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            assertEquals(usuarioId, claims.getSubject());
            assertEquals("login01@correo.test", claims.get("email", String.class));
            assertNotNull(claims.getIssuedAt());
            assertNotNull(claims.getExpiration());
            assertTrue(claims.getExpiration().after(new java.util.Date()),
                    "El token no debe estar expirado inmediatamente despues de emitirse");
        }

        @Test
        void tokenConFirmaAlteradaEsRechazadoPorElParser() throws Exception {
            // Complementa el test anterior: si alguien manipula el payload/firma, la
            // verificacion con la clave real de la app debe fallar. Esto es lo minimo
            // que demuestra que el JWT realmente protege algo y no es solo una cadena
            // aleatoria etiquetada "token".
            var body = Map.of("email", "login01@correo.test", "password", "Prueba#2026x");

            String respuesta = mockMvc.perform(post(LOGIN_URL)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(body)))
                    .andReturn().getResponse().getContentAsString();

            String token = objectMapper.readTree(respuesta).get("token").asString();
            // Se corrompe el ultimo caracter de la firma
            String tokenAlterado = token.substring(0, token.length() - 1)
                    + (token.charAt(token.length() - 1) == 'A' ? 'B' : 'A');

            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
            assertThrows(io.jsonwebtoken.security.SignatureException.class,
                    () -> Jwts.parser().verifyWith(key).build().parseSignedClaims(tokenAlterado),
                    "Un token con firma alterada debe ser rechazado, no aceptado silenciosamente");
        }

        @Test
        void loginConPasswordIncorrectaDevuelveErrorGenerico401() throws Exception {
            var body = Map.of("email", "login01@correo.test", "password", "IncorrectaX#1");

            mockMvc.perform(post(LOGIN_URL)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
        }

        @Test
        void loginConUsuarioInexistenteDevuelveMismoErrorQueClaveIncorrecta() throws Exception {
            var bodyInexistente = Map.of("email", "inexistente99@correo.test", "password", "Prueba#2026x");
            var bodyClaveMala = Map.of("email", "login01@correo.test", "password", "IncorrectaX#1");

            var respInexistente = mockMvc.perform(post(LOGIN_URL)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(bodyInexistente)))
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();

            var respClaveMala = mockMvc.perform(post(LOGIN_URL)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(bodyClaveMala)))
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();

            assertEquals(respInexistente, respClaveMala);
        }

        @Test
        void cadaIntentoFallidoQuedaRegistradoEnAuditoria() throws Exception {
            var body = Map.of("email", "login01@correo.test", "password", "IncorrectaX#1");

            mockMvc.perform(post(LOGIN_URL)
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(body)));

            List<LoginAttempt> intentos = loginAttemptRepository.findAll();
            assertEquals(1, intentos.size());
            assertFalse(intentos.get(0).isSuccessful());
            assertEquals("login01@correo.test", intentos.get(0).getEmail());
        }

        @Test
        void loginExitosoNoSeContabilizaComoIntentoFallido() throws Exception {
            var body = Map.of("email", "login01@correo.test", "password", "Prueba#2026x");

            mockMvc.perform(post(LOGIN_URL)
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(body)));

            List<LoginAttempt> intentos = loginAttemptRepository.findAll();
            assertEquals(1, intentos.size());
            assertTrue(intentos.get(0).isSuccessful());
        }
    }

    // ---------- CP-HU03-01 ----------

    @Nested
    class BloqueoPorIntentos {

        @BeforeEach
        void seedUsuario() {
            crearUsuario("bloqueo01@correo.test", "Prueba#2026x");
        }

        @Test
        void bloqueaCuentaTrasSuperarLimiteDeIntentosFallidos() throws Exception {
            var bodyMalo = Map.of("email", "bloqueo01@correo.test", "password", "IncorrectaX#1");

            for (int i = 0; i < 5; i++) {
                mockMvc.perform(post(LOGIN_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(bodyMalo)));
            }

            mockMvc.perform(post(LOGIN_URL)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(bodyMalo)))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.errorCode").value("ACCOUNT_LOCKED"))
                    .andExpect(jsonPath("$.retryAfterSeconds").isNumber());

            // Verifica que el bloqueo quedo PERSISTIDO en la base de datos, no solo
            // reflejado en la respuesta HTTP de esa peticion.
            Usuario u = usuarioRepository.findByEmail("bloqueo01@correo.test").orElseThrow();
            assertNotNull(u.getLockedUntil(), "lockedUntil debe estar persistido en la BD");
            assertTrue(u.getLockedUntil().isAfter(java.time.Instant.now()));
            assertTrue(u.getFailedLoginAttempts() >= 5);

            long registrosBloqueo = loginAttemptRepository.findAll().stream()
                    .filter(a -> "ACCOUNT_LOCKED".equals(a.getReason()))
                    .count();
            assertEquals(1, registrosBloqueo,
                    "Solo el intento que causo/encontro el bloqueo debe registrarse como ACCOUNT_LOCKED");
        }

        @Test
        void cuentaBloqueadaRechazaInclusoConCredencialesCorrectas() throws Exception {
            var bodyMalo = Map.of("email", "bloqueo01@correo.test", "password", "IncorrectaX#1");
            var bodyBueno = Map.of("email", "bloqueo01@correo.test", "password", "Prueba#2026x");

            for (int i = 0; i < 5; i++) {
                mockMvc.perform(post(LOGIN_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(bodyMalo)));
            }

            mockMvc.perform(post(LOGIN_URL)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(bodyBueno)))
                    .andExpect(status().isTooManyRequests());
        }
    }

    // ---------- Escenarios de comportamiento end-to-end (feedback: faltaban) ----------

    @Nested
    class EscenariosEndToEnd {

        @Test
        void flujoCompletoRegistroLoginRecibeJwtValido() throws Exception {
            var registro = Map.of("email", "e2e01@correo.test", "password", "Prueba#2026x");
            mockMvc.perform(post(REGISTER_URL)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(registro)))
                    .andExpect(status().isCreated());

            var login = Map.of("email", "e2e01@correo.test", "password", "Prueba#2026x");
            String respuesta = mockMvc.perform(post(LOGIN_URL)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(login)))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            String token = objectMapper.readTree(respuesta).get("token").asString();
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
            var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            assertEquals("e2e01@correo.test", claims.get("email", String.class));
        }

        @Test
        void flujoFallosRepetidosLuegoBloqueoLuegoCredencialesCorrectasSiguenRechazadas() throws Exception {
            crearUsuario("e2e02@correo.test", "Prueba#2026x");
            var bodyMalo = Map.of("email", "e2e02@correo.test", "password", "Mala#1234");
            var bodyBueno = Map.of("email", "e2e02@correo.test", "password", "Prueba#2026x");

            for (int i = 0; i < 5; i++) {
                mockMvc.perform(post(LOGIN_URL)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(bodyMalo)));
            }

            // Cuenta bloqueada: ni siquiera la clave correcta debe pasar
            mockMvc.perform(post(LOGIN_URL)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(bodyBueno)))
                    .andExpect(status().isTooManyRequests());

            Usuario u = usuarioRepository.findByEmail("e2e02@correo.test").orElseThrow();
            assertNotNull(u.getLockedUntil());
        }

        @Test
        void flujoBloqueoVencidoPermiteLoginConCredencialesCorrectas() throws Exception {
            // Simula que el periodo de bloqueo ya vencio manipulando directamente el
            // estado persistido (equivalente a "esperar 15 minutos" en un test rapido).
            crearUsuario("e2e03@correo.test", "Prueba#2026x");
            Usuario u = usuarioRepository.findByEmail("e2e03@correo.test").orElseThrow();
            u.setFailedLoginAttempts(5);
            u.setLockedUntil(java.time.Instant.now().minusSeconds(5)); // vencido
            usuarioRepository.save(u);

            var bodyBueno = Map.of("email", "e2e03@correo.test", "password", "Prueba#2026x");

            mockMvc.perform(post(LOGIN_URL)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(bodyBueno)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty());

            Usuario despues = usuarioRepository.findByEmail("e2e03@correo.test").orElseThrow();
            assertEquals(0, despues.getFailedLoginAttempts());
            assertNull(despues.getLockedUntil());
        }
    }
}
