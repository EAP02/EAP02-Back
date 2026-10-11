package com.codefactory.devnet.discussion.domain;

/**
 * Tipo de conversacion que abre una discusion. Debe coincidir con
 * {@code ck_discusion_categoria} en {@code V1__baseline.sql}.
 */
public enum CategoriaDiscusion {

    /** Alguien pide ayuda. Puede quedar resuelta al aceptar un comentario. */
    PREGUNTA,

    /**
     * Se invita a opinar. Es la categoria por defecto: HU-11 habla de "generar
     * conversacion", que es exactamente esto.
     */
    DEBATE,

    /** Se comunica algo: un lanzamiento, un evento. */
    ANUNCIO,

    /** Se comparte material: un tutorial, una herramienta, un articulo. */
    RECURSO
}
