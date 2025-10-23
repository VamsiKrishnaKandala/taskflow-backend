package com.taskflowpro.projectservice.router;

import com.taskflowpro.projectservice.exception.GlobalExceptionHandler;
import com.taskflowpro.projectservice.exception.InvalidProjectDataException;
import com.taskflowpro.projectservice.exception.ProjectNotFoundException;
import com.taskflowpro.projectservice.handler.ProjectHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Defines functional routes for Project Service using reactive WebFlux.
 * All handler methods now work with DTOs (ProjectRequestDTO, ProjectResponseDTO, etc.).
 */
@Configuration
public class ProjectRouter {

    @Bean
    public RouterFunction<ServerResponse> projectRoutes(ProjectHandler handler, GlobalExceptionHandler errorHandler) {
        return RouterFunctions.route()
                // -------------------- CRUD Endpoints --------------------
                .POST("/projects", handler::createProject)          // Create project (expects ProjectRequestDTO)
                .GET("/projects/{id}", handler::getProjectById)    // Get project by ID (returns ProjectResponseDTO)
                .GET("/projects", handler::getAllProjects)         // Get all projects (returns Flux<ProjectResponseDTO>)
                .PUT("/projects/{id}", handler::updateProject)     // Update project by ID (expects ProjectRequestDTO)
                .DELETE("/projects/{id}", handler::deleteProject)  // Delete project by ID

                // -------------------- Team Management Endpoints --------------------
                .POST("/projects/{id}/members", handler::addMembers)    // Add members (expects ProjectMembersDTO)
                .DELETE("/projects/{id}/members", handler::removeMembers) // Remove members (expects ProjectMembersDTO)

                // -------------------- Tag Management Endpoints --------------------
                .POST("/projects/{id}/tags", handler::addTags)        // Add tags (expects ProjectTagsDTO)
                .DELETE("/projects/{id}/tags", handler::removeTags)   // Remove tags (expects ProjectTagsDTO)

                // -------------------- Exception Mappings --------------------
                .onError(ProjectNotFoundException.class,
                        (ex, request) -> errorHandler.handleProjectNotFound(request, (ProjectNotFoundException) ex))
                .onError(InvalidProjectDataException.class,
                        (ex, request) -> errorHandler.handleInvalidProjectData(request, (InvalidProjectDataException) ex))
                .onError(IllegalArgumentException.class,
                        (ex, request) -> errorHandler.handleValidationError(request, (IllegalArgumentException) ex))
                .onError(Throwable.class,
                        (ex, request) -> errorHandler.handleGenericError(request, ex))
                .build();
    }
}