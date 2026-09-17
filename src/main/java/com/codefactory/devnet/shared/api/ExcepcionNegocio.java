package com.codefactory.devnet.shared.api;

import java.util.List;

/**
 * Excepcion base de toda regla de negocio incumplida.
 *
 * <p>Es la unica via por la que el dominio y la capa de aplicacion senalan un error
 * al cliente. El manejador global la traduce al cuerpo de {@link RespuestaError}
 * usando el estado HTTP que declara su {@link CodigoError}, de modo que el
 * controlador nunca decide codigos HTTP.</p>
 *
 * <p>No se lanza directamente con un mensaje suelto: siempre con un codigo del
 * {@code enum} del modulo. Eso es lo que mantiene estable el catalogo de
 * {@code errorCode} que los clientes programan.</p>
 *
 * <p>Vive en {@code shared.api} y no en {@code shared.domain} por una razon
 * pragmatica: es el unico punto donde el dominio toca el mundo HTTP, y hacerlo
 * explicito es preferible a inventar una jerarquia paralela de excepciones que
 * habria que traducir en cada modulo.</p>
 */
public class ExcepcionNegocio extends RuntimeException {

    private final transient CodigoError codigo;
    private final transient List<DetalleError> detalles;

    public ExcepcionNegocio(CodigoError codigo) {
        this(codigo, codigo.mensajePorDefecto(), List.of());
    }

    public ExcepcionNegocio(CodigoError codigo, List<DetalleError> detalles) {
        this(codigo, codigo.mensajePorDefecto(), detalles);
    }

    public ExcepcionNegocio(CodigoError codigo, String mensaje, List<DetalleError> detalles) {
        super(mensaje);
        this.codigo = codigo;
        this.detalles = detalles == null ? List.of() : List.copyOf(detalles);
    }

    public CodigoError codigo() {
        return codigo;
    }

    public List<DetalleError> detalles() {
        return detalles;
    }

    // ---------------------------------------------------------------------
    // Atajos para las condiciones mas frecuentes
    // ---------------------------------------------------------------------

    /**
     * Recurso inexistente, o existente pero no visible para quien pregunta.
     *
     * <p>ADR-005 fija 404 tambien para lo segundo: responder 403 confirmaria la
     * existencia del recurso y filtraria informacion por diferencia de respuestas.
     * La autorizacion ocurre y se audita igual; lo que cambia es lo que se revela.</p>
     */
    public static ExcepcionNegocio noEncontrado(String recurso, Object id) {
        return new ExcepcionNegocio(
                CodigoErrorComun.RECURSO_NO_ENCONTRADO,
                CodigoErrorComun.RECURSO_NO_ENCONTRADO.mensajePorDefecto(),
                List.of(DetalleError.de(recurso, id, "no_encontrado")));
    }

    public static ExcepcionNegocio accesoDenegado(String razon) {
        return new ExcepcionNegocio(
                CodigoErrorComun.ACCESO_DENEGADO,
                CodigoErrorComun.ACCESO_DENEGADO.mensajePorDefecto(),
                List.of(DetalleError.de("autorizacion", razon)));
    }
}