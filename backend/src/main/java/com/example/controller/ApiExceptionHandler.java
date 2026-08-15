package com.example.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ensures a thrown {@link ResponseStatusException} reaches the browser as JSON
 * with its {@code reason} in a "message" field, so the UI can show actionable
 * errors (e.g. "set ANTHROPIC_API_KEY") instead of a bare status code.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handle(ResponseStatusException ex) {
        String message = ex.getReason() != null ? ex.getReason() : ex.getStatusCode().toString();
        return ResponseEntity.status(ex.getStatusCode())
            .body(Map.of("message", message, "status", ex.getStatusCode().value()));
    }
}
