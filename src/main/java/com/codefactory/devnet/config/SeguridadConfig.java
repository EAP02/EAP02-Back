package com.codefactory.devnet.config;

import jakarta.servlet.http.HttpServletRequest;
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
            "/api/v1/auth/refresco",
            "/oauth2/**",
            "/login/oauth2/**"
    };

    /** Documentacion del contrato. Deshabilitada en produccion por configuracion. */
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

            // CSRF protege formularios con sesion por cookie. Una API sin estado y
            // con token en cabecera no tiene ese vector.
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

    /** IP real del cliente. Render termina TLS en su proxy. */
    public static String ipDe(HttpServletRequest http) {
        String reenviada = http.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            // Solo el primer valor: el resto de la cadena lo puede falsificar el cliente.
            return reenviada.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }
}