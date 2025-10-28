package com.taskflowpro.taskservice.handler;

import com.taskflowpro.taskservice.dto.*;
import com.taskflowpro.taskservice.service.TaskService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders; // Import HttpHeaders
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

// Import exceptions to handle them directly
import com.taskflowpro.taskservice.exception.AccessDeniedException;
import com.taskflowpro.taskservice.exception.TaskNotFoundException;
import org.springframework.http.HttpStatus;
import com.taskflowpro.taskservice.exception.ErrorResponse;
import java.time.LocalDateTime;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handler for Task endpoints (functional).
 * Validates incoming payloads and delegates to TaskService for business logic.
 * Reads authorization headers (X-User-Id, X-User-Role) and passes them to the service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskHandler {

    private final TaskService taskService;
    private final Validator validator; // Validator can be null in test environments

    // --- Authorization Header Helpers ---

    /**
     * Extracts X-User-Id header from the request.
     */
    private String getRequesterId(ServerRequest request) {
        // We rely on the gateway to send this; if it's null, the service will handle it (likely as AccessDenied)
        return request.headers().firstHeader("X-User-Id");
    }

    /**
     * Extracts X-User-Role header from the request.
     */
    private String getRequesterRole(ServerRequest request) {
        return request.headers().firstHeader("X-User-Role");
    }

    /**
     * Extracts the raw Authorization header to forward to other services.
     */
    private String getAuthHeader(ServerRequest request) {
        return request.headers().firstHeader(HttpHeaders.AUTHORIZATION);
    }

    /**
     * Generic validation helper using Jakarta Validator.
     * Returns Mono.error() if validation fails.
     */
    private <T> Mono<T> validate(T object) {
        if (validator == null) return Mono.just(object);
        Set<ConstraintViolation<T>> violations = validator.validate(object);
        if (!violations.isEmpty()) {
            String msg = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining(", "));
            log.warn("Validation failed: {}", msg);
            // This exception will be caught by GlobalExceptionHandler
            return Mono.error(new IllegalArgumentException(msg));
        }
        return Mono.just(object);
    }

    // ---------------------------------------------------
    // CRUD Operations for Task
    // ---------------------------------------------------

    public Mono<ServerResponse> createTask(ServerRequest request) {
        String requesterId = getRequesterId(request);
        String requesterRole = getRequesterRole(request);
        String authHeader = getAuthHeader(request); // Get Auth header for propagation
        log.info("Handler: createTask invoked by User: {}, Role: {}", requesterId, requesterRole);

        return request.bodyToMono(TaskRequestDTO.class)
                .flatMap(this::validate) // Validate DTO
                .flatMap(dto -> {
                    // Overwrite createdBy with the authenticated user ID from the token
                    dto.setCreatedBy(requesterId);
                    return taskService.createTask(dto, requesterId, requesterRole, authHeader);
                })
                .flatMap(saved -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(saved));
        // GlobalExceptionHandler handles all errors
    }

    public Mono<ServerResponse> getTaskById(ServerRequest request) {
        String id = request.pathVariable("id");
        String requesterId = getRequesterId(request);
        String requesterRole = getRequesterRole(request);
        String authHeader = getAuthHeader(request); // Get Auth header
        log.info("Handler: getTaskById {} invoked by User: {}, Role: {}", id, requesterId, requesterRole);
        
        return taskService.getTaskById(id, requesterId, requesterRole, authHeader) // Pass header
                .flatMap(task -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(task))
                .onErrorResume(AccessDeniedException.class, ex -> buildErrorResponse(ex, HttpStatus.FORBIDDEN, "Access Denied", request))
                .onErrorResume(TaskNotFoundException.class, ex -> buildErrorResponse(ex, HttpStatus.NOT_FOUND, "Task Not Found", request));
    }

    public Mono<ServerResponse> getAllTasks(ServerRequest request) {
        String requesterId = getRequesterId(request);
        String requesterRole = getRequesterRole(request);
        String authHeader = getAuthHeader(request); // <--- FIX: Extract authHeader
        log.info("Handler: getAllTasks invoked by User: {}, Role: {}", requesterId, requesterRole);

        // We use .onErrorResume to handle the 500->403 error for Flux
        // FIX: Pass authHeader to match the TaskService method signature (3 arguments)
        return taskService.getAllTasks(requesterId, requesterRole, authHeader) 
                .collectList() // Collect to Mono<List>
                .flatMap(tasks -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(tasks)
                )
                .onErrorResume(AccessDeniedException.class, ex -> buildErrorResponse(ex, HttpStatus.FORBIDDEN, "Access Denied", request));
    }

    public Mono<ServerResponse> getTasksByProjectId(ServerRequest request) {
        String projectId = request.pathVariable("projectId");
        String requesterId = getRequesterId(request);
        String requesterRole = getRequesterRole(request);
        String authHeader = getAuthHeader(request); // Get Auth header
        log.info("Handler: getTasksByProjectId {} invoked by User: {}, Role: {}", projectId, requesterId, requesterRole);
        
        // We use .onErrorResume to handle the 500->403 error for Flux
        return taskService.getTasksByProjectId(projectId, requesterId, requesterRole, authHeader) // Pass header
                .collectList()
                .flatMap(tasks -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(tasks)
                )
                .onErrorResume(AccessDeniedException.class, ex -> buildErrorResponse(ex, HttpStatus.FORBIDDEN, "Access Denied", request));
    }

    public Mono<ServerResponse> updateTask(ServerRequest request) {
        String id = request.pathVariable("id");
        String requesterId = getRequesterId(request);
        String requesterRole = getRequesterRole(request);
        String authHeader = getAuthHeader(request); // Get Auth header
        log.info("Handler: updateTask {} invoked by User: {}, Role: {}", id, requesterId, requesterRole);

        return request.bodyToMono(TaskRequestDTO.class)
                .flatMap(this::validate)
                .flatMap(dto -> taskService.updateTask(id, dto, requesterId, requesterRole, authHeader)) // Pass header
                .flatMap(updated -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(updated));
    }

    public Mono<ServerResponse> updateStatus(ServerRequest request) {
        String id = request.pathVariable("id");
        String requesterId = getRequesterId(request);
        String requesterRole = getRequesterRole(request);
        String authHeader = getAuthHeader(request); // Get Auth header
        log.info("Handler: updateStatus {} invoked by User: {}, Role: {}", id, requesterId, requesterRole);

        return request.bodyToMono(new ParameterizedTypeReference<Map<String, String>>() {})
                .flatMap(map -> {
                    String status = map.get("status");
                    return taskService.updateTaskStatus(id, status, requesterId, requesterRole, authHeader); // Pass header
                })
                .flatMap(updated -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(updated));
    }

    public Mono<ServerResponse> deleteTask(ServerRequest request) {
        String id = request.pathVariable("id");
        String requesterId = getRequesterId(request);
        String requesterRole = getRequesterRole(request);
        String authHeader = getAuthHeader(request); // Get Auth header
        log.info("Handler: deleteTask {} invoked by User: {}, Role: {}", id, requesterId, requesterRole);

        return taskService.deleteTask(id, requesterId, requesterRole, authHeader) // Pass header
                .thenReturn(Map.of("message", "The Task " + id + " is deleted."))
                .flatMap(resp -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(resp));
    }
    
    public Mono<ServerResponse> deleteTasksByProjectId(ServerRequest request) {
        String projectId = request.pathVariable("projectId");
        String requesterId = getRequesterId(request);
        String requesterRole = getRequesterRole(request);
        String authHeader = getAuthHeader(request); // Get Auth header
        log.info("Handler: deleteTasksByProjectId {} invoked by User: {}, Role: {}", projectId, requesterId, requesterRole);
        
        return taskService.deleteTasksByProjectId(projectId, requesterId, requesterRole, authHeader) // Pass header
                .then(ServerResponse.noContent().build()); // 204 No Content
    }

    // ---------------------------------------------------
    // Assignee & Tag Management
    // ---------------------------------------------------

    public Mono<ServerResponse> addAssignees(ServerRequest request) {
        String id = request.pathVariable("id");
        String requesterId = getRequesterId(request);
        String requesterRole = getRequesterRole(request);
        String authHeader = getAuthHeader(request); // Get Auth header
        log.info("Handler: addAssignees {} invoked by User: {}, Role: {}", id, requesterId, requesterRole);

        return request.bodyToMono(TaskAssigneesDTO.class)
                .flatMap(dto -> taskService.addAssignees(id, dto, requesterId, requesterRole, authHeader)) // Pass header
                .flatMap(saved -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(saved));
    }

    public Mono<ServerResponse> removeAssignees(ServerRequest request) {
        String id = request.pathVariable("id");
        String requesterId = getRequesterId(request);
        String requesterRole = getRequesterRole(request);
        String authHeader = getAuthHeader(request); // Get Auth header
        log.info("Handler: removeAssignees {} invoked by User: {}, Role: {}", id, requesterId, requesterRole);

        return request.bodyToMono(TaskAssigneesDTO.class)
                .flatMap(dto -> taskService.removeAssignees(id, dto, requesterId, requesterRole, authHeader)) // Pass header
                .flatMap(saved -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(saved));
    }

    public Mono<ServerResponse> addTags(ServerRequest request) {
        String id = request.pathVariable("id");
        String requesterId = getRequesterId(request);
        String requesterRole = getRequesterRole(request);
        log.info("Handler: addTags {} invoked by User: {}, Role: {}", id, requesterId, requesterRole);

        return request.bodyToMono(TaskTagsDTO.class)
                .flatMap(dto -> taskService.addTags(id, dto, requesterId, requesterRole))
                .flatMap(saved -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(saved));
    }

    public Mono<ServerResponse> removeTags(ServerRequest request) {
        String id = request.pathVariable("id");
        String requesterId = getRequesterId(request);
        String requesterRole = getRequesterRole(request);
        log.info("Handler: removeTags {} invoked by User: {}, Role: {}", id, requesterId, requesterRole);

        return request.bodyToMono(TaskTagsDTO.class)
                .flatMap(dto -> taskService.removeTags(id, dto, requesterId, requesterRole))
                .flatMap(saved -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(saved));
    }

    // ---------------------------------------------------
    // Task Event Stream (SSE)
    // ---------------------------------------------------

    public Mono<ServerResponse> taskEventsStream(ServerRequest request) {
        String projectId = request.pathVariable("projectId");
        String requesterId = getRequesterId(request);
        String requesterRole = getRequesterRole(request);
        String authHeader = getAuthHeader(request); // Get Auth header
        log.info("Handler: taskEventsStream {} invoked by User: {}, Role: {}", projectId, requesterId, requesterRole);
        
        // Authorize user by checking if they can get tasks for this project
        return taskService.getTasksByProjectId(projectId, requesterId, requesterRole, authHeader) // Pass header
            .next() // Check if user can get at least one task (or empty)
            .then(ServerResponse.ok() 
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(taskService.taskEventsStream()
                    .filter(ev -> ev.contains(":project:" + projectId)) 
                    .map(ev -> ev), String.class
                )
            )
            .onErrorResume(AccessDeniedException.class, ex -> buildErrorResponse(ex, HttpStatus.FORBIDDEN, "Access Denied", request));
    }

    // --- ADDED HELPER FOR HANDLER-LEVEL ERROR MAPPING ---
    private Mono<ServerResponse> buildErrorResponse(Throwable ex, HttpStatus status, String message, ServerRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                message,
                ex.getMessage()
        );
        log.warn("Handler mapping error [{}]: {} - {} for path {}", status, message, ex.getMessage(), request.path());
        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(errorResponse);
    }
}