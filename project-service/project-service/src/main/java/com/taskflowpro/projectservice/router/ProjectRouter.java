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
 * Defines functional routes for Project Service, with integrated exception handling.
 */
@Configuration
public class ProjectRouter {

    @Bean
    public RouterFunction<ServerResponse> projectRoutes(ProjectHandler handler, GlobalExceptionHandler errorHandler) {
        return RouterFunctions.route()
                .POST("/projects", handler::createProject)
                .GET("/projects/{id}", handler::getProjectById)
                .GET("/projects", handler::getAllProjects)
                .PUT("/projects/{id}", handler::updateProject)

                // Exception Mappings
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