package com.codefactory.devnet.shared.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Genera o propaga el identificador de correlacion de cada peticion.
 *
 * <p>Es la pieza que hace posible la trazabilidad de ADR-005 y del lineamiento 4.3:
 * el mismo valor aparece en la respuesta HTTP, en el log JSON y en la columna
 * {@code auditoria.trace_id}. Un usuario que reporta un fallo entrega ese valor y
 * el equipo llega al log exacto sin buscar a ciegas.</p>
 *
 * <p>Se ejecuta antes que cualquier otro filtro, incluida la cadena de seguridad,
 * para que hasta un 401 salga correlacionado.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FiltroTraceId extends OncePerRequestFilter {

    public static final String CABECERA = "X-Trace-Id";
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_RUTA = "ruta";
    public static final String MDC_METODO = "metodo";

    /**
     * Un traceId entrante se acepta solo si es hexadecimal de 8 a 64 caracteres.
     * Sin esta validacion, un cliente podria inyectar saltos de linea y falsificar
     * entradas en el log.
     */
    private static final Pattern FORMATO_VALIDO = Pattern.compile("^[0-9a-fA-F]{8,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest peticion,
                                    HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {
        String traceId = resolver(peticion.getHeader(CABECERA));
        try {
            MDC.put(MDC_TRACE_ID, traceId);
            MDC.put(MDC_RUTA, peticion.getRequestURI());
            MDC.put(MDC_METODO, peticion.getMethod());
            respuesta.setHeader(CABECERA, traceId);
            cadena.doFilter(peticion, respuesta);
        } finally {
            // Se limpia siempre: el hilo vuelve al pool y no debe arrastrar
            // el contexto de la peticion anterior.
            MDC.remove(MDC_TRACE_ID);
            MDC.remove(MDC_RUTA);
            MDC.remove(MDC_METODO);
        }
    }

    private String resolver(String entrante) {
        if (entrante != null && FORMATO_VALIDO.matcher(entrante).matches()) {
            return entrante.toLowerCase();
        }
        return generar();
    }

    private String generar() {
        return Long.toHexString(UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE);
    }

    /** Devuelve el traceId de la peticion en curso, o un marcador si no hay contexto. */
    public static String actual() {
        String valor = MDC.get(MDC_TRACE_ID);
        return valor != null ? valor : "sin-traza";
    }
}