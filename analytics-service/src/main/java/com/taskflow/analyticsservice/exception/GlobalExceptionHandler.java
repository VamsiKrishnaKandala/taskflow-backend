package com.taskflow.analyticsservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Global exception handler for the Analytics Service.
 * Catches exceptions and returns standardized ErrorResponse.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE) // Ensure this runs before Spring's default handlers
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Helper to build the standard error response.
     */
    private Mono<ResponseEntity<ErrorResponse>> buildErrorResponse(HttpStatus status, String message, String detail) {
        ErrorResponse errorResponse = new ErrorResponse(status.value(), message, detail);
        log.warn("Exception handled [{}]: {} - {}", status, message, detail);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    /**
     * Handles our custom authorization failures (e.g., non-admin access).
     * @return 403 FORBIDDEN
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAccessDenied(AccessDeniedException ex) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Access Denied", ex.getMessage());
    }

    /**
     * Handles errors from downstream services (Task, Project, User).
     * @return The status code from the downstream service (e.g., 404, 403).
     */
    @ExceptionHandler(WebClientResponseException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleWebClientError(WebClientResponseException ex) {
        log.warn("Downstream service error: {} {} - {}", ex.getStatusCode(), ex.getMessage(), ex.getResponseBodyAsString());
        // Pass through the error from the service we called
        return buildErrorResponse(
            HttpStatus.valueOf(ex.getStatusCode().value()), 
            "Downstream Service Error", 
            ex.getResponseBodyAsString()
        );
    }

    /**
     * Handles all other uncaught exceptions as a fallback.
     * @return 500 INTERNAL_SERVER_ERROR
     */
    @ExceptionHandler(Throwable.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGenericError(Throwable ex) {
        log.error("An unexpected error occurred in analytics-service", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An unexpected error occurred.");
    }
}