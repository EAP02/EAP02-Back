package com.codefactory.devnet.administration.domain;

import java.util.List;

/** Una pagina de resultados con el total, para que el cliente sepa cuantas hay. */
public record Pagina<T>(List<T> contenido, int pagina, int tamano, long total) {

    public Pagina {
        contenido = List.copyOf(contenido);
    }

    public int totalPaginas() {
        return (int) ((total + tamano - 1) / tamano);
    }

    public <R> Pagina<R> mapear(java.util.function.Function<T, R> f) {
        return new Pagina<>(contenido.stream().map(f).toList(), pagina, tamano, total);
    }
}
