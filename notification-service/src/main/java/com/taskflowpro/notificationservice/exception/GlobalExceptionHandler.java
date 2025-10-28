package com.taskflowpro.notificationservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Builds a standard error response entity.
     */
    private Mono<ResponseEntity<ErrorResponse>> buildErrorResponse(HttpStatus status, String error, String message) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(error)
                .message(message)
                .build();
        log.warn("Exception handled [{}]: {} - {}", status, error, message);
        return Mono.just(ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(errorResponse));
    }

    /**
     * Handles role-based authorization failures.
     * @return 403 FORBIDDEN
     */
    @ExceptionHandler(AccessDeniedException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAccessDenied(AccessDeniedException ex) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Access Denied", ex.getMessage());
    }

    /**
     * Handles validation errors.
     * @return 400 BAD_REQUEST
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleIllegalArgument(IllegalArgumentException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Bad Request", ex.getMessage());
    }
    
    // Add handlers for any other custom exceptions like NotificationNotFoundException if you create them
    // @ExceptionHandler(NotificationNotFoundException.class)
    // public Mono<ResponseEntity<ErrorResponse>> handleNotFound(NotificationNotFoundException ex) {
    //    return buildErrorResponse(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage());
    // }

    /**
     * Handles all unhandled exceptions (fallback).
     * @return 500 INTERNAL_SERVER_ERROR
     */
    @ExceptionHandler(Throwable.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGenericError(Throwable ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        
        // Handle downstream service errors
        if (ex instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
            String errorBody = ((org.springframework.web.reactive.function.client.WebClientResponseException) ex).getResponseBodyAsString();
            log.error("Service-to-service call failed: {}", errorBody);
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "Downstream service failed: " + errorBody);
        }
        
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An unexpected error occurred.");
    }
}