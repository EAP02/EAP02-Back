package com.codefactory.devnet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Cadena de seguridad HTTP.
 *
 * <p>Implementa lo que ADR-004 decidio: identidad propia en Spring Security, sin
 * estado de sesion, con la autorizacion evaluada en el servidor para cada operacion
 * protegida (lineamiento 6.2).</p>
 *
 * <p><b>Estado actual:</b> la validacion de JWT todavia no esta conectada. Los
 * endpoints publicos responden y el resto devuelve 401 a traves del manejador
 * global. La emision y verificacion de tokens llega con la historia de
 * autenticacion; hasta entonces el proyecto arranca y se puede trabajar sobre los
 * demas modulos.</p>
 */
@Configuration
@EnableMethodSecurity   // habilita @PreAuthorize: RBAC por endpoint (lineamiento 3.4)
public class SeguridadConfig {

    /** Rutas abiertas. Todo lo que no este aqui exige autenticacion. */
    private static final String[] PUBLICAS = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/api/v1/auth/registro",
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

    public SeguridadConfig(PropiedadesDevNet propiedades) {
        this.propiedades = propiedades;
    }

    @Bean
    public SecurityFilterChain cadenaFiltros(HttpSecurity http) throws Exception {
        http
            // Sin sesion de servidor: la identidad viaja en el token. Es lo que
            // permite correr mas de una instancia sin sesion pegajosa (RNF-04).
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // CSRF protege formularios con sesion por cookie. Una API sin estado y
            // con token en cabecera no tiene ese vector. El refresco si va en cookie,
            // pero es SameSite=Strict y solo lo consume su propio endpoint.
            .csrf(csrf -> csrf.disable())

            .cors(Customizer.withDefaults())

            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(PUBLICAS).permitAll()
                    .requestMatchers(DOCUMENTACION).permitAll()
                    // Lectura publica del contenido: el caso contempla visitantes.
                    .requestMatchers(HttpMethod.GET, "/api/v1/publicaciones/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/perfiles/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/v1/tecnologias/**").permitAll()
                    // Metricas: nunca abiertas, ni siquiera en integracion.
                    .requestMatchers("/actuator/**").authenticated()
                    .anyRequest().authenticated())

            // Delega 401 y 403 al manejador global para que salgan con el cuerpo
            // uniforme de ADR-005 en vez de la pagina por defecto de Spring.
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint((req, res, e) -> { throw e; })
                    .accessDeniedHandler((req, res, e) -> { throw e; }));

        return http.build();
    }

    /**
     * Argon2id, no BCrypt.
     *
     * <p>Es el algoritmo recomendado actualmente para derivacion de contrasenas por
     * su resistencia a ataques con hardware dedicado. Los parametros son los que
     * OWASP sugiere: 19 MiB de memoria, 2 iteraciones, 1 hilo. El costo en tiempo es
     * deliberado y esta contemplado en el presupuesto de latencia de autenticacion
     * (800 ms p95).</p>
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
        config.setAllowCredentials(true);   // el refresco viaja en cookie
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", config);
        return fuente;
    }
}