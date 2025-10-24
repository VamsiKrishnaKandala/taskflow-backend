package com.taskflowpro.taskservice.handler;

import com.taskflowpro.taskservice.dto.*;
import com.taskflowpro.taskservice.service.TaskService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handler for Task endpoints (functional).
 * Validates incoming payloads (if Validator provided) and delegates to TaskService.
 * Uses DTOs instead of direct Task model and supports reactive validation + SSE.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskHandler {

    private final TaskService taskService;
    private final Validator validator; // Validator can be null in test environments

    /**
     * Generic validation helper using Jakarta Validator.
     * Returns Mono.error() if validation fails.
     */
    private <T> Mono<Void> validate(T object) {
        if (validator == null) return Mono.empty();
        Set<ConstraintViolation<T>> violations = validator.validate(object);
        if (!violations.isEmpty()) {
            String msg = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining(", "));
            log.warn("Validation failed: {}", msg);
            return Mono.error(new IllegalArgumentException(msg));
        }
        return Mono.empty();
    }

    // ---------------------------------------------------
    // CRUD Operations for Task
    // ---------------------------------------------------

    /**
     * Create a new Task.
     */
    public Mono<ServerResponse> createTask(ServerRequest request) {
        return request.bodyToMono(TaskRequestDTO.class)
            .flatMap(dto -> {
                Set<ConstraintViolation<TaskRequestDTO>> violations = validator.validate(dto);
                if (!violations.isEmpty()) {
                    String msg = violations.stream()
                            .map(ConstraintViolation::getMessage)
                            .collect(Collectors.joining(", "));
                    return Mono.error(new IllegalArgumentException(msg));
                }
                return taskService.createTask(dto);
            })
            .flatMap(saved -> ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(saved))
            .onErrorResume(IllegalArgumentException.class, e -> handleError("Validation failed", e))
            .onErrorResume(e -> handleError("Error creating task", e));
    }

    /**
     * Get task by ID.
     */
    public Mono<ServerResponse> getTaskById(ServerRequest request) {
        String id = request.pathVariable("id");
        return taskService.getTaskById(id)
                .flatMap(task -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(task))
                .switchIfEmpty(ServerResponse.notFound().build())
                .onErrorResume(e -> handleError("Error fetching task with ID: " + id, e));
    }

    /**
     * Get all tasks.
     * MODIFIED: Wrapped response builder in Mono.defer() for stable error handling in Flux-to-Mono flows.
     */
    public Mono<ServerResponse> getAllTasks(ServerRequest request) {
        return Mono.defer(() -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(taskService.getAllTasks(), TaskResponseDTO.class))
                .onErrorResume(e -> handleError("Error fetching all tasks", e));
    }

    /**
     * Get tasks by project ID.
     * MODIFIED: Wrapped response builder in Mono.defer() for stable error handling in Flux-to-Mono flows.
     */
    public Mono<ServerResponse> getTasksByProjectId(ServerRequest request) {
        String projectId = request.pathVariable("projectId");
        return Mono.defer(() -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(taskService.getTasksByProjectId(projectId), TaskResponseDTO.class))
                .onErrorResume(e -> handleError("Error fetching tasks for project: " + projectId, e));
    }

    /**
     * Update an existing Task fully.
     */
    public Mono<ServerResponse> updateTask(ServerRequest request) {
        String id = request.pathVariable("id");

        return request.bodyToMono(TaskRequestDTO.class)
            .flatMap(dto -> validate(dto)
                .then(taskService.updateTask(id, dto)
                    .flatMap(updated -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(updated))
                )
            )
            .onErrorResume(e -> ServerResponse.badRequest().bodyValue("Error updating task " + id + ": " + e.getMessage()));
    }
    /**
     * Update Task status.
     * Expected body: { "status": "IN_PROGRESS", "changedBy": "user123" }
     */
    public Mono<ServerResponse> updateStatus(ServerRequest request) {
        String id = request.pathVariable("id");
        return request.bodyToMono(new ParameterizedTypeReference<Map<String, String>>() {})
                .flatMap(map -> {
                    String status = map.get("status");
                    String changedBy = map.getOrDefault("changedBy", null);
                    return taskService.updateTaskStatus(id, status, changedBy);
                })
                .flatMap(updated -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(updated))
                .onErrorResume(e -> handleError("Error updating status for task " + id, e));
    }

    /**
     * Delete a Task by ID.
     * Returns confirmation message in JSON.
     */
    public Mono<ServerResponse> deleteTask(ServerRequest request) {
        String id = request.pathVariable("id");
        return taskService.deleteTask(id)
                .thenReturn(Map.of("message", "The Task " + id + " is deleted."))
                .flatMap(resp -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(resp))
                .onErrorResume(e -> handleError("Error deleting task " + id, e));
    }

    // ---------------------------------------------------
    // Assignee & Tag Management
    // ---------------------------------------------------

    /**
     * Add Assignees to a Task.
     */
    public Mono<ServerResponse> addAssignees(ServerRequest request) {
        String id = request.pathVariable("id");
        return request.bodyToMono(TaskAssigneesDTO.class)
                .flatMap(dto -> taskService.addAssignees(id, dto))
                .flatMap(saved -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(saved))
                .onErrorResume(e -> handleError("Error adding assignees to task " + id, e));
    }

    /**
     * Remove Assignees from a Task.
     */
    public Mono<ServerResponse> removeAssignees(ServerRequest request) {
        String id = request.pathVariable("id");
        return request.bodyToMono(TaskAssigneesDTO.class)
                .flatMap(dto -> taskService.removeAssignees(id, dto))
                .flatMap(saved -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(saved))
                .onErrorResume(e -> handleError("Error removing assignees from task " + id, e));
    }

    /**
     * Add Tags to a Task.
     */
    public Mono<ServerResponse> addTags(ServerRequest request) {
        String id = request.pathVariable("id");
        return request.bodyToMono(TaskTagsDTO.class)
                .flatMap(dto -> taskService.addTags(id, dto))
                .flatMap(saved -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(saved))
                .onErrorResume(e -> handleError("Error adding tags to task " + id, e));
    }

    /**
     * Remove Tags from a Task.
     */
    public Mono<ServerResponse> removeTags(ServerRequest request) {
        String id = request.pathVariable("id");
        return request.bodyToMono(TaskTagsDTO.class)
                .flatMap(dto -> taskService.removeTags(id, dto))
                .flatMap(saved -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(saved))
                .onErrorResume(e -> handleError("Error removing tags from task " + id, e));
    }

    // ---------------------------------------------------
    // Task Event Stream (SSE)
    // ---------------------------------------------------

    /**
     * Stream realtime task events via Server-Sent Events (SSE).
     * Filters by projectId.
     */
    public Mono<ServerResponse> taskEventsStream(ServerRequest request) {
        String projectId = request.pathVariable("projectId");
        return ServerResponse.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(taskService.taskEventsStream()
                        .filter(ev -> ev.contains("project:" + projectId))
                        .map(ev -> ev), String.class);
    }

    // ---------------------------------------------------
    // Error Handling Helper
    // ---------------------------------------------------

    private Mono<ServerResponse> handleError(String message, Throwable e) {
        log.error("{}: {}", message, e.getMessage(), e);
        return ServerResponse.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "error", message,
                        "details", e.getMessage()
                ));
    }
}