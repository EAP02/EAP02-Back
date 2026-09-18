package com.codefactory.devnet.identity.application;

import com.codefactory.devnet.identity.domain.GeneradorTokenRefresco;
import com.codefactory.devnet.identity.domain.RepositorioTokensRefresco;
import com.codefactory.devnet.identity.domain.TokenRefresco;
import com.codefactory.devnet.shared.config.PropiedadesDevNet;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Emision de tokens de refresco, en sus dos formas.
 *
 * <p>Existe porque {@link AutenticarUsuario} y {@link RefrescarSesion} necesitan lo
 * mismo por dentro —generar el valor, calcular el vencimiento, armar la emision— y solo
 * se diferencian en un punto: <b>de donde sale la familia</b>.</p>
 *
 * <p>Ese punto es justamente el invariante central del ADR-004, y tenerlo escrito dos
 * veces es la forma mas facil de romperlo sin que nadie lo note.</p>
 */
@Service
public class EmitirRefresco {

    private final RepositorioTokensRefresco tokens;
    private final GeneradorTokenRefresco generador;
    private final PropiedadesDevNet propiedades;

    public EmitirRefresco(RepositorioTokensRefresco tokens,
                          GeneradorTokenRefresco generador,
                          PropiedadesDevNet propiedades) {
        this.tokens = tokens;
        this.generador = generador;
        this.propiedades = propiedades;
    }

    /**
     * Abre una sesion nueva, con <b>familia nueva</b>.
     *
     * <p>Una familia equivale a un inicio de sesion. Entrar desde el movil no debe
     * invalidar la sesion del portatil, y un reuso detectado en una no debe tumbar la
     * otra: por eso cada login estrena la suya.</p>
     *
     * @return el token en claro, para la cookie. No se persiste en ninguna parte.
     */
    public String abrirSesion(UUID usuarioId, Instant ahora, String ip, String agenteUsuario) {
        GeneradorTokenRefresco.Generado nuevo = generador.generar();

        tokens.emitir(new RepositorioTokensRefresco.Emision(
                usuarioId,
                UUID.randomUUID(),          // familia nueva: esto es un login
                nuevo.hash(),
                ahora,
                ahora.plus(propiedades.seguridad().vigenciaRefresco()),
                ip,
                agenteUsuario));

        return nuevo.valor();
    }

    /**
     * Rota dentro de una sesion existente: el sucesor <b>hereda la familia</b>.
     *
     * <p>Heredarla es lo que hace posible la revocacion en cascada. Si aqui se generara
     * una familia nueva, cada rotacion cortaria el hilo con las anteriores y revocar
     * ante un reuso solo alcanzaria al ultimo token, que es precisamente el que el
     * atacante ya uso.</p>
     *
     * @return el token en claro, o <b>vacio si el anterior ya estaba consumido</b>, que
     *         es la senal de reuso
     */
    public Optional<String> rotar(TokenRefresco anterior,
                                  Instant ahora,
                                  String ip,
                                  String agenteUsuario) {
        GeneradorTokenRefresco.Generado nuevo = generador.generar();

        return tokens.rotar(anterior.id(), new RepositorioTokensRefresco.Emision(
                        anterior.usuarioId(),
                        anterior.familia(),   // heredada, no nueva
                        nuevo.hash(),
                        ahora,
                        ahora.plus(propiedades.seguridad().vigenciaRefresco()),
                        ip,
                        agenteUsuario))
                .map(emitido -> nuevo.valor());
    }
}