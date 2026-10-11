package com.codefactory.devnet.project.domain;

/**
 * Una entrada del catalogo curado de tecnologias.
 *
 * <p>El {@code slug} es el identificador legible ({@code spring-boot}, {@code java}):
 * estable, sin espacios ni tildes, apto para ir en una URL. Es el que usa el filtro de
 * la busqueda (HU-09.2); el {@code id} numerico sigue siendo el que se envia al
 * publicar, porque asi lo fijo HU-07.</p>
 *
 * @param categoria LENGUAJE, FRAMEWORK, BASE_DATOS, HERRAMIENTA o CLOUD
 */
public record Tecnologia(short id, String nombre, String slug, String categoria) {
}
