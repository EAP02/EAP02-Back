package com.codefactory.devnet.discussion.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Puerto de persistencia de discusiones. Lo implementa {@code infrastructure}.
 */
public interface RepositorioDiscusiones {

    /**
     * Persiste una discusion recien abierta.
     *
     * <p>Escribe en tres tablas dentro de la misma transaccion: {@code publicacion}
     * (supertipo), {@code publicacion_discusion} (subtipo) y
     * {@code publicacion_tecnologia}. O entran las tres o ninguna: una publicacion de
     * tipo DISCUSION sin su subtipo violaria el invariante del discriminador.</p>
     */
    void guardar(Discusion discusion);

    /**
     * Una discusion visible al publico, o vacio si no existe o no esta publicada.
     *
     * <p>Filtra por estado aqui y no en el caso de uso: no hay ningun camino de lectura
     * publica que deba ver una discusion oculta o archivada.</p>
     */
    Optional<DiscusionPublicada> publicadaPorId(UUID discusionId);

    /**
     * HU-11, criterio 4: los usuarios distintos que han comentado, por orden de su
     * primera intervencion.
     *
     * <p>Solo cuentan los comentarios visibles: quien solo dejo comentarios eliminados
     * ya no participa en el hilo.</p>
     */
    List<Participante> participantes(UUID discusionId);

    /**
     * Proyeccion de lectura. Lleva identificadores de autor y etiquetas, no sus
     * nombres: esos pertenecen a {@code identity} y a {@code project}, y se resuelven en
     * la capa de aplicacion a traves de sus contratos.
     */
    record DiscusionPublicada(
            UUID id,
            UUID autorId,
            String titulo,
            String cuerpo,
            CategoriaDiscusion categoria,
            boolean resuelta,
            Set<Short> tecnologias,
            Instant publicadoEn
    ) { }

    /**
     * @param comentarios       cuantos comentarios visibles lleva en el hilo
     * @param primeraIntervencion cuando comento por primera vez
     */
    record Participante(UUID usuarioId, int comentarios, Instant primeraIntervencion) { }
}
