package com.codefactory.devnet.identity.infrastructure;

import com.codefactory.devnet.identity.domain.EmisorTokens;
import com.codefactory.devnet.identity.domain.Usuario;
import com.codefactory.devnet.shared.config.PropiedadesDevNet;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Adaptador de {@link EmisorTokens} sobre JWT firmados con RS256.
 *
 * <p>El token lleva los <b>permisos</b>, no los roles: HU-06 evalua la autorizacion
 * contra codigos {@code recurso:accion}, de modo que reasignar capacidades a un rol
 * no obliga a tocar ni una anotacion del codigo.</p>
 *
 * <p>Los roles viajan solo como informacion para la interfaz y la auditoria; ninguna
 * decision de acceso se toma con ellos.</p>
 */
@Service
public class ServicioJwt implements EmisorTokens {

    public static final String CLAIM_PERMISOS = "permisos";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_NOMBRE_USUARIO = "nombreUsuario";

    private static final String EMISOR = "devnet";

    private final JwtEncoder codificador;
    private final PropiedadesDevNet propiedades;

    public ServicioJwt(JwtEncoder codificador, PropiedadesDevNet propiedades) {
        this.codificador = codificador;
        this.propiedades = propiedades;
    }

    @Override
    public TokenAcceso emitir(Usuario usuario) {
        Instant ahora = Instant.now();
        Instant expira = ahora.plus(propiedades.seguridad().vigenciaAcceso());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(EMISOR)
                .issuedAt(ahora)
                .expiresAt(expira)
                .subject(usuario.id().toString())
                .claim(CLAIM_NOMBRE_USUARIO, usuario.nombreUsuario())
                .claim(CLAIM_ROLES, List.copyOf(usuario.codigosDeRol()))
                // permisosEfectivos() ya devuelve vacio si el rol exige MFA y el
                // usuario no lo inscribio: el token sale sin capacidades.
                .claim(CLAIM_PERMISOS, List.copyOf(usuario.permisosEfectivos()))
                .build();

        String valor = codificador.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return new TokenAcceso(valor, expira, propiedades.seguridad().vigenciaAcceso().toSeconds());
    }
}