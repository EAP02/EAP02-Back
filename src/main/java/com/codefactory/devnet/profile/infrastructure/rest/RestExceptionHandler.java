package com.codefactory.devnet.profile.infrastructure.rest;

import com.codefactory.devnet.profile.domain.InvalidProfileException;
import com.codefactory.devnet.profile.domain.ProfileNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class RestExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) errors.put(error.getField(), error.getDefaultMessage());
        return ResponseEntity.badRequest().body(Map.of("message", "Hay campos invalidos.", "errors", errors));
    }
    @ExceptionHandler(InvalidProfileException.class)
    ResponseEntity<Map<String, Object>> invalidProfile(InvalidProfileException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage(), "errors", Map.of(exception.getField(), exception.getMessage())));
    }
    @ExceptionHandler(ProfileNotFoundException.class)
    ResponseEntity<Map<String, String>> profileNotFound(ProfileNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Perfil no encontrado."));
    }
}
