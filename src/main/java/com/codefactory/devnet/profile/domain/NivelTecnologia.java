package com.codefactory.devnet.profile.domain;

/**
 * Dominio del desarrollador sobre una tecnologia.
 *
 * <p>Debe coincidir con {@code ck_perfil_tecnologia_nivel} en V1__baseline.sql.</p>
 */
public enum NivelTecnologia {
    BASICO,
    INTERMEDIO,
    AVANZADO,
    EXPERTO
}