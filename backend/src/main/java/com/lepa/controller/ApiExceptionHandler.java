package com.lepa.controller;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String,Object>> badRequest(RuntimeException ex) {
        return ResponseEntity.badRequest().body(error(ex.getMessage()));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String,Object>> notFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error(ex.getMessage()));
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<Map<String,Object>> aiServiceResponse(RestClientResponseException ex) {
        String detail = ex.getResponseBodyAsString();
        String message = "AI service returned HTTP " + ex.getStatusCode().value();
        if (detail != null && !detail.isBlank()) {
            message += ": " + detail;
        }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(error(message));
    }


    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String,Object>> ioFailure(IOException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(error("Document/AI forwarding failed: " + ex.getMessage()));
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<Map<String,Object>> aiServiceUnavailable(ResourceAccessException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(error("AI service is unreachable. Make sure FastAPI is running on the configured port."));
    }

    private Map<String,Object> error(String message) {
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("error", message);
        body.put("time", Instant.now().toString());
        return body;
    }
}
