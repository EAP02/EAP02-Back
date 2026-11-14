package com.codefactory.devnet.administration.domain;

import com.codefactory.devnet.shared.api.ExcepcionNegocio;

/**
 * Pagina pedida: numero (desde 0) y tamano (1..100).
 *
 * <p>El tope existe por §5.3 ("paginacion determinista y limitar consultas costosas"):
 * la tabla de auditoria crece con cada inicio de sesion y nadie debe poder pedirla
 * entera.</p>
 */
public record Paginacion(int pagina, int tamano) {

    public static final int TAMANO_MAXIMO = 100;

    public Paginacion {
        if (pagina < 0 || tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw new ExcepcionNegocio(CodigoErrorAdministracion.PAGINACION_INVALIDA);
        }
    }

    public long desplazamiento() {
        return (long) pagina * tamano;
    }
}
