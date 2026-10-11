package com.codefactory.devnet.discussion.domain;

import java.util.Locale;

/**
 * Lo que un miembro elige al listar discusiones (HU-12): el tema, es decir, la etiqueta
 * de tecnologia.
 *
 * <p>Objeto de valor, como {@code CriteriosBusqueda} en {@code project}: se construye ya
 * normalizado. Sin etiqueta tambien es valido: lista todas las discusiones publicadas.</p>
 *
 * @param tecnologiaSlug slug en minusculas, o {@code null} si no se filtro
 */
public record FiltroDiscusiones(String tecnologiaSlug) {

    public static FiltroDiscusiones de(String tecnologia) {
        if (tecnologia == null || tecnologia.isBlank()) {
            return new FiltroDiscusiones(null);
        }
        // Los slugs del catalogo estan en minusculas: "Java" debe encontrar "java".
        return new FiltroDiscusiones(tecnologia.strip().toLowerCase(Locale.ROOT));
    }

    public boolean hayTecnologia() {
        return tecnologiaSlug != null;
    }

    /**
     * HU-12: el estado vacio cuando un filtro no tiene discusiones.
     *
     * <p>Distingue el caso de una etiqueta que no existe del de una etiqueta valida sin
     * discusiones todavia: al primero le sirve saber que se equivoco; al segundo, que
     * puede abrir la primera.</p>
     *
     * @param nombreTecnologia nombre legible de la etiqueta filtrada, o {@code null} si
     *                         el slug no corresponde a ninguna del catalogo
     */
    public String mensajeSinResultados(String nombreTecnologia) {
        if (hayTecnologia() && nombreTecnologia == null) {
            return "La tecnologia \"%s\" no esta en el catalogo. Revisa el catalogo de tecnologias para ver las disponibles."
                    .formatted(tecnologiaSlug);
        }
        if (hayTecnologia()) {
            return "Todavia no hay discusiones sobre %s. Abre la primera o explora otros temas."
                    .formatted(nombreTecnologia);
        }
        return "Todavia no hay discusiones. Abre la primera para empezar la conversacion.";
    }
}
