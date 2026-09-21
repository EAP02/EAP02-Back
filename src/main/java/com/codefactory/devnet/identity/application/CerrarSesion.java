package com.codefactory.devnet.identity.application;

import com.codefactory.devnet.identity.domain.GeneradorTokenRefresco;
import com.codefactory.devnet.identity.domain.RepositorioTokensRefresco;
import com.codefactory.devnet.identity.domain.TokenRefresco;
import com.codefactory.devnet.shared.audit.EventoAuditoria;
import com.codefactory.devnet.shared.audit.RegistroAuditoria;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Cierre de sesion: revoca la familia del token de refresco presentado.
 *
 * <p>Sin esto, toda la maquinaria de revocacion del ADR-004 no tendria quien la dispare
 * salvo la deteccion de reuso. Un credito de siete dias que su titular no puede
 * invalidar es peor que no tenerlo.</p>
 *
 * <p><b>Nunca falla.</b> No hay cookie, el token no existe, ya estaba revocado: en los
 * tres casos el resultado deseado —que esa sesion no sirva— ya se cumple. Devolver un
 * error obligaria al cliente a distinguir situaciones que para el son la misma, y
 * responder distinto segun el caso diria si un token existe o no.</p>
 */
@Service
public class CerrarSesion {

    private final RepositorioTokensRefresco tokens;
    private final GeneradorTokenRefresco generador;
    private final RegistroAuditoria auditoria;

    public CerrarSesion(RepositorioTokensRefresco tokens,
                        GeneradorTokenRefresco generador,
                        RegistroAuditoria auditoria) {
        this.tokens = tokens;
        this.generador = generador;
        this.auditoria = auditoria;
    }

    /**
     * Revoca la familia entera, no solo el token presentado.
     *
     * <p>Cerrar sesion en un dispositivo cierra esa sesion completa, incluidas las
     * rotaciones que quedaran vivas por una peticion a medias. Las demas familias del
     * usuario —otros dispositivos— siguen intactas.</p>
     *
     * @param refrescoEnClaro valor de la cookie; puede ser nulo
     */
    public void ejecutar(String refrescoEnClaro, String ip) {
        if (refrescoEnClaro == null || refrescoEnClaro.isBlank()) {
            return;
        }

        Optional<TokenRefresco> encontrado =
                tokens.porHash(generador.hashDe(refrescoEnClaro));

        if (encontrado.isEmpty()) {
            return;
        }

        TokenRefresco token = encontrado.get();
        int revocados = tokens.revocarFamilia(token.familia(), Instant.now());

        // Solo se audita si habia algo vivo que revocar: repetir la llamada con la
        // misma cookie no debe multiplicar las entradas.
        if (revocados > 0) {
            auditoria.registrar(
                    EventoAuditoria.cierreSesion(token.usuarioId(), token.familia(), ip));
        }
    }
}