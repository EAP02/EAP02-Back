package com.codefactory.devnet.shared.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.function.Function;

/**
 * Pagina de resultados con cursor, no con desplazamiento.
 *
 * <p>El lineamiento 5.3 exige "paginacion determinista". {@code OFFSET} no lo es por
 * dos razones: PostgreSQL lee y descarta todas las filas anteriores, asi que la
 * pagina 50 cuesta 50 veces la pagina 1; y si alguien publica mientras el usuario
 * pagina, las filas se desplazan y aparecen elementos duplicados u omitidos.</p>
 *
 * <p>Con cursor, la consulta compara {@code (publicado_en, id) < (:cursor)}, lo que
 * ataca directamente el indice compuesto en un solo recorrido descendente y devuelve
 * siempre el mismo conjunto aunque cambien los datos.</p>
 *
 * <p>Se usa en el feed, la bandeja de mensajes y toda coleccion ordenada por tiempo.
 * Para catalogos pequenos y acotados basta una lista completa.</p>
 *
 * @param contenido        elementos de esta pagina
 * @param cursorSiguiente  opaco para el cliente: se devuelve tal cual en la siguiente
 *                         peticion. Nulo cuando no hay mas paginas.
 * @param hayMas           evita que el cliente tenga que interpretar el cursor
 */
@Schema(name = "PaginaKeyset", description = "Pagina con cursor. El cliente devuelve cursorSiguiente sin interpretarlo.")
public record PaginaKeyset<T>(
        List<T> contenido,
        @Schema(nullable = true) String cursorSiguiente,
        boolean hayMas
) {
    public PaginaKeyset {
        contenido = contenido == null ? List.of() : List.copyOf(contenido);
    }

    public static <T> PaginaKeyset<T> vacia() {
        return new PaginaKeyset<>(List.of(), null, false);
    }

    /**
     * Construye la pagina a partir de una consulta que pidio {@code limite + 1} filas.
     *
     * <p>Pedir una fila de mas es como se sabe si hay pagina siguiente sin ejecutar
     * un {@code COUNT} sobre toda la coleccion.</p>
     *
     * @param filas    resultado de la consulta, de hasta {@code limite + 1} elementos
     * @param limite   tamano real de pagina solicitado
     * @param aCursor  como derivar el cursor del ultimo elemento devuelto
     */
    public static <T> PaginaKeyset<T> de(List<T> filas, int limite, Function<T, String> aCursor) {
        boolean hayMas = filas.size() > limite;
        List<T> pagina = hayMas ? filas.subList(0, limite) : filas;
        String cursor = hayMas && !pagina.isEmpty()
                ? aCursor.apply(pagina.get(pagina.size() - 1))
                : null;
        return new PaginaKeyset<>(pagina, cursor, hayMas);
    }

    /** Aplica una transformacion al contenido conservando la paginacion. */
    public <R> PaginaKeyset<R> mapear(Function<T, R> mapeador) {
        return new PaginaKeyset<>(contenido.stream().map(mapeador).toList(), cursorSiguiente, hayMas);
    }
}