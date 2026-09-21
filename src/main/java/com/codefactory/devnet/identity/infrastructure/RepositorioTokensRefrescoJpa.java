package com.codefactory.devnet.identity.infrastructure;

import com.codefactory.devnet.identity.domain.RepositorioTokensRefresco;
import com.codefactory.devnet.identity.domain.TokenRefresco;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Adaptador JPA de {@link RepositorioTokensRefresco}.
 *
 * <p>Ninguna entidad cruza hacia {@code application}: se devuelve siempre el agregado de
 * dominio, igual que hace {@link RepositorioUsuariosJpa}.</p>
 */
@Component
public class RepositorioTokensRefrescoJpa implements RepositorioTokensRefresco {

    private static final Logger log = LoggerFactory.getLogger(RepositorioTokensRefrescoJpa.class);

    /** Cuatro grupos de 0-255. Deliberadamente estricto: ver {@link #aDireccion}. */
    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)$");

    private final TokenRefrescoRepositorio jpa;

    public RepositorioTokensRefrescoJpa(TokenRefrescoRepositorio jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public TokenRefresco emitir(Emision emision) {
        return jpa.save(aEntidad(emision)).aDominio();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TokenRefresco> porHash(String tokenHash) {
        return jpa.findByTokenHash(tokenHash).map(TokenRefrescoEntity::aDominio);
    }

    /**
     * Consume el anterior, inserta el sucesor y los enlaza, todo en una transaccion.
     *
     * <p><b>El consumo va primero.</b> Es lo que permite salir con {@code Optional}
     * vacio sin dejar rastro: si la actualizacion condicional no toca ninguna fila, no
     * se ha escrito nada todavia y no hay nada que revertir. Hacerlo al reves —insertar
     * el sucesor y despues intentar consumir— obligaria a forzar un rollback para
     * deshacer esa insercion.</p>
     *
     * <p>El enlace {@code reemplazado_por} es un tercer paso porque tiene clave foranea
     * contra la propia tabla y no puede apuntar al sucesor antes de que exista. Si algo
     * fallara entre medias, la transaccion revierte los tres pasos juntos y el titular
     * conserva su token anterior.</p>
     */
    @Override
    @Transactional
    public Optional<TokenRefresco> rotar(UUID idAnterior, Emision sucesor) {
        int consumidas = jpa.consumirSiSigueVigente(idAnterior, sucesor.emitidoEn());
        if (consumidas == 0) {
            // Ya estaba consumido o revocado: es reuso. Quien llama revoca la familia.
            return Optional.empty();
        }

        TokenRefrescoEntity nuevo = jpa.save(aEntidad(sucesor));
        jpa.enlazarSucesor(idAnterior, nuevo.id());

        return Optional.of(nuevo.aDominio());
    }

    /**
     * {@code REQUIRES_NEW} no es un detalle de estilo.
     *
     * <p>Quien invoca esto acaba de detectar un reuso y va a lanzar una excepcion
     * inmediatamente despues. En la transaccion de la peticion, el rollback desharia la
     * revocacion y la familia comprometida seguiria viva: la deteccion de reuso no
     * revocaria nada y ninguna prueba con dobles lo notaria.</p>
     *
     * <p>Es el mismo razonamiento que documenta
     * {@code RepositorioUsuariosJpa.guardarEstadoAcceso} para el contador de intentos
     * fallidos.</p>
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revocarFamilia(UUID familia, Instant momento) {
        return jpa.revocarFamilia(familia, momento);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revocarDeUsuario(UUID usuarioId, Instant momento) {
        return jpa.revocarDeUsuario(usuarioId, momento);
    }

    // ------------------------------------------------------------------

    private TokenRefrescoEntity aEntidad(Emision emision) {
        return TokenRefrescoEntity.nuevo(
                emision.usuarioId(),
                emision.familia(),
                emision.tokenHash(),
                emision.emitidoEn(),
                emision.expiraEn(),
                aDireccion(emision.ipOrigen()),
                emision.agenteUsuario());
    }

    /**
     * Convierte a {@link InetAddress} solo si la cadena ya es una IP literal.
     *
     * <p>La IP llega de {@code X-Forwarded-For}, que el cliente controla.
     * {@code InetAddress.getByName} resuelve por DNS cualquier cosa que no sea un
     * literal, asi que pasarle el valor a ciegas convertiria cada peticion en una
     * consulta DNS saliente elegida por quien la envia. Java 17 no tiene
     * {@code InetAddress.ofLiteral}, de ahi la comprobacion a mano.</p>
     *
     * <p>Una cadena con dos puntos solo puede ser IPv6: un nombre de host no los admite,
     * y {@code getByName} la trata como literal sin consultar a nadie. El resto tiene que
     * pasar por el patron de IPv4.</p>
     *
     * <p>Lo que no encaja se guarda como nulo. La IP es un dato de trazabilidad: perder
     * una vale mucho menos que abrir un canal de salida hacia donde el cliente decida.</p>
     */
    private InetAddress aDireccion(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        String limpia = ip.strip();

        // Forma [::1]:443 de algunos proxies.
        if (limpia.startsWith("[")) {
            int cierre = limpia.indexOf(']');
            if (cierre > 1) {
                limpia = limpia.substring(1, cierre);
            }
        }

        boolean esLiteral = limpia.indexOf(':') >= 0 || IPV4.matcher(limpia).matches();
        if (!esLiteral) {
            log.debug("Se descarta un X-Forwarded-For que no es una IP literal");
            return null;
        }

        try {
            return InetAddress.getByName(limpia);
        } catch (UnknownHostException ex) {
            // IPv6 mal formada. No hay consulta DNS de por medio.
            return null;
        }
    }

}