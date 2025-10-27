package com.taskflowpro.taskservice.router;

import com.taskflowpro.taskservice.exception.GlobalExceptionHandler;
import com.taskflowpro.taskservice.handler.TaskHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * Functional routing for Task Service.
 * Includes CRUD, assignee/tag endpoints and an SSE endpoint for task events.
 */
@Configuration
public class TaskRouter {

    @Bean
    public RouterFunction<ServerResponse> taskRoutes(TaskHandler handler, GlobalExceptionHandler errorHandler) {
        return RouterFunctions.route()
                .POST("/tasks", handler::createTask)
                .GET("/tasks/{id}", handler::getTaskById)
                .GET("/tasks", handler::getAllTasks)
                .GET("/tasks/project/{projectId}", handler::getTasksByProjectId)
                .PUT("/tasks/{id}", handler::updateTask)
                .PUT("/tasks/{id}/status", handler::updateStatus)
                .DELETE("/tasks/{id}", handler::deleteTask)

                .DELETE("/tasks/project/{projectId}", handler::deleteTasksByProjectId)
                
                .POST("/tasks/{id}/assignees", handler::addAssignees)
                .DELETE("/tasks/{id}/assignees", handler::removeAssignees)

                .POST("/tasks/{id}/tags", handler::addTags)
                .DELETE("/tasks/{id}/tags", handler::removeTags)

                // SSE stream for project
                .GET("/tasks/stream/{projectId}", handler::taskEventsStream)

                // default exception mappings
                .onError(Throwable.class, (ex, req) -> errorHandler.handleGenericError(req, ex))
                .build();
    }
}

