package com.taskflowpro.projectservice.handler;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.taskflowpro.projectservice.dto.ProjectRequestDTO;
import com.taskflowpro.projectservice.dto.ProjectResponseDTO;
import com.taskflowpro.projectservice.dto.ProjectMembersDTO;
import com.taskflowpro.projectservice.dto.ProjectTagsDTO;
import com.taskflowpro.projectservice.exception.AccessDeniedException;
import com.taskflowpro.projectservice.exception.ErrorResponse;
import com.taskflowpro.projectservice.exception.ProjectNotFoundException;
import com.taskflowpro.projectservice.service.ProjectService;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectHandler {

    private final ProjectService projectService;
    private final Validator validator;

    // --- Authorization Header Helpers ---
    private String getRequesterId(ServerRequest request) {
        return request.headers().firstHeader("X-User-Id");
    }
    private String getRequesterRole(ServerRequest request) {
        return request.headers().firstHeader("X-User-Role");
    }
    private String getAuthHeader(ServerRequest request) {
        return request.headers().firstHeader(HttpHeaders.AUTHORIZATION);
    }
    // ---

    private <T> Mono<T> validate(T object) { // Changed to return Mono<T>
        if (validator == null) return Mono.just(object);
        Set<ConstraintViolation<T>> violations = validator.validate(object);
        if (!violations.isEmpty()) {
            String errorMessages = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining(", "));
            log.warn("Validation failed: {}", errorMessages);
            return Mono.error(new IllegalArgumentException(errorMessages));
        }
        return Mono.just(object); // Pass the object through
    }
    
    // --- Helper for handler-level error responses ---
    private Mono<ServerResponse> buildErrorResponse(HttpStatus status, String error, String message, ServerRequest request) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(error)
                .message(message)
                .build();
        log.warn("Handler mapping error [{}]: {} - {} for path {}", status, error, message, request.path());
        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(errorResponse);
    }

    // -------------------- CRUD OPERATIONS --------------------

    public Mono<ServerResponse> createProject(ServerRequest request) {
        String requesterId = getRequesterId(request);
        String requesterRole = getRequesterRole(request);
        String authHeader = getAuthHeader(request);
        log.info("Handler: createProject invoked by User: {}, Role: {}", requesterId, requesterRole);

        return request.bodyToMono(ProjectRequestDTO.class)
                .flatMap(this::validate) // Validate DTO
                .flatMap(dto -> projectService.createProject(dto, requesterId, requesterRole, authHeader)) // Pass headers
                .flatMap(saved -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(saved));
    }

    public Mono<ServerResponse> getProjectById(ServerRequest request) {
        String id = request.pathVariable("id");
        String requesterId = getRequesterId(request);
        String requesterRole = getRequesterRole(request);
        String authHeader = getAuthHeader(request);

        log.info("Handler: getProjectById {} invoked by User: {}, Role: {}", id, requesterId, requesterRole);
        return projectService.getProjectById(id, requesterId, requesterRole, authHeader) // Pass headers
                .flatMap(project -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(project))
                // Add specific error handling for this Mono endpoint
                .onErrorResume(AccessDeniedException.class, ex -> buildErrorResponse(HttpStatus.FORBIDDEN, "Access Denied", ex.getMessage(), request))
                .onErrorResume(ProjectNotFoundException.class, ex -> buildErrorResponse(HttpStatus.NOT_FOUND, "Project Not Found", ex.getMessage(), request));
    }

    public Mono<ServerResponse> getAllProjects(ServerRequest request) {
        String requesterId = getRequesterId(request);
        String requesterRole = getRequesterRole(request);
        String authHeader = getAuthHeader(request);
        log.info("Handler: getAllProjects invoked by User: {}, Role: {}", requesterId, requesterRole);

        return projectService.getAllProjects(requesterId, requesterRole, authHeader) // Pass headers
                .collectList() // Collect Flux to Mono<List>
                .flatMap(projects -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(projects)
                )
                // Handle errors from the Flux stream
                .onErrorResume(AccessDeniedException.class, ex -> buildErrorResponse(HttpStatus.FORBIDDEN, "Access Denied", ex.getMessage(), request));
    }

    public Mono<ServerResponse> updateProject(ServerRequest request) {
        String id = request.pathVariable("id");
        String requesterId = getRequesterId(request);
        String requesterRole = getRequesterRole(request);
        String authHeader = getAuthHeader(request);
        log.info("Handler: updateProject {} invoked by User: {}, Role: {}", id, requesterId, requesterRole);

        return request.bodyToMono(ProjectRequestDTO.class)
                .flatMap(this::validate)
                .flatMap(dto -> projectService.updateProject(id, dto, requesterId, requesterRole, authHeader)) // Pass headers
                .flatMap(updated -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(updated));
    }

    public Mono<ServerResponse> deleteProject(ServerRequest request) {
        String id = request.pathVariable("id");
        String requesterId = getRequesterId(request);
        String requesterRole = getRequesterRole(request);
        String authHeader = getAuthHeader(request);
        log.info("Handler: deleteProject {} invoked by User: {}, Role: {}", id, requesterId, requesterRole);

        return projectService.deleteProject(id, requesterId, requesterRole, authHeader) // Pass headers
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
        String requesterId = getRequesterId(request);
        String requesterRole = getRequesterRole(request);
        String authHeader = getAuthHeader(request);
        log.info("Handler: addMembers to {} invoked by User: {}, Role: {}", projectId, requesterId, requesterRole);

        return request.bodyToMono(new ParameterizedTypeReference<List<String>>() {})
                .map(ProjectMembersDTO::new)
                .flatMap(dto -> projectService.addMembers(projectId, dto, requesterId, requesterRole, authHeader)) // Pass headers
                .flatMap(updated -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(updated));
    }

    public Mono<ServerResponse> removeMembers(ServerRequest request) {
        String projectId = request.pathVariable("id");
        String requesterId = getRequesterId(request);
        String requesterRole = getRequesterRole(request);
        String authHeader = getAuthHeader(request);
        log.info("Handler: removeMembers from {} invoked by User: {}, Role: {}", projectId, requesterId, requesterRole);

        return request.bodyToMono(new ParameterizedTypeReference<List<String>>() {})
                .map(ProjectMembersDTO::new)
                .flatMap(dto -> projectService.removeMembers(projectId, dto, requesterId, requesterRole, authHeader)) // Pass headers
                .flatMap(updated -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(updated));
    }
    // -------------------- TAG MANAGEMENT --------------------

    public Mono<ServerResponse> addTags(ServerRequest request) {
        String projectId = request.pathVariable("id");
        String requesterId = getRequesterId(request);
        String requesterRole = getRequesterRole(request);
        String authHeader = getAuthHeader(request);
        log.info("Handler: addTags to {} invoked by User: {}, Role: {}", projectId, requesterId, requesterRole);

        return request.bodyToMono(new ParameterizedTypeReference<List<String>>() {})
                .map(ProjectTagsDTO::new)
                .flatMap(dto -> projectService.addTags(projectId, dto, requesterId, requesterRole, authHeader)) // Pass headers
                .flatMap(updated -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(updated));
    }

    public Mono<ServerResponse> removeTags(ServerRequest request) {
        String projectId = request.pathVariable("id");
        String requesterId = getRequesterId(request);
        String requesterRole = getRequesterRole(request);
        String authHeader = getAuthHeader(request);
        log.info("Handler: removeTags from {} invoked by User: {}, Role: {}", projectId, requesterId, requesterRole);
        
        return request.bodyToMono(new ParameterizedTypeReference<List<String>>() {})
                .map(ProjectTagsDTO::new)
                .flatMap(dto -> projectService.removeTags(projectId, dto, requesterId, requesterRole, authHeader)) // Pass headers
                .flatMap(updated -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(updated));
    }
}