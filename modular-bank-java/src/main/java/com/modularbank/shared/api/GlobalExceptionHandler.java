package com.modularbank.shared.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

/**
 * Convierte ResponseStatusException en el contrato JSON {"message": "..."}
 * que esperan los clientes (en particular ms-transfers sobre /internal/**).
 * Sin esto, Spring despacha a /error y responde la página Whitelabel en HTML.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
            .body(Map.of("message", e.getReason() == null ? "" : e.getReason()));
    }
}
