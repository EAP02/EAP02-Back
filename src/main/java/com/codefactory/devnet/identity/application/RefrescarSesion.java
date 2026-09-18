package com.codefactory.devnet.identity.application;

import com.codefactory.devnet.identity.domain.CodigoErrorIdentidad;
import com.codefactory.devnet.identity.domain.EmisorTokens;
import com.codefactory.devnet.identity.domain.EstadoUsuario;
import com.codefactory.devnet.identity.domain.GeneradorTokenRefresco;
import com.codefactory.devnet.identity.domain.RepositorioTokensRefresco;
import com.codefactory.devnet.identity.domain.RepositorioUsuarios;
import com.codefactory.devnet.identity.domain.TokenRefresco;
import com.codefactory.devnet.identity.domain.Usuario;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import com.codefactory.devnet.shared.audit.EventoAuditoria;
import com.codefactory.devnet.shared.audit.RegistroAuditoria;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Renovacion del token de acceso a partir del token de refresco (ADR-004).
 *
 * <p>Es el unico punto del sistema donde se reevalua el estado de una cuenta sin pedir
 * la contrasena. El JWT de acceso vive quince minutos y no se puede revocar; suspender a
 * alguien surte efecto aqui, en su siguiente rotacion.</p>
 *
 * <p><b>El orden de las comprobaciones es parte del diseno</b>, no una casualidad: el
 * reuso se comprueba antes que la revocacion y que la expiracion. Un token consumido y
 * ademas caducado sigue siendo la senal de que alguien uso dos veces la misma
 * credencial, y esa senal no se puede perder detras de un "expiro".</p>
 */
@Service
public class RefrescarSesion {

    private final RepositorioTokensRefresco tokens;
    private final RepositorioUsuarios usuarios;
    private final GeneradorTokenRefresco generador;
    private final EmitirRefresco emisorRefresco;
    private final EmisorTokens emisor;
    private final RegistroAuditoria auditoria;

    public RefrescarSesion(RepositorioTokensRefresco tokens,
                           RepositorioUsuarios usuarios,
                           GeneradorTokenRefresco generador,
                           EmitirRefresco emisorRefresco,
                           EmisorTokens emisor,
                           RegistroAuditoria auditoria) {
        this.tokens = tokens;
        this.usuarios = usuarios;
        this.generador = generador;
        this.emisorRefresco = emisorRefresco;
        this.emisor = emisor;
        this.auditoria = auditoria;
    }

    /**
     * @param refrescoEnClaro valor de la cookie. Nulo si el navegador no la envio.
     * @param ip              origen, para la trazabilidad de la familia
     * @param agenteUsuario   cabecera User-Agent, util para distinguir dispositivos
     */
    public Resultado ejecutar(String refrescoEnClaro, String ip, String agenteUsuario) {
        Instant ahora = Instant.now();

        // 1. Sin cookie no hay nada que renovar. Se valida aqui y no en el controlador
        //    para que la regla completa viva en un solo sitio.
        if (refrescoEnClaro == null || refrescoEnClaro.isBlank()) {
            throw new ExcepcionNegocio(CodigoErrorIdentidad.AUTH_REFRESCO_AUSENTE);
        }

        // 2. Se busca por el hash: el valor en claro no esta guardado en ningun sitio.
        Optional<TokenRefresco> encontrado =
                tokens.porHash(generador.hashDe(refrescoEnClaro));

        if (encontrado.isEmpty()) {
            throw new ExcepcionNegocio(CodigoErrorIdentidad.AUTH_REFRESCO_INVALIDO);
        }

        TokenRefresco token = encontrado.get();

        // 3. REUSO. Un refresco se usa exactamente una vez; verlo consumido significa
        //    que existen dos copias. Se revoca la familia entera antes de rechazar.
        if (token.estaConsumido()) {
            revocarFamiliaPorReuso(token, ip);
            throw new ExcepcionNegocio(CodigoErrorIdentidad.AUTH_REFRESCO_REUSADO);
        }

        // 4. Ya revocado: es la consecuencia esperada de un reuso anterior o de un
        //    cierre de sesion. No se vuelve a revocar ni se audita como reuso; hacerlo
        //    llenaria la cola de alertas con ecos del mismo incidente.
        if (token.estaRevocado()) {
            throw new ExcepcionNegocio(CodigoErrorIdentidad.AUTH_REFRESCO_REVOCADO);
        }

        if (token.haExpirado(ahora)) {
            throw new ExcepcionNegocio(CodigoErrorIdentidad.AUTH_REFRESCO_EXPIRADO);
        }

        // 5. Estado de la cuenta. Este es el motivo por el que el refresco sirve como
        //    mecanismo de revocacion: el JWT de 15 minutos no puede reevaluar nada.
        Usuario usuario = usuarios.porId(token.usuarioId())
                .orElseThrow(() -> new ExcepcionNegocio(CodigoErrorIdentidad.AUTH_REFRESCO_INVALIDO));

        if (usuario.estado() == EstadoUsuario.DESACTIVADO) {
            tokens.revocarFamilia(token.familia(), ahora);
            throw new ExcepcionNegocio(CodigoErrorIdentidad.AUTH_CUENTA_DESACTIVADA);
        }

        if (usuario.estaBloqueada()) {
            throw new ExcepcionNegocio(CodigoErrorIdentidad.AUTH_CUENTA_BLOQUEADA);
        }

        // 6. Rotacion. Vacio significa que otra peticion consumio este mismo token
        //    entre el paso 3 y ahora: es un reuso concurrente y se trata igual.
        String refrescoNuevo = emisorRefresco.rotar(token, ahora, ip, agenteUsuario)
                .orElseGet(() -> {
                    revocarFamiliaPorReuso(token, ip);
                    throw new ExcepcionNegocio(CodigoErrorIdentidad.AUTH_REFRESCO_REUSADO);
                });

        // 7. Token de acceso nuevo, con los permisos recalculados: quien inscribio el
        //    segundo factor desde el ultimo refresco los recupera aqui.
        EmisorTokens.TokenAcceso acceso = emisor.emitir(usuario);

        auditoria.registrar(EventoAuditoria.refrescoRotado(usuario.id(), token.familia(), ip));

        return new Resultado(acceso, refrescoNuevo, usuario, usuario.tieneMfaPendiente());
    }

    /**
     * Revoca la familia y deja constancia.
     *
     * <p>La revocacion confirma en su propia transaccion —ver
     * {@code RepositorioTokensRefresco.revocarFamilia}— porque justo despues se lanza
     * una excepcion que revierte la de la peticion.</p>
     */
    private void revocarFamiliaPorReuso(TokenRefresco token, String ip) {
        tokens.revocarFamilia(token.familia(), Instant.now());
        auditoria.registrar(
                EventoAuditoria.refrescoReutilizado(token.usuarioId(), token.familia(), ip));
    }

    /**
     * @param refrescoEnClaro va a la cookie, <b>nunca al cuerpo de la respuesta</b>
     */
    public record Resultado(EmisorTokens.TokenAcceso token,
                            String refrescoEnClaro,
                            Usuario usuario,
                            boolean mfaPendiente) { }
}