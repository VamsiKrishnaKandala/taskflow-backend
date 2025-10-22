package com.taskflow.userservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
// Import the new exception
import org.springframework.web.server.ServerWebInputException; // <-- ADD THIS IMPORT
import reactor.core.publisher.Mono;

import java.util.List;
// We no longer need this import
// import org.springframework.web.bind.support.WebExchangeBindException; 

/**
 * Global exception handler for the User Service.
 * Catches custom exceptions and validation errors, returning a
 * standardized ErrorResponse.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles validation errors (e.g., @NotNull, @Email).
     * This catches the ServerWebInputException we manually throw from our handler.
     *
     * @param ex The validation exception.
     * @return A Mono<ResponseEntity> with HTTP 400 (Bad Request).
     */
    // --- THIS METHOD IS CHANGED ---
    @ExceptionHandler(ServerWebInputException.class) 
    public Mono<ResponseEntity<ErrorResponse>> handleValidationException(ServerWebInputException ex) {
        log.warn("Validation error: {}", ex.getMessage());
        
        // The exception's "reason" is the string we built
        List<String> details = List.of(ex.getReason()); 

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Validation Failed",
                details
        );
        return Mono.just(ResponseEntity.badRequest().body(errorResponse));
    }
    // --- END OF CHANGE ---

    /**
     * Handles "Resource Not Found" errors.
     * @param ex The custom exception.
     * @return A Mono<ResponseEntity> with HTTP 404 (Not Found).
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                "Resource Not Found",
                ex.getMessage()
        );
        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse));
    }

    /**
     * Handles "Duplicate Resource" errors.
     * @param ex The custom exception.
     * @return A Mono<ResponseEntity> with HTTP 409 (Conflict).
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleDuplicateResourceException(DuplicateResourceException ex) {
        log.warn("Duplicate resource attempt: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                "Duplicate Resource",
                ex.getMessage()
        );
        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse));
    }

    /**
     * Handles all other uncaught exceptions.
     * @param ex The generic exception.
     * @return A Mono<ResponseEntity> with HTTP 500 (Internal Server Error).
     */
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGenericException(Exception ex) {
        log.error("An unexpected error occurred", ex);
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "An unexpected error occurred. Please try again later."
        );
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse));
    }
    /**
     * Handles "Invalid Login" errors.
     * @param ex The custom exception.
     * @return A Mono<ResponseEntity> with HTTP 401 (Unauthorized).
     */
    @ExceptionHandler(InvalidLoginException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleInvalidLoginException(InvalidLoginException ex) {
        log.warn("Login attempt failed: {}", ex.getMessage());
        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                "Login Failed",
                ex.getMessage()
        );
        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse));
    }
}