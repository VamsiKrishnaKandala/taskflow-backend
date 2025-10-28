package com.taskflowpro.projectservice.router;

// Remove the import for GlobalExceptionHandler
// import com.taskflowpro.projectservice.exception.GlobalExceptionHandler;
import com.taskflowpro.projectservice.handler.ProjectHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Defines functional routes for Project Service.
 * Relies on GlobalExceptionHandler (@RestControllerAdvice) for automatic error handling.
 */
@Configuration
public class ProjectRouter {

    @Bean
    // Remove GlobalExceptionHandler from parameters
    public RouterFunction<ServerResponse> projectRoutes(ProjectHandler handler) {
        return RouterFunctions.route()
                // -------------------- CRUD Endpoints --------------------
                .POST("/projects", handler::createProject)       // Create project
                .GET("/projects/{id}", handler::getProjectById)   // Get project by ID
                .GET("/projects", handler::getAllProjects)        // Get all projects
                .PUT("/projects/{id}", handler::updateProject)    // Update project by ID
                .DELETE("/projects/{id}", handler::deleteProject) // Delete project by ID

                // -------------------- Team Management Endpoints --------------------
                .POST("/projects/{id}/members", handler::addMembers)     // Add members
                .DELETE("/projects/{id}/members", handler::removeMembers) // Remove members

                // -------------------- Tag Management Endpoints --------------------
                .POST("/projects/{id}/tags", handler::addTags)        // Add tags
                .DELETE("/projects/{id}/tags", handler::removeTags)   // Remove tags
                .build();
    }
}