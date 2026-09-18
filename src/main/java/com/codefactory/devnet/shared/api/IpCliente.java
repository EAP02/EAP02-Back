package com.codefactory.devnet.shared.api;

import jakarta.servlet.http.HttpServletRequest;

/**
 * IP de origen de una peticion.
 *
 * <p>Render termina TLS en su proxy, asi que {@code getRemoteAddr()} devuelve la IP del
 * balanceador y no la del cliente. La real llega en {@code X-Forwarded-For}.</p>
 *
 * <p><b>Solo se toma el primer valor de la cadena.</b> El cliente puede enviar su propia
 * cabecera {@code X-Forwarded-For}, y el proxy le antepone la IP observada en lugar de
 * reemplazarla: todo lo que venga despues del primer elemento lo escribio alguien no
 * confiable. Quedarse con el resto seria dejar que el origen de un evento de auditoria
 * lo elija quien lo provoca.</p>
 *
 * <p>Vive en {@code shared} porque la necesitan la cadena de seguridad, el manejador
 * global de errores y los controladores que auditan; estaba copiada literalmente en los
 * tres.</p>
 */
public final class IpCliente {

    private static final String CABECERA_REENVIO = "X-Forwarded-For";

    public static String de(HttpServletRequest http) {
        String reenviada = http.getHeader(CABECERA_REENVIO);
        if (reenviada != null && !reenviada.isBlank()) {
            return reenviada.split(",")[0].trim();
        }
        return http.getRemoteAddr();
    }

    private IpCliente() {
    }
}