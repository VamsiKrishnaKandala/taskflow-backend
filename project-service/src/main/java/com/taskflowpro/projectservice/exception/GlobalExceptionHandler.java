package com.taskflowpro.projectservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Centralized Global Exception Handler for functional WebFlux endpoints.
 * Converts all thrown exceptions into consistent JSON responses.
 */
@Component
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Builds a standard error response with structured JSON.
     */
    private Mono<ServerResponse> buildErrorResponse(ServerRequest request, HttpStatus status, String error, String message) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(error)
                .message(message)
                .path(request.path())
                .build();

        log.error("Exception handled [{}]: {} - {}", status, error, message);
        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(errorResponse);
    }

    /**
     * Handles ProjectNotFoundException.
     */
    public Mono<ServerResponse> handleProjectNotFound(ServerRequest request, ProjectNotFoundException ex) {
        log.warn("ProjectNotFoundException: {}", ex.getMessage());
        return buildErrorResponse(request, HttpStatus.NOT_FOUND, "Project Not Found", ex.getMessage());
    }

    /**
     * Handles InvalidProjectDataException.
     */
    public Mono<ServerResponse> handleInvalidProjectData(ServerRequest request, InvalidProjectDataException ex) {
        log.warn("InvalidProjectDataException: {}", ex.getMessage());
        return buildErrorResponse(request, HttpStatus.BAD_REQUEST, "Invalid Project Data", ex.getMessage());
    }

    /**
     * Handles validation errors (e.g., IllegalArgumentException from ProjectHandler).
     */
    public Mono<ServerResponse> handleValidationError(ServerRequest request, IllegalArgumentException ex) {
        log.warn("Validation error: {}", ex.getMessage());
        return buildErrorResponse(request, HttpStatus.BAD_REQUEST, "Validation Error", ex.getMessage());
    }

    /**
     * Handles all unhandled exceptions (fallback).
     */
    public Mono<ServerResponse> handleGenericError(ServerRequest request, Throwable ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return buildErrorResponse(request, HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", ex.getMessage());
    }

    /**
     * Handles role-based authorization failures.
     */
    public Mono<ServerResponse> handleUnauthorizedAction(ServerRequest request, UnauthorizedActionException ex) {
        log.warn("UnauthorizedActionException: {}", ex.getMessage());
        return buildErrorResponse(request, HttpStatus.FORBIDDEN, "Unauthorized Action", ex.getMessage());
    }

    /**
     * Handles reactive event publishing failures.
     */
    public Mono<ServerResponse> handleProjectEventPublishing(ServerRequest request, ProjectEventPublishingException ex) {
        log.error("ProjectEventPublishingException: {}", ex.getMessage(), ex);
        return buildErrorResponse(request, HttpStatus.INTERNAL_SERVER_ERROR, "Project Event Publishing Failed", ex.getMessage());
    }
}