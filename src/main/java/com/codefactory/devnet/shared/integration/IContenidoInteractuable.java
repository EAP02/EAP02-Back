package com.codefactory.devnet.shared.integration;

import java.util.Optional;
import java.util.UUID;

/**
 * Contrato que <b>proveen los modulos {@code project} y {@code discussion}</b>, y
 * que <b>consume {@code interaction}</b>.
 *
 * <p>Es el contrato que justifica que este paquete exista. Los comentarios y las
 * reacciones se aplican por igual a proyectos y a discusiones, pero
 * {@code interaction} no debe conocer a ninguno de los dos. Con este contrato
 * pregunta lo unico que necesita saber: si el contenido existe, quien lo escribio y
 * si admite interaccion ahora mismo.</p>
 *
 * <p>En la base de datos esto tiene un reflejo directo: {@code publicacion} es el
 * supertipo y {@code publicacion_proyecto} y {@code publicacion_discusion} sus
 * especializaciones, de modo que {@code comentario.publicacion_id} es una clave
 * foranea real y no una referencia polimorfica. Ver
 * {@code docs/bd/02-modelo-logico.md}.</p>
 *
 * <p>Se registran dos implementaciones, una por modulo proveedor, y se resuelven por
 * el {@link Tipo} del contenido.</p>
 */
public interface IContenidoInteractuable {

    enum Tipo { PROYECTO, DISCUSION }

    /**
     * @param publicacionId     identificador del contenido
     * @param tipo              cual de los dos subtipos es
     * @param autorId           quien lo escribio. Base de las reglas ABAC de propiedad.
     * @param admiteComentarios falso si no esta publicado o si esta archivado
     */
    record ContenidoRef(
            UUID publicacionId,
            Tipo tipo,
            UUID autorId,
            boolean admiteComentarios
    ) {
        public boolean esAutor(UUID usuarioId) {
            return autorId.equals(usuarioId);
        }
    }

    /** Que subtipo atiende esta implementacion. */
    Tipo tipoSoportado();

    /** Vacio si el contenido no existe o no es visible. */
    Optional<ContenidoRef> buscar(UUID publicacionId);

    /**
     * Ajusta el contador denormalizado de comentarios.
     *
     * <p>Existe porque {@code publicacion.contador_comentarios} se lee en cada
     * elemento del feed y contarlo en vivo obligaria a una subconsulta agregada por
     * fila. El delta se aplica en la misma transaccion que el comentario.</p>
     *
     * @param delta {@code +1} al comentar, {@code -1} al eliminar
     */
    void ajustarContadorComentarios(UUID publicacionId, int delta);

    /** Mismo criterio que {@link #ajustarContadorComentarios}, para reacciones. */
    void ajustarContadorReacciones(UUID publicacionId, int delta);
}