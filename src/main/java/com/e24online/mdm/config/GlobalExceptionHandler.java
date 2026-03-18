package com.e24online.mdm.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for REST controllers.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String GENERIC_INTERNAL_MESSAGE = "An unexpected error occurred";

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex,
                                                                             ServerWebExchange exchange) {
        log.warn("Response status error: {} - {}", ex.getStatusCode(), ex.getReason());
        HttpStatus status = (ex.getStatusCode() instanceof HttpStatus) ? (HttpStatus) ex.getStatusCode() : HttpStatus.valueOf(ex.getStatusCode().value());
        String safeMessage = status.is5xxServerError() ? GENERIC_INTERNAL_MESSAGE : defaultIfBlank(ex.getReason(), status.getReasonPhrase());
        return buildErrorResponse(status, safeMessage, exchange);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(AuthenticationException ex,
                                                                             ServerWebExchange exchange) {
        log.warn("Authentication error: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Authentication failed", exchange);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(AccessDeniedException ex,
                                                                           ServerWebExchange exchange) {
        log.warn("Access denied: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Access denied", exchange);
    }

    @ExceptionHandler(AuthenticationCredentialsNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleCredentialsNotFoundException(AuthenticationCredentialsNotFoundException ex,
                                                                                  ServerWebExchange exchange) {
        log.warn("Credentials not found: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Authentication required", exchange);
    }

    @ExceptionHandler(SQLException.class)
    public ResponseEntity<Map<String, Object>> handleSQLException(SQLException ex,
                                                                  ServerWebExchange exchange) {
        log.error("Database SQL error: {} - SQLState: {} - Type: {}",
                ex.getMessage(), ex.getSQLState(), ex.getClass().getSimpleName());
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Database error", exchange);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateKeyException(DuplicateKeyException ex,
                                                                           ServerWebExchange exchange) {
        ex.getMostSpecificCause();
        log.warn("Duplicate key violation: {}", ex.getMostSpecificCause().getMessage());
        return buildErrorResponse(HttpStatus.CONFLICT, "Resource already exists", exchange);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolationException(DataIntegrityViolationException ex,
                                                                                      ServerWebExchange exchange) {
        ex.getMostSpecificCause();
        String detail = ex.getMostSpecificCause().getMessage();
        if (detail == null) {
            detail = "";
        }
        String normalized = detail.toLowerCase();
        if (normalized.contains("unique") || normalized.contains("duplicate key")) {
            log.warn("Data integrity conflict: {}", detail);
            return buildErrorResponse(HttpStatus.CONFLICT, "Resource already exists", exchange);
        }
        log.warn("Data integrity validation failure: {}", detail);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Request violates data constraints", exchange);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolationException(ConstraintViolationException ex,
                                                                                  ServerWebExchange exchange) {
        String message = ex.getConstraintViolations().stream()
                .findFirst()
                .map(v -> defaultIfBlank(v.getMessage(), "Validation failed"))
                .orElse("Validation failed");
        log.warn("Constraint validation failed: {}", message);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, message, exchange);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<Map<String, Object>> handleWebExchangeBindException(WebExchangeBindException ex,
                                                                              ServerWebExchange exchange) {
        String message = ex.getFieldErrors().stream()
                .findFirst()
                .map(this::toFieldErrorMessage)
                .orElse("Validation failed");
        log.warn("Request binding validation failed: {}", message);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, message, exchange);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex,
                                                                              ServerWebExchange exchange) {
        log.warn("Invalid argument: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, defaultIfBlank(ex.getMessage(), "Invalid request"), exchange);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex,
                                                                      ServerWebExchange exchange) {
        log.error("Unexpected error: {} - Type: {}",
                ex.getMessage(), ex.getClass().getSimpleName());
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_INTERNAL_MESSAGE, exchange);
    }

    private ResponseEntity<Map<String, Object>> buildErrorResponse(HttpStatus status,
                                                                   String message,
                                                                   ServerWebExchange exchange) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", OffsetDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", getRequestPath(exchange));
        return ResponseEntity.status(status.value()).body(body);
    }

    private String getRequestPath(ServerWebExchange exchange) {
        if (exchange == null) {
            return "unknown";
        } else {
            exchange.getRequest();
            exchange.getRequest().getPath();
        }
        return exchange.getRequest().getPath().value();
    }

    private String defaultIfBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private String toFieldErrorMessage(FieldError error) {
        String field = defaultIfBlank(error.getField(), "field");
        String msg = defaultIfBlank(error.getDefaultMessage(), "is invalid");
        return field + " " + msg;
    }
}
