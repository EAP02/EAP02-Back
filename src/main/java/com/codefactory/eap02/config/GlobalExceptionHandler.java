package com.codefactory.eap02.config;

import com.codefactory.eap02.auth.DTO.ErrorResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Manejador global de errores de la API.
 *
 * Cierra dos huecos detectados por las pruebas de integracion del Sprint 1:
 *  - Los errores de @Valid devolvian 400 sin cuerpo (no indicaban el campo). Ahora
 *    se responde 400 con un mensaje que nombra el/los campos invalidos (CAF-10).
 *  - La colision de correo duplicado bajo concurrencia escapaba como 500. El chequeo
 *    existsByEmail() en el service no es suficiente bajo una condicion de carrera:
 *    dos peticiones simultaneas pueden pasarlo antes de que cualquiera haga save().
 *    La unica garantia es la restriccion UNIQUE de la base de datos; aqui esa
 *    violacion se traduce a 409 EMAIL_ALREADY_EXISTS (RN-07, CAF-08).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String detalle = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (detalle.isBlank()) {
            detalle = "Datos de entrada invalidos";
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse("VALIDATION_ERROR", detalle, null));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("EMAIL_ALREADY_EXISTS", "Ya existe una cuenta con ese correo", null));
    }
}
