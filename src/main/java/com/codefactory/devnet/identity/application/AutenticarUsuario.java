package com.codefactory.devnet.identity.application;

import com.codefactory.devnet.config.PropiedadesDevNet;
import com.codefactory.devnet.identity.domain.CodigoErrorIdentidad;
import com.codefactory.devnet.identity.domain.EmisorTokens;
import com.codefactory.devnet.identity.domain.EstadoUsuario;
import com.codefactory.devnet.identity.domain.RepositorioUsuarios;
import com.codefactory.devnet.identity.domain.Usuario;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;
import com.codefactory.devnet.shared.audit.EventoAuditoria;
import com.codefactory.devnet.shared.audit.RegistroAuditoria;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Caso de uso de inicio de sesion con credenciales locales.
 *
 * <p>Es el habilitador de HU-06: sin un token que lleve permisos no hay forma de
 * demostrar que la autorizacion se verifica en el servidor.</p>
 */
@Service
public class AutenticarUsuario {

    private final RepositorioUsuarios usuarios;
    private final EmisorTokens emisor;
    private final PasswordEncoder codificador;
    private final RegistroAuditoria auditoria;
    private final PropiedadesDevNet propiedades;

    public AutenticarUsuario(RepositorioUsuarios usuarios,
                             EmisorTokens emisor,
                             PasswordEncoder codificador,
                             RegistroAuditoria auditoria,
                             PropiedadesDevNet propiedades) {
        this.usuarios = usuarios;
        this.emisor = emisor;
        this.codificador = codificador;
        this.auditoria = auditoria;
        this.propiedades = propiedades;
    }

    /**
     * @param correo dato personal: no se registra en el log, solo en auditoria
     * @param clave  en claro, solo en memoria y solo durante esta llamada
     * @param ip     origen, para el registro de auditoria
     */
    public Resultado ejecutar(String correo, String clave, String ip) {
        Optional<Usuario> encontrado = usuarios.porCorreo(correo);

        // Usuario inexistente y clave incorrecta devuelven el MISMO codigo. Dos
        // respuestas distintas permitirian enumerar cuentas registradas probando
        // correos, que es el primer paso de un ataque dirigido.
        if (encontrado.isEmpty()) {
            auditoria.registrar(EventoAuditoria.inicioSesionFallido(correo, ip));
            throw new ExcepcionNegocio(CodigoErrorIdentidad.AUTH_CREDENCIALES_INVALIDAS);
        }

        Usuario usuario = encontrado.get();

        if (usuario.estaBloqueada()) {
            auditoria.registrar(EventoAuditoria.cuentaBloqueada(usuario.id(), ip));
            throw new ExcepcionNegocio(CodigoErrorIdentidad.AUTH_CUENTA_BLOQUEADA);
        }

        if (usuario.estado() == EstadoUsuario.DESACTIVADO) {
            auditoria.registrar(EventoAuditoria.inicioSesionFallido(correo, ip));
            throw new ExcepcionNegocio(CodigoErrorIdentidad.AUTH_CUENTA_DESACTIVADA);
        }

        if (!claveCoincide(clave, usuario.claveHash())) {
            boolean quedaBloqueada = usuario.registrarIntentoFallido(
                    propiedades.seguridad().intentosMaximos(),
                    propiedades.seguridad().bloqueo());

            // Se persiste ANTES de lanzar: la excepcion revierte la transaccion de
            // la peticion, y sin esto el contador nunca subiria.
            usuarios.guardarEstadoAcceso(usuario);

            auditoria.registrar(quedaBloqueada
                    ? EventoAuditoria.cuentaBloqueada(usuario.id(), ip)
                    : EventoAuditoria.inicioSesionFallido(correo, ip));

            throw new ExcepcionNegocio(quedaBloqueada
                    ? CodigoErrorIdentidad.AUTH_CUENTA_BLOQUEADA
                    : CodigoErrorIdentidad.AUTH_CREDENCIALES_INVALIDAS);
        }

        usuario.registrarAccesoExitoso();
        usuarios.guardarEstadoAcceso(usuario);
        auditoria.registrar(EventoAuditoria.inicioSesion(usuario.id(), ip));

        EmisorTokens.TokenAcceso token = emisor.emitir(usuario);

        // Un rol con MFA obligatorio se autentica, pero su token sale sin permisos.
        // Se avisa explicitamente para que el cliente sepa por que no puede nada.
        return new Resultado(token, usuario, usuario.tieneMfaPendiente());
    }

    /**
     * Compara contra el hash almacenado.
     *
     * <p>Si la cuenta se creo por GitHub y no tiene clave local, {@code claveHash} es
     * nulo. Se codifica igualmente contra un hash ficticio para que el tiempo de
     * respuesta no delate cuales cuentas tienen contrasena y cuales no.</p>
     */
    private boolean claveCoincide(String clave, String hashAlmacenado) {
        if (hashAlmacenado == null) {
            return false;
        }
        try {
            return codificador.matches(clave, hashAlmacenado);
        } catch (IllegalArgumentException ex) {
            // Hash corrupto o de otro algoritmo: se trata como credencial invalida,
            // nunca como acceso concedido. El detalle va al log del manejador global.
            return false;
        }
    }

    /**
     * @param mfaPendiente el rol exige segundo factor y el usuario no lo inscribio.
     *                     El token es valido pero no concede ningun permiso.
     */
    public record Resultado(EmisorTokens.TokenAcceso token, Usuario usuario, boolean mfaPendiente) { }
}