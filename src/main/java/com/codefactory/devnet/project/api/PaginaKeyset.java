package com.codefactory.devnet.project.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.function.Function;

@Schema(name = "PaginaKeyset", description = "Pagina con cursor opaco.")
public record PaginaKeyset<T>(
        List<T> contenido,
        @Schema(nullable = true) String cursorSiguiente,
        boolean hayMas
) {
    public PaginaKeyset {
        contenido = contenido == null ? List.of() : List.copyOf(contenido);
    }

    public static <T> PaginaKeyset<T> de(List<T> filas, int limite, Function<T, String> aCursor) {
        boolean hayMas = filas.size() > limite;
        List<T> pagina = hayMas ? filas.subList(0, limite) : filas;
        String cursor = hayMas && !pagina.isEmpty()
                ? aCursor.apply(pagina.get(pagina.size() - 1))
                : null;
        return new PaginaKeyset<>(pagina, cursor, hayMas);
    }

    public <R> PaginaKeyset<R> mapear(Function<T, R> mapeador) {
        return new PaginaKeyset<>(contenido.stream().map(mapeador).toList(), cursorSiguiente, hayMas);
    }
}
