package com.codefactory.devnet.shared.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;

/**
 * Traduce toda excepcion al cuerpo unico de {@link RespuestaError}.
 *
 * <p>Centralizarlo aqui es lo que impide que un modulo se desvie del formato:
 * ningun controlador construye respuestas de error por su cuenta, y un test de
 * arquitectura lo verifica.</p>
 *
 * <p>Dos decisiones del contrato de errores se reflejan directamente aqui:</p>
 * <ul>
 *   <li>Un recurso que existe pero no es visible para quien pregunta responde 404,
 *       no 403, para no filtrar su existencia por diferencia de respuestas.</li>
 *   <li>409 es conflicto con el estado actual del recurso; 422 es una regla de
 *       negocio incumplible con independencia del estado. Distinguirlos evita el
 *       400 para todo.</li>
 * </ul>
 */
@RestControllerAdvice
public class ManejadorGlobalErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorGlobalErrores.class);

    // ------------------------------------------------------------------
    // Reglas de negocio
    // ------------------------------------------------------------------

    @ExceptionHandler(ExcepcionNegocio.class)
    public ResponseEntity<RespuestaError> negocio(ExcepcionNegocio ex, HttpServletRequest req) {
        CodigoError codigo = ex.codigo();
        // Nivel WARN, no ERROR: una regla de negocio que se cumple no es un fallo
        // del sistema. Reservar ERROR para lo que de verdad hay que ir a mirar.
        log.warn("Regla de negocio incumplida: {} en {} {}",
                codigo.codigo(), req.getMethod(), req.getRequestURI());
        return construir(codigo, ex.getMessage(), ex.detalles(), req);
    }

    // ------------------------------------------------------------------
    // Validacion de entrada
    // ------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RespuestaError> validacion(MethodArgumentNotValidException ex,
                                                     HttpServletRequest req) {
        List<DetalleError> detalles = ex.getBindingResult().getAllErrors().stream()
                .map(error -> {
                    String campo = error instanceof FieldError fe ? fe.getField() : error.getObjectName();
                    Object valor = error instanceof FieldError fe ? fe.getRejectedValue() : null;
                    return DetalleError.de(campo, valor, error.getDefaultMessage());
                })
                .toList();
        return construir(CodigoErrorComun.VALIDACION_FALLIDA, null, detalles, req);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<RespuestaError> restriccion(ConstraintViolationException ex,
                                                      HttpServletRequest req) {
        List<DetalleError> detalles = ex.getConstraintViolations().stream()
                .map(v -> DetalleError.de(
                        v.getPropertyPath().toString(),
                        v.getInvalidValue(),
                        v.getMessage()))
                .toList();
        return construir(CodigoErrorComun.VALIDACION_FALLIDA, null, detalles, req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RespuestaError> cuerpoIlegible(HttpMessageNotReadableException ex,
                                                         HttpServletRequest req) {
        // El mensaje original expone nombres de clase y posiciones del parser:
        // se registra pero no se devuelve.
        log.warn("Cuerpo ilegible en {} {}: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return construir(CodigoErrorComun.CUERPO_ILEGIBLE, null, List.of(), req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<RespuestaError> tipoInvalido(MethodArgumentTypeMismatchException ex,
                                                       HttpServletRequest req) {
        return construir(CodigoErrorComun.PARAMETRO_INVALIDO, null,
                List.of(DetalleError.de(ex.getName(), ex.getValue(), "tipo_invalido")), req);
    }

    // ------------------------------------------------------------------
    // Seguridad
    // ------------------------------------------------------------------

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<RespuestaError> noAutenticado(AuthenticationException ex,
                                                        HttpServletRequest req) {
        log.warn("Autenticacion fallida en {} {}", req.getMethod(), req.getRequestURI());
        return construir(CodigoErrorComun.AUTH_REQUERIDA, null, List.of(), req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<RespuestaError> accesoDenegado(AccessDeniedException ex,
                                                         HttpServletRequest req) {
        // HU-06, criterio 1: "recibe error 403 y queda registrado el intento".
        //
        // Aqui confluyen los dos caminos por los que se deniega: el de la cadena de
        // filtros, que SeguridadConfig redirige a este manejador, y el de
        // @PreAuthorize, que lanza desde el interceptor de method security. Un solo
        // punto evita auditar dos veces o ninguna.
        // Nunca se registra el token ni las credenciales (lineamiento 6.2).
        log.warn("Acceso denegado en {} {}", req.getMethod(), req.getRequestURI());

        return construir(CodigoErrorComun.ACCESO_DENEGADO, null, List.of(), req);
    }

    // ------------------------------------------------------------------
    // Conflictos de datos
    // ------------------------------------------------------------------

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<RespuestaError> concurrencia(OptimisticLockingFailureException ex,
                                                       HttpServletRequest req) {
        return construir(CodigoErrorComun.CONFLICTO_CONCURRENCIA, null, List.of(), req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<RespuestaError> integridad(DataIntegrityViolationException ex,
                                                     HttpServletRequest req) {
        // La causa lleva el nombre de la restriccion violada y a veces valores de
        // la fila: util en el log, nunca en la respuesta.
        log.warn("Violacion de integridad en {} {}: {}",
                req.getMethod(), req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return construir(CodigoErrorComun.CONFLICTO_DATOS, null, List.of(), req);
    }

    // ------------------------------------------------------------------
    // Enrutamiento
    // ------------------------------------------------------------------

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<RespuestaError> rutaNoEncontrada(NoResourceFoundException ex,
                                                           HttpServletRequest req) {
        return construir(CodigoErrorComun.RECURSO_NO_ENCONTRADO, null, List.of(), req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<RespuestaError> metodoNoPermitido(HttpRequestMethodNotSupportedException ex,
                                                            HttpServletRequest req) {
        return construir(CodigoErrorComun.METODO_NO_PERMITIDO, null,
                List.of(DetalleError.de("metodo", ex.getMethod(), "no_soportado")), req);
    }

    // ------------------------------------------------------------------
    // Ultimo recurso
    // ------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RespuestaError> inesperado(Exception ex, HttpServletRequest req) {
        // Aqui si va ERROR y con la traza completa: es lo unico que el equipo tiene
        // para diagnosticar. El usuario solo recibe el traceId que lo correlaciona.
        log.error("Error no controlado en {} {}", req.getMethod(), req.getRequestURI(), ex);
        return construir(CodigoErrorComun.ERROR_INTERNO, null, List.of(), req);
    }

    // ------------------------------------------------------------------

    private ResponseEntity<RespuestaError> construir(CodigoError codigo,
                                                     String mensaje,
                                                     List<DetalleError> detalles,
                                                     HttpServletRequest req) {
        RespuestaError cuerpo = new RespuestaError(
                codigo.codigo(),
                mensaje != null ? mensaje : codigo.mensajePorDefecto(),
                detalles,
                FiltroTraceId.actual(),
                Instant.now(),
                req.getRequestURI());
        return ResponseEntity.status(codigo.estadoHttp()).body(cuerpo);
    }
}
