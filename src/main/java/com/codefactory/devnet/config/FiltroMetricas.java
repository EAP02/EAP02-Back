package com.codefactory.devnet.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Protege {@code /actuator/prometheus} con un token propio del scraper (ADR-010).
 *
 * <p>Prometheus no puede usar el JWT de un usuario: caduca a los 15 minutos y un scraper
 * no inicia sesion. Tampoco se deja el endpoint abierto: las metricas revelan rutas,
 * volumenes y errores. Por eso este filtro exige {@code Authorization: Bearer <token>}
 * con un secreto compartido que vive en {@code METRICAS_TOKEN} (lineamiento 7.3:
 * secretos fuera del codigo).</p>
 *
 * <p>Solo actua sobre esa ruta. Cualquier otro endpoint de Actuator sigue exigiendo un
 * JWT valido en {@link SeguridadConfig}. Si el token configurado esta vacio, el filtro
 * rechaza todo: falla cerrado.</p>
 */
@Component
public class FiltroMetricas extends OncePerRequestFilter {

    static final String RUTA = "/actuator/prometheus";
    private static final String ESQUEMA = "Bearer ";

    private final byte[] esperado;

    public FiltroMetricas(@Value("${devnet.observabilidad.token-metricas:}") String token) {
        this.esperado = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !RUTA.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain cadena) throws ServletException, IOException {
        if (tokenValido(request.getHeader("Authorization"))) {
            cadena.doFilter(request, response);
            return;
        }
        response.setHeader("WWW-Authenticate", "Bearer");
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
    }

    private boolean tokenValido(String cabecera) {
        if (esperado.length == 0 || cabecera == null || !cabecera.startsWith(ESQUEMA)) {
            return false;
        }
        byte[] recibido = cabecera.substring(ESQUEMA.length()).trim().getBytes(StandardCharsets.UTF_8);
        // Comparacion en tiempo constante: no filtra cuantos caracteres coinciden.
        return MessageDigest.isEqual(esperado, recibido);
    }
}
