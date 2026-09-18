package com.codefactory.devnet.profile.domain;

import com.codefactory.devnet.shared.api.DetalleError;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;

import java.util.List;
import java.util.UUID;

/**
 * Reglas ABAC del modulo de perfiles.
 *
 * <p>Un perfil solo lo edita su titular. La comprobacion vive aqui, en el dominio, y
 * no en el controlador, para que ninguna ruta futura pueda saltarsela: es el mismo
 * criterio que {@code Proyecto.exigirAutoriaPropia} en HU-07.</p>
 *
 * <p>El lineamiento 6.2 lo exige expresamente: "centralizar la decision de
 * autorizacion y verificarla en el servidor para cada operacion protegida", y
 * complementar el RBAC con atributos "cuando la propiedad, el estado o el contexto
 * sean relevantes". Aqui la propiedad es justamente lo que decide.</p>
 */
public final class PoliticaPerfil {

    private PoliticaPerfil() {
    }

    /**
     * Exige que quien edita sea el titular del perfil.
     *
     * <p>Devuelve 403 y no 404: el perfil es publico y su existencia ya se puede
     * comprobar con un GET, asi que ocultarla no aportaria nada y solo confundiria a
     * quien se equivoco de identificador.</p>
     *
     * @param perfilId     perfil que se intenta modificar
     * @param autenticadoId usuario de la peticion en curso; nulo si es anonima
     * @throws ExcepcionNegocio {@code PERFIL_AJENO} si no coinciden
     */
    public static void exigirTitular(UUID perfilId, UUID autenticadoId) {
        if (autenticadoId == null || !autenticadoId.equals(perfilId)) {
            throw new ExcepcionNegocio(
                    CodigoErrorPerfil.PERFIL_AJENO,
                    CodigoErrorPerfil.PERFIL_AJENO.mensajePorDefecto(),
                    List.of(DetalleError.de("perfilId", "no_eres_el_titular")));
        }
    }
}