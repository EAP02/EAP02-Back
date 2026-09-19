package com.codefactory.devnet.identity.api;

import com.codefactory.devnet.shared.config.PropiedadesDevNet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

/**
 * La cookie que transporta el token de refresco.
 *
 * <p><b>Es el unico sitio del proyecto que construye o lee una cookie.</b> Toda la
 * politica —que rutas la reciben, si exige TLS, cuanto vive— esta escrita aqui y en
 * ningun otro lugar.</p>
 *
 * <p>Vive en {@code api} y no en {@code infrastructure} porque quien la necesita es el
 * controlador, y la regla 3 de {@code FronterasModularesTest} prohibe que {@code api}
 * dependa de {@code infrastructure}. En {@code domain} seria imposible: la regla 2 no
 * admite tipos de Spring ni de Jakarta ahi.</p>
 */
@Component
public class CookieRefresco {

    public static final String NOMBRE = "devnet_refresco";

    /**
     * La cookie solo se envia a las dos rutas que la usan.
     *
     * <p>Con {@code Path=/} viajaria en cada peticion de la sesion —decenas por
     * minuto— sin que ninguna la lea. Acotarla no cuesta nada y reduce las
     * oportunidades de que se filtre por un log o un proxy.</p>
     */
    private static final String RUTA = "/api/v1/auth";

    /**
     * {@code Secure} exige HTTPS y el navegador descarta la cookie sobre
     * {@code http://localhost}. De ahi que sea configurable, con el valor seguro por
     * defecto: solo el perfil local lo baja.
     *
     * <p>Va por {@code @Value} y no dentro de {@code PropiedadesDevNet.Seguridad} a
     * proposito. Un {@code boolean} primitivo que falte en el yml se enlaza a
     * {@code false} <b>en silencio</b>, y ese silencio significaria desplegar
     * produccion sin {@code Secure}. Es el mismo motivo por el que {@code JwtConfig}
     * lee sus claves con {@code @Value}.</p>
     */
    @Value("${devnet.seguridad.cookie-segura:true}")
    private boolean segura;

    private final PropiedadesDevNet propiedades;

    public CookieRefresco(PropiedadesDevNet propiedades) {
        this.propiedades = propiedades;
    }

    /**
     * Cookie con el token.
     *
     * <p>Se usa {@link ResponseCookie} y no {@link Cookie} por una razon concreta: la
     * de Jakarta no sabe escribir {@code SameSite}, que es el atributo que impide que
     * un sitio de terceros provoque una renovacion de sesion.</p>
     *
     * <p><b>{@code SameSite=Strict} supone que el cliente es del mismo sitio.</b> En
     * local lo es: {@code localhost:5173} y {@code localhost:8080} solo difieren en el
     * puerto, que no cuenta. Si el frontend se despliega en otro dominio, la cookie
     * dejara de viajar y habra que pasar a {@code None} —que exige {@code Secure}— y
     * revisar el ADR-004.</p>
     */
    public ResponseCookie emitir(String valorEnClaro) {
        return base(valorEnClaro)
                .maxAge(propiedades.seguridad().vigenciaRefresco())
                .build();
    }

    /** Cookie vacia y ya vencida: le dice al navegador que la borre. */
    public ResponseCookie expirar() {
        return base("").maxAge(0).build();
    }

    /**
     * Lee la cookie de la peticion.
     *
     * <p>No se usa {@code @CookieValue} en el controlador porque obligaria a declararla
     * opcional y a tratar el nulo alli, repartiendo en dos sitios una politica que debe
     * estar en uno.</p>
     */
    public Optional<String> leer(HttpServletRequest http) {
        Cookie[] cookies = http.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(c -> NOMBRE.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(valor -> valor != null && !valor.isBlank())
                .findFirst();
    }

    private ResponseCookie.ResponseCookieBuilder base(String valor) {
        return ResponseCookie.from(NOMBRE, valor)
                .httpOnly(true)      // inaccesible desde JavaScript: acota el XSS
                .secure(segura)
                .sameSite("Strict")
                .path(RUTA);
    }
}