package com.taskflowpro.taskservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Component
@Slf4j
public class GlobalExceptionHandler {

    private Mono<ServerResponse> buildErrorResponse(ServerRequest request, HttpStatus status, String error, String message) {
        ErrorResponse resp = new ErrorResponse(LocalDateTime.now(), status.value(), error, message, request.path());
        log.error("Exception handled [{}]: {} - {}", status, error, message);
        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(resp);
    }

    public Mono<ServerResponse> handleTaskNotFound(ServerRequest request, TaskNotFoundException ex) {
        log.warn("TaskNotFound: {}", ex.getMessage());
        return buildErrorResponse(request, HttpStatus.NOT_FOUND, "Task Not Found", ex.getMessage());
    }

    public Mono<ServerResponse> handleInvalidTaskData(ServerRequest request, InvalidTaskDataException ex) {
        log.warn("InvalidTaskData: {}", ex.getMessage());
        return buildErrorResponse(request, HttpStatus.BAD_REQUEST, "Invalid Task Data", ex.getMessage());
    }

    public Mono<ServerResponse> handleUnauthorized(ServerRequest request, UnauthorizedActionException ex) {
        log.warn("Unauthorized: {}", ex.getMessage());
        return buildErrorResponse(request, HttpStatus.FORBIDDEN, "Unauthorized", ex.getMessage());
    }

    public Mono<ServerResponse> handleTaskEventPublishing(ServerRequest request, TaskEventPublishingException ex) {
        log.error("Event publishing failure: {}", ex.getMessage(), ex);
        return buildErrorResponse(request, HttpStatus.INTERNAL_SERVER_ERROR, "Event Publishing Failed", ex.getMessage());
    }

    public Mono<ServerResponse> handleGenericError(ServerRequest request, Throwable ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return buildErrorResponse(request, HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", ex.getMessage());
    }
}
