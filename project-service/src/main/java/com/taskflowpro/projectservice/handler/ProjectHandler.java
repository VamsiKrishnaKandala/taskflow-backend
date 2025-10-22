package com.taskflowpro.projectservice.handler;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.taskflowpro.projectservice.model.Project;
import com.taskflowpro.projectservice.service.ProjectService;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Handles HTTP requests for Project operations:
 * - CRUD operations
 * - Member management (add/remove)
 * - Tag management (add/remove)
 * 
 * This handler works with reactive types (Mono/Flux) and delegates business logic to ProjectService.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectHandler {

    private final ProjectService projectService; // Service layer for CRUD, member, tag operations
    private final Validator validator;           // Optional Jakarta Bean Validator for request validation

    /**
     * Validates an object using Jakarta Bean Validation.
     * Returns a Mono<Void> that emits an error if validation fails.
     * @param object object to validate (e.g., Project)
     * @param <T> type of object
     */
    private <T> Mono<Void> validate(T object) {
        if (validator == null) return Mono.empty(); // skip validation if validator is null (useful in tests)
        Set<ConstraintViolation<T>> violations = validator.validate(object);
        if (!violations.isEmpty()) {
            String errorMessages = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining(", "));
            log.warn("Validation failed: {}", errorMessages);
            return Mono.error(new IllegalArgumentException(errorMessages));
        }
        return Mono.empty();
    }

    // -------------------- CRUD OPERATIONS --------------------

    /**
     * Creates a new project.
     * - Validates incoming Project object.
     * - Calls ProjectService to save.
     * - Returns saved project in response.
     */
    public Mono<ServerResponse> createProject(ServerRequest request) {
        return request.bodyToMono(Project.class)   // parse JSON body into Project
                .flatMap(project -> validate(project)
                        .then(projectService.createProject(project))) // delegate creation
                .flatMap(saved -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(saved)); // return saved project
    }

    /**
     * Retrieves a project by ID.
     * - Calls ProjectService to fetch project.
     * - Returns project if found; else exception is propagated.
     */
    public Mono<ServerResponse> getProjectById(ServerRequest request) {
        String id = request.pathVariable("id");
        log.info("Fetching project with ID: {}", id);
        return projectService.getProjectById(id)
                .flatMap(project -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(project));
    }

    /**
     * Retrieves all projects.
     * - Returns Flux<Project> from service.
     * - Spring WebFlux automatically serializes the list to JSON.
     */
    public Mono<ServerResponse> getAllProjects(ServerRequest request) {
        log.info("Fetching all projects");
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(projectService.getAllProjects(), Project.class);
    }

    /**
     * Updates a project by ID.
     * - Validates incoming Project object.
     * - Calls service to update.
     * - Returns updated project.
     */
    public Mono<ServerResponse> updateProject(ServerRequest request) {
        String id = request.pathVariable("id");
        return request.bodyToMono(Project.class)
                .flatMap(project -> validate(project)
                        .then(projectService.updateProject(id, project)))
                .flatMap(updated -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(updated));
    }

    /**
     * Deletes a project by ID.
     * - Calls service to delete project.
     * - Returns simple JSON message confirming deletion.
     */
    public Mono<ServerResponse> deleteProject(ServerRequest request) {
        String id = request.pathVariable("id");
        return projectService.deleteProject(id)
                .then(Mono.fromSupplier(() ->
                        Collections.singletonMap("message", "The Project " + id + " is deleted.")
                ))
                .flatMap(response -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(response));
    }

    // -------------------- MEMBER MANAGEMENT --------------------

    /**
     * Adds members to a project.
     * - Expects JSON array of member IDs in request body.
     * - Delegates addition to ProjectService.
     */
    public Mono<ServerResponse> addMembers(ServerRequest request) {
        String projectId = request.pathVariable("id");
        return request.bodyToMono(new ParameterizedTypeReference<List<String>>() {})
                .flatMap(memberIds -> projectService.addMembers(projectId, memberIds))
                .flatMap(updated -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(updated));
    }

    /**
     * Removes members from a project.
     * - Expects JSON array of member IDs in request body.
     */
    public Mono<ServerResponse> removeMembers(ServerRequest request) {
        String projectId = request.pathVariable("id");
        return request.bodyToMono(new ParameterizedTypeReference<List<String>>() {})
                .flatMap(memberIds -> projectService.removeMembers(projectId, memberIds))
                .flatMap(updated -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(updated));
    }

    // -------------------- TAG MANAGEMENT --------------------

    /**
     * Adds tags to a project.
     * - Expects JSON array of tags in request body.
     * - Delegates addition to ProjectService.
     */
    public Mono<ServerResponse> addTags(ServerRequest request) {
        String projectId = request.pathVariable("id");
        return request.bodyToMono(new ParameterizedTypeReference<List<String>>() {})
                .flatMap(tags -> projectService.addTags(projectId, tags))
                .flatMap(updated -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(updated));
    }

    /**
     * Removes tags from a project.
     * - Expects JSON array of tags in request body.
     */
    public Mono<ServerResponse> removeTags(ServerRequest request) {
        String projectId = request.pathVariable("id");
        return request.bodyToMono(new ParameterizedTypeReference<List<String>>() {})
                .flatMap(tags -> projectService.removeTags(projectId, tags))
                .flatMap(updated -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(updated));
    }
}