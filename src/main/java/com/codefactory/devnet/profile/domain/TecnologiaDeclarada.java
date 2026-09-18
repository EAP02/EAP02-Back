package com.codefactory.devnet.profile.domain;

/**
 * Una tecnologia que el desarrollador declara en su perfil, con el nivel que dice
 * tener.
 *
 * <p>Referencia al catalogo por identificador, no por texto libre. Esa es la
 * diferencia que hace que el reporte de tecnologias mas discutidas signifique algo:
 * con texto libre aparecerian React, ReactJS y react.js como tres tecnologias
 * distintas y ninguna agregacion seria fiable.</p>
 *
 * @param tecnologiaId identificador del catalogo {@code tecnologia}
 * @param nivel        obligatorio. El modelo lo exige y ademas es el dato que da
 *                     valor a buscar colaboradores: no es lo mismo "he tocado Java"
 *                     que "llevo cinco anios"
 * @param anios        opcional
 */
public record TecnologiaDeclarada(short tecnologiaId, NivelTecnologia nivel, Short anios) {

    public TecnologiaDeclarada {
        if (nivel == null) {
            throw new PerfilInvalidoException("tecnologias.nivel", "Indica tu nivel en cada tecnologia.");
        }
        if (anios != null && (anios < 0 || anios > 70)) {
            throw new PerfilInvalidoException("tecnologias.anios", "Los anios deben estar entre 0 y 70.");
        }
    }
}