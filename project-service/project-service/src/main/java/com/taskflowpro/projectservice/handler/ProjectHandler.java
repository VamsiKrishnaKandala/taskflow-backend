package com.taskflowpro.projectservice.handler;

import com.taskflowpro.projectservice.model.Project;
import com.taskflowpro.projectservice.service.ProjectService;
import jakarta.validation.Validator;
import jakarta.validation.ConstraintViolation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles incoming HTTP requests for Project operations.
 * Validation errors and exceptions are propagated to GlobalExceptionHandler.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectHandler {
	
	private final ProjectService projectService;
	private final Validator validator;
	
	/**
     * Validates incoming Project data.
     * @param object Project object to validate
     * @param <T> Type of object
     * @return Mono<Void> that emits error if validation fails
     */
	private <T> Mono<Void> validate(T object) {
		Set<ConstraintViolation<T>> violations = validator.validate(object);
		if(!violations.isEmpty()) {
			String errorMessages = violations.stream()
					.map(ConstraintViolation::getMessage)
					.collect(Collectors.joining(", "));
			log.warn("Validation failed: {}", errorMessages);
			return Mono.error(new IllegalArgumentException(errorMessages));
		}
		return Mono.empty();
	}
	
	/**
     * Creates a new project after validation.
     */
	public Mono<ServerResponse> createProject(ServerRequest request){
		return request.bodyToMono(Project.class)
				.flatMap(project -> validate(project)
						.then(projectService.createProject(project)))
				.flatMap(saved -> {
					log.info("Project created successfully: {}", saved.getId());
					return ServerResponse.ok()
							.contentType(MediaType.APPLICATION_JSON)
							.bodyValue(saved);
				});	
	}
	
	 /**
     * Fetches project by ID.
     */
	public Mono<ServerResponse> getProjectById(ServerRequest request){
		String id = request.pathVariable("id");
		log.info("Fetching project with ID: {}", id);
		return projectService.getProjectById(id)
				.flatMap(project -> ServerResponse.ok()
						.contentType(MediaType.APPLICATION_JSON)
						.bodyValue(project));
	}
	
	/**
     * Fetches all projects.
     */
	public Mono<ServerResponse> getAllProjects(ServerRequest request){
		log.info("Fetching all projects");
		return ServerResponse.ok()
				.contentType(MediaType.APPLICATION_JSON)
				.body(projectService.getAllProjects(), Project.class);
	}
	
	/**
     * Updates project by ID with validation.
     */
	public Mono<ServerResponse> updateProject(ServerRequest request){
		String id = request.pathVariable("id");
		return request.bodyToMono(Project.class)
				.flatMap(project -> validate(project)
						.then(projectService.updateProject(id, project)))
				.flatMap(updated -> {
					log.info("Project updated successfully: {}", updated.getId());
					return ServerResponse.ok()
							.contentType(MediaType.APPLICATION_JSON)
							.bodyValue(updated);
				});
	}
	
}
