package com.codefactory.devnet.project.domain;

import com.codefactory.devnet.shared.api.DetalleError;
import com.codefactory.devnet.shared.api.ExcepcionNegocio;

import java.util.List;
import java.util.Locale;

/**
 * Lo que alguien busca al explorar proyectos (HU-09): un texto libre, una tecnologia, o
 * ambas cosas a la vez.
 *
 * <p>Objeto de valor: se construye ya normalizado y validado, de modo que el resto del
 * sistema no tiene que preguntarse si el texto trae espacios sobrantes o si el slug
 * viene en mayusculas.</p>
 *
 * <p>Sin ningun criterio tambien es una busqueda valida: es "explorar", y devuelve todos
 * los proyectos publicados.</p>
 *
 * @param texto          termino libre ya recortado, o {@code null} si no se busco texto
 * @param tecnologiaSlug slug en minusculas, o {@code null} si no se filtro
 */
public record CriteriosBusqueda(String texto, String tecnologiaSlug) {

    /**
     * Tope del termino de busqueda. Nadie busca con un parrafo, y un texto enorme solo
     * encarece la consulta de texto completo.
     */
    public static final int TEXTO_MAX = 100;

    /**
     * Normaliza y valida lo que llega del cliente.
     *
     * @throws ExcepcionNegocio {@code PROYECTO_BUSQUEDA_INVALIDA} si el texto supera
     *                          {@link #TEXTO_MAX} caracteres
     */
    public static CriteriosBusqueda de(String texto, String tecnologia) {
        String textoLimpio = vacioANulo(texto);
        if (textoLimpio != null && textoLimpio.length() > TEXTO_MAX) {
            throw new ExcepcionNegocio(
                    CodigoErrorProyecto.PROYECTO_BUSQUEDA_INVALIDA,
                    CodigoErrorProyecto.PROYECTO_BUSQUEDA_INVALIDA.mensajePorDefecto(),
                    List.of(DetalleError.de("q", textoLimpio.length(), "longitud_maxima_" + TEXTO_MAX)));
        }

        // Los slugs del catalogo estan en minusculas: aceptar "Java" o "JAVA" es mas
        // amable que responder vacio por una mayuscula.
        String slug = vacioANulo(tecnologia);
        return new CriteriosBusqueda(textoLimpio, slug == null ? null : slug.toLowerCase(Locale.ROOT));
    }

    public boolean hayTexto() {
        return texto != null;
    }

    public boolean hayTecnologia() {
        return tecnologiaSlug != null;
    }

    /**
     * HU-09.3: el mensaje orientador cuando no hay ningun resultado.
     *
     * <p>Se adapta a lo que se busco, porque el consejo util es distinto en cada caso: a
     * quien combino texto y tecnologia le sirve quitar el filtro; a quien escribio mal
     * una tecnologia, saber que no existe; a quien busco solo texto, probar con otras
     * palabras. Un "no hay resultados" generico no orienta a nadie.</p>
     *
     * @param nombreTecnologia nombre legible de la tecnologia filtrada, o {@code null}
     *                         si el slug no corresponde a ninguna del catalogo
     */
    public String mensajeSinResultados(String nombreTecnologia) {
        if (hayTecnologia() && nombreTecnologia == null) {
            return "La tecnologia \"%s\" no esta en el catalogo. Revisa el catalogo de tecnologias para ver las disponibles."
                    .formatted(tecnologiaSlug);
        }
        if (hayTexto() && hayTecnologia()) {
            return "No encontramos proyectos de %s que coincidan con \"%s\". Prueba con otros terminos o quita el filtro de tecnologia."
                    .formatted(nombreTecnologia, texto);
        }
        if (hayTexto()) {
            return "No encontramos proyectos que coincidan con \"%s\". Prueba con otras palabras o con terminos mas generales."
                    .formatted(texto);
        }
        if (hayTecnologia()) {
            return "Todavia no hay proyectos publicados con %s. Explora otras tecnologias del catalogo."
                    .formatted(nombreTecnologia);
        }
        return "Todavia no hay proyectos publicados. Animate a publicar el primero.";
    }

    private static String vacioANulo(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.strip();
        return limpio.isEmpty() ? null : limpio;
    }
}
