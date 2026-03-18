package com.e24online.mdm.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockServerWebExchange exchange;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/test/resource").build());
    }

    @Test
    void sqlExceptionResponse_isSanitized() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleSQLException(new SQLException("relation auth_user does not exist", "42P01"), exchange);

        assertEquals(500, response.getStatusCode().value());
        assertEquals("Database error", response.getBody().get("message"));
        assertEquals("/api/test/resource", response.getBody().get("path"));
    }

    @Test
    void duplicateKeyException_mapsToConflict() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleDuplicateKeyException(new DuplicateKeyException("duplicate key value violates unique constraint"), exchange);

        assertEquals(409, response.getStatusCode().value());
        assertEquals("Resource already exists", response.getBody().get("message"));
    }

    @Test
    void dataIntegrityViolation_nonUnique_mapsToBadRequest() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleDataIntegrityViolationException(new DataIntegrityViolationException("violates check constraint"), exchange);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("Request violates data constraints", response.getBody().get("message"));
    }

    @Test
    void dataIntegrityViolation_unique_mapsToConflict() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleDataIntegrityViolationException(new DataIntegrityViolationException("duplicate key value violates unique constraint"), exchange);

        assertEquals(409, response.getStatusCode().value());
        assertEquals("Resource already exists", response.getBody().get("message"));
    }

    @Test
    void constraintViolation_mapsToBadRequest() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleConstraintViolationException(new ConstraintViolationException("invalid", Set.of()), exchange);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("Validation failed", response.getBody().get("message"));
    }

    @Test
    void genericExceptionResponse_isSanitized() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleGenericException(new RuntimeException("stack details"), exchange);

        assertEquals(500, response.getStatusCode().value());
        assertEquals("An unexpected error occurred", response.getBody().get("message"));
    }

    @Test
    void responseStatusException_keepsClientErrorMessageButNotServerErrorMessage() {
        ResponseEntity<Map<String, Object>> badRequest =
                handler.handleResponseStatusException(new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid username"), exchange);
        ResponseEntity<Map<String, Object>> internalError =
                handler.handleResponseStatusException(new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "sql internals"), exchange);

        assertEquals(400, badRequest.getStatusCode().value());
        assertEquals("invalid username", badRequest.getBody().get("message"));
        assertEquals(500, internalError.getStatusCode().value());
        assertEquals("An unexpected error occurred", internalError.getBody().get("message"));
    }

    @Test
    void responseStatusException_usesReasonPhraseWhenReasonBlank() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleResponseStatusException(new ResponseStatusException(HttpStatus.BAD_REQUEST, "   "), exchange);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("Bad Request", response.getBody().get("message"));
    }

    @Test
    void responseStatusException_supportsNonHttpStatusCodeImplementation() {
        HttpStatusCode customStatus = HttpStatusCode.valueOf(499);

        assertThrows(IllegalArgumentException.class,
                () -> handler.handleResponseStatusException(new ResponseStatusException(customStatus, null), exchange));
    }

    @Test
    void responseStatusException_nonEnumStatusCodeWithKnownValue_isHandled() throws Exception {
        Class<?> defaultCodeClass = Class.forName("org.springframework.http.DefaultHttpStatusCode");
        var constructor = defaultCodeClass.getDeclaredConstructor(int.class);
        constructor.setAccessible(true);
        HttpStatusCode nonEnumStatus = (HttpStatusCode) constructor.newInstance(400);

        ResponseEntity<Map<String, Object>> response =
                handler.handleResponseStatusException(new ResponseStatusException(nonEnumStatus, null), exchange);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("Bad Request", response.getBody().get("message"));
    }

    @Test
    void authenticationException_mapsToUnauthorized() {
        AuthenticationException ex = new AuthenticationException("bad auth") {
        };

        ResponseEntity<Map<String, Object>> response =
                handler.handleAuthenticationException(ex, exchange);

        assertEquals(401, response.getStatusCode().value());
        assertEquals("Authentication failed", response.getBody().get("message"));
    }

    @Test
    void accessDeniedException_mapsToForbidden() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleAccessDeniedException(new AccessDeniedException("denied"), exchange);

        assertEquals(403, response.getStatusCode().value());
        assertEquals("Access denied", response.getBody().get("message"));
    }

    @Test
    void credentialsNotFoundException_mapsToUnauthorized() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleCredentialsNotFoundException(new AuthenticationCredentialsNotFoundException("missing"), exchange);

        assertEquals(401, response.getStatusCode().value());
        assertEquals("Authentication required", response.getBody().get("message"));
    }

    @Test
    void illegalArgumentException_usesFallbackWhenMessageBlank() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleIllegalArgumentException(new IllegalArgumentException("   "), exchange);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("Invalid request", response.getBody().get("message"));
    }

    @Test
    void illegalArgumentException_usesFallbackWhenMessageNull() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleIllegalArgumentException(new IllegalArgumentException((String) null), exchange);

        assertEquals(400, response.getStatusCode().value());
        assertEquals("Invalid request", response.getBody().get("message"));
    }

    @Test
    void nullExchange_usesUnknownPath() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleGenericException(new RuntimeException("x"), null);

        assertEquals(500, response.getStatusCode().value());
        assertEquals("unknown", response.getBody().get("path"));
    }
}
