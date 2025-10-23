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
import org.springframework.core.ParameterizedTypeReference;

import com.taskflowpro.projectservice.dto.ProjectRequestDTO;
import com.taskflowpro.projectservice.dto.ProjectResponseDTO;
import com.taskflowpro.projectservice.dto.ProjectMembersDTO;
import com.taskflowpro.projectservice.dto.ProjectTagsDTO;
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
 * Works with DTOs (ProjectRequestDTO, ProjectResponseDTO, etc.) and reactive types (Mono/Flux)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectHandler {

    private final ProjectService projectService; // Service layer for CRUD, member, tag operations
    private final Validator validator;           // Jakarta Bean Validator for request validation

    /**
     * Validates a DTO object using Jakarta Bean Validation.
     * Returns a Mono<Void> that emits an error if validation fails.
     */
    private <T> Mono<Void> validate(T object) {
        if (validator == null) return Mono.empty(); // skip validation if validator is null
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

    public Mono<ServerResponse> createProject(ServerRequest request) {
        return request.bodyToMono(ProjectRequestDTO.class)
                .flatMap(dto -> validate(dto)
                        .then(projectService.createProject(dto)))
                .flatMap(saved -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(saved));
    }

    public Mono<ServerResponse> getProjectById(ServerRequest request) {
        String id = request.pathVariable("id");
        log.info("Fetching project with ID: {}", id);
        return projectService.getProjectById(id)
                .flatMap(project -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(project));
    }

    public Mono<ServerResponse> getAllProjects(ServerRequest request) {
        log.info("Fetching all projects");
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(projectService.getAllProjects(), ProjectResponseDTO.class);
    }

    public Mono<ServerResponse> updateProject(ServerRequest request) {
        String id = request.pathVariable("id");
        return request.bodyToMono(ProjectRequestDTO.class)
                .flatMap(dto -> validate(dto)
                        .then(projectService.updateProject(id, dto)))
                .flatMap(updated -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(updated));
    }

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

    public Mono<ServerResponse> addMembers(ServerRequest request) {
        String projectId = request.pathVariable("id");
        
        // Reads the raw JSON array ["User1", "User2"] into List<String>
        return request.bodyToMono(new ParameterizedTypeReference<List<String>>() {})
                // Maps the List<String> to your ProjectMembersDTO
                .map(memberList -> new ProjectMembersDTO(memberList))
                .flatMap(dto -> projectService.addMembers(projectId, dto))
                .flatMap(updated -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(updated));
    }

    public Mono<ServerResponse> removeMembers(ServerRequest request) {
        String projectId = request.pathVariable("id");
        
        // Reads the raw JSON array ["User1", "User2"] into List<String>
        return request.bodyToMono(new ParameterizedTypeReference<List<String>>() {})
                // Maps the List<String> to your ProjectMembersDTO
                .map(memberList -> new ProjectMembersDTO(memberList))
                .flatMap(dto -> projectService.removeMembers(projectId, dto))
                .flatMap(updated -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(updated));
    }
    // -------------------- TAG MANAGEMENT --------------------

    public Mono<ServerResponse> addTags(ServerRequest request) {
        String projectId = request.pathVariable("id");
        
        // Reads the raw JSON array ["backend", "urgent"] into List<String>
        return request.bodyToMono(new ParameterizedTypeReference<List<String>>() {})
                // Maps the List<String> to your ProjectTagsDTO
                .map(tagList -> new ProjectTagsDTO(tagList))
                .flatMap(dto -> projectService.addTags(projectId, dto))
                .flatMap(updated -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(updated));
    }

    public Mono<ServerResponse> removeTags(ServerRequest request) {
        String projectId = request.pathVariable("id");
        
        // Reads the raw JSON array ["backend", "urgent"] into List<String>
        return request.bodyToMono(new ParameterizedTypeReference<List<String>>() {})
                // Maps the List<String> to your ProjectTagsDTO
                .map(tagList -> new ProjectTagsDTO(tagList))
                .flatMap(dto -> projectService.removeTags(projectId, dto))
                .flatMap(updated -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(updated));
    }
}
