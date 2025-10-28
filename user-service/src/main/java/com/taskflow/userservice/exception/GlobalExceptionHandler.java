package com.taskflow.userservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
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
     * Helper to build the standard error response with list details.
     */
    private Mono<ResponseEntity<ErrorResponse>> buildErrorResponse(HttpStatus status, String message, List<String> details) {
        ErrorResponse errorResponse = new ErrorResponse(status.value(), message, details);
        log.warn("Exception handled [{}]: {} - {}", status, message, details);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    /**
     * Handles validation errors (e.g., @NotNull, @Email).
     */
    @ExceptionHandler(ServerWebInputException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleValidationException(ServerWebInputException ex) {
        List<String> details = List.of(ex.getReason());
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Validation Failed", details);
    }

    /**
     * Handles "Resource Not Found" errors.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Resource Not Found", ex.getMessage());
    }

    /**
     * Handles "Duplicate Resource" errors.
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleDuplicateResourceException(DuplicateResourceException ex) {
        return buildErrorResponse(HttpStatus.CONFLICT, "Duplicate Resource", ex.getMessage());
    }

    /**
     * Handles "Invalid Login" errors.
     */
    @ExceptionHandler(InvalidLoginException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleInvalidLoginException(InvalidLoginException ex) {
        return buildErrorResponse(HttpStatus.UNAUTHORIZED, "Login Failed", ex.getMessage());
    }

    /**
     * Handles "Access Denied" errors (Authorization failures).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAccessDeniedException(AccessDeniedException ex) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Access Denied", ex.getMessage());
    }
    
    /**
     * Handles all other uncaught exceptions.
     */
    @ExceptionHandler(Throwable.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGenericException(Throwable ex) {
        log.error("An unexpected error occurred in user-service", ex);
        
        if (ex instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
            String errorBody = ((org.springframework.web.reactive.function.client.WebClientResponseException) ex).getResponseBodyAsString();
            log.error("Service-to-service call failed: {}", errorBody);
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "Downstream service failed: " + errorBody);
        }
        
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An unexpected error occurred. Please try again later.");
    }
}