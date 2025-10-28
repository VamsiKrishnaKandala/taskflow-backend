package com.taskflowpro.projectservice.exception;

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

    @ExceptionHandler(ProjectNotFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleProjectNotFound(ProjectNotFoundException ex) {
        return buildErrorResponse(HttpStatus.NOT_FOUND, "Project Not Found", ex.getMessage());
    }

    @ExceptionHandler(InvalidProjectDataException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleInvalidProjectData(InvalidProjectDataException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Invalid Project Data", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleValidationError(IllegalArgumentException ex) {
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Validation Error", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAccessDenied(AccessDeniedException ex) {
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Access Denied", ex.getMessage());
    }
    
    @ExceptionHandler(UnauthorizedActionException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleUnauthorizedAction(UnauthorizedActionException ex) {
        log.warn("UnauthorizedActionException: {}", ex.getMessage());
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Unauthorized Action", ex.getMessage());
    }

    @ExceptionHandler(ProjectEventPublishingException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleProjectEventPublishing(ProjectEventPublishingException ex) {
        log.error("ProjectEventPublishingException: {}", ex.getMessage(), ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Project Event Publishing Failed", ex.getMessage());
    }

    @ExceptionHandler(Throwable.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGenericError(Throwable ex) {
        log.error("Unhandled exception in project-service: {}", ex.getMessage(), ex);
        if (ex instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
            String errorBody = ((org.springframework.web.reactive.function.client.WebClientResponseException) ex).getResponseBodyAsString();
            log.error("Service-to-service call failed: {}", errorBody);
            return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "Downstream service failed: " + errorBody);
        }
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An unexpected error occurred.");
    }
}