package com.codefactory.devnet.identity.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia de tokens de refresco.
 *
 * <p>La IP viaja como {@code String} y no como {@code InetAddress}: la columna es de
 * tipo {@code inet} y convertirla es cosa del adaptador. El dominio no conoce ni el
 * tipo de PostgreSQL ni el de {@code java.net}.</p>
 */
public interface RepositorioTokensRefresco {

    /**
     * Datos de una emision.
     *
     * @param tokenHash lo unico que se persiste del token. El valor en claro solo
     *                  existe en memoria y en la cookie del titular.
     * @param ipOrigen  puede ser nula o no ser una IP valida: llega de una cabecera que
     *                  controla el cliente. El adaptador decide que hacer con ella.
     */
    record Emision(
            UUID usuarioId,
            UUID familia,
            String tokenHash,
            Instant emitidoEn,
            Instant expiraEn,
            String ipOrigen,
            String agenteUsuario
    ) { }

    TokenRefresco emitir(Emision emision);

    Optional<TokenRefresco> porHash(String tokenHash);

    /**
     * Consume el anterior e inserta el sucesor <b>en una sola transaccion</b>.
     *
     * <p>Es una operacion y no dos (marcar consumido, luego guardar) para que no exista
     * ningun instante en que el anterior este consumido y el sucesor no exista: ahi el
     * titular se quedaria sin sesion por una caida a medias.</p>
     *
     * <p>El consumo es condicional sobre {@code consumido_en IS NULL}. Si dos peticiones
     * concurrentes traen el mismo refresco, solo una gana.</p>
     *
     * @return el sucesor, o <b>vacio si el anterior ya estaba consumido o revocado</b>.
     *         Vacio significa reuso, y quien llama debe tratarlo como tal.
     */
    Optional<TokenRefresco> rotar(UUID idAnterior, Emision sucesor);

    /**
     * Revoca todos los tokens vigentes de una familia.
     *
     * <p><b>Debe confirmar aunque quien la invoca lance una excepcion despues.</b> El
     * caso de uso revoca y acto seguido rechaza la peticion; si la revocacion viajara en
     * la transaccion de la peticion, el rollback la desharia y la deteccion de reuso no
     * revocaria nada. Es el mismo motivo por el que
     * {@code RepositorioUsuarios.guardarEstadoAcceso} se invoca antes de lanzar.</p>
     *
     * @return cuantas filas se revocaron
     */
    int revocarFamilia(UUID familia, Instant momento);

    /**
     * Revoca todas las familias vigentes de un usuario.
     *
     * <p>Para "cerrar sesion en todos los dispositivos" y, sobre todo, para invocarla
     * cuando una cuenta pase a suspendida o desactivada. Hoy ningun caso de uso cambia
     * el estado de una cuenta, asi que no tiene quien la llame todavia.</p>
     */
    int revocarDeUsuario(UUID usuarioId, Instant momento);
}