package com.codefactory.devnet.config;

import com.codefactory.devnet.shared.config.PropiedadesDevNet;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.List;

/**
 * Cadena de seguridad HTTP.
 *
 * <p>HU-06: la autorizacion se verifica en el servidor en cada operacion protegida,
 * incluso ante peticiones directas al endpoint (lineamiento 6.2). No hay ninguna ruta
 * que confie en el cliente.</p>
 */
@Configuration
@EnableMethodSecurity   // habilita @PreAuthorize: RBAC por endpoint (lineamiento 3.4)
public class SeguridadConfig {

    /** Rutas abiertas. Todo lo que no este aqui exige autenticacion. */
    private static final String[] PUBLICAS = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/api/v1/auth/inicio-sesion",
            "/api/v1/auth/registro",
            "/api/v1/auth/refresco",
            // Publico a proposito: quien vuelve pasados 15 minutos tiene el token de
            // acceso caducado y aun asi debe poder cerrar su sesion. La credencial que
            // autoriza la operacion es la cookie de refresco, no el Bearer.
            "/api/v1/auth/cierre-sesion",
            "/oauth2/**",
            "/login/oauth2/**"
    };

    /**
     * Documentacion del contrato, abierta en todos los entornos.
     *
     * <p>ADR-007: sin frontend propio, Swagger UI es la unica superficie de interaccion
     * del producto. Exponer el contrato no concede acceso; cada operacion protegida
     * sigue exigiendo su permiso.</p>
     */
    private static final String[] DOCUMENTACION = {
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**"
    };

    private final PropiedadesDevNet propiedades;
    private final HandlerExceptionResolver resolvedorExcepciones;

    public SeguridadConfig(PropiedadesDevNet propiedades,
                           @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolvedorExcepciones) {
        this.propiedades = propiedades;
        this.resolvedorExcepciones = resolvedorExcepciones;
    }

    @Bean
    public SecurityFilterChain cadenaFiltros(HttpSecurity http,
                                             JwtAuthenticationConverter convertidor) throws Exception {
        http
            // Sin sesion de servidor: la identidad viaja en el token. Es lo que
            // permite correr mas de una instancia sin sesion pegajosa (RNF-04).
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // CSRF protege formularios con sesion por cookie. El grueso de la API no
            // tiene ese vector: sin estado y con el token en cabecera.
            //
            // Con dos excepciones, /api/v1/auth/refresco y /cierre-sesion, que si
            // aceptan una credencial en cookie. Ahi el vector existe, pero lo que un
            // tercero puede provocar es una rotacion o un cierre de sesion ajenos, no
            // leer nada: CORS le impide ver la respuesta, y por tanto tambien el token
            // nuevo. Lo cierra SameSite=Strict, que impide que la cookie viaje en una
            // peticion originada en otro sitio.
            //
            // Si alguna vez hay que pasar a SameSite=None -frontend en otro dominio-,
            // esta linea deja de ser defendible y hay que revisar el ADR-004.
            .csrf(csrf -> csrf.disable())

            .cors(Customizer.withDefaults())

            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(PUBLICAS).permitAll()
                    .requestMatchers(DOCUMENTACION).permitAll()
                    // Lectura publica del contenido: el caso contempla visitantes.
                    .requestMatchers(HttpMethod.GET, "/api/v1/proyectos/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/perfiles/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/tecnologias/**").permitAll()
                    // Metricas: nunca abiertas, ni siquiera en integracion.
                    .requestMatchers("/actuator/**").authenticated()
                    .anyRequest().authenticated())

            .oauth2ResourceServer(oauth -> oauth
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(convertidor)))

            // Delega 401 y 403 al manejador global, para que salgan con el cuerpo
            // uniforme de ADR-005 en vez de la pagina por defecto de Spring. Es
            // ademas el punto donde se registra el intento denegado que pide HU-06.
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint((req, res, e) ->
                            resolvedorExcepciones.resolveException(req, res, null, e))
                    .accessDeniedHandler((req, res, e) ->
                            resolvedorExcepciones.resolveException(req, res, null, e)));

        return http.build();
    }

    /**
     * Traduce el claim {@code permisos} del token en autoridades de Spring Security.
     *
     * <p>Sin prefijo: la autoridad es literalmente {@code publicacion:moderar}, de
     * modo que {@code @PreAuthorize("hasAuthority('publicacion:moderar')")} se lee
     * igual que la fila de {@code rol_permiso} que la concede.</p>
     *
     * <p>Por defecto Spring buscaria {@code scope} o {@code scp} y antepondria
     * {@code SCOPE_}; ninguna de las dos cosas sirve aqui.</p>
     */
    @Bean
    public JwtAuthenticationConverter convertidorJwt() {
        JwtGrantedAuthoritiesConverter autoridades = new JwtGrantedAuthoritiesConverter();
        autoridades.setAuthoritiesClaimName("permisos");
        autoridades.setAuthorityPrefix("");

        JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter(autoridades);
        return convertidor;
    }

    /**
     * Argon2id, no BCrypt.
     *
     * <p>Es el algoritmo recomendado actualmente por su resistencia a ataques con
     * hardware dedicado. Parametros segun OWASP: 19 MiB de memoria, 2 iteraciones,
     * 1 hilo. El costo en tiempo es deliberado y esta contemplado en el presupuesto
     * de latencia de autenticacion (800 ms p95).</p>
     */
    @Bean
    public PasswordEncoder codificadorClaves() {
        return new Argon2PasswordEncoder(16, 32, 1, 19 * 1024, 2);
    }

    @Bean
    public CorsConfigurationSource configuracionCors() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(propiedades.seguridad().origenesPermitidos());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Trace-Id"));
        config.setExposedHeaders(List.of("X-Trace-Id"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", config);
        return fuente;
    }
}
