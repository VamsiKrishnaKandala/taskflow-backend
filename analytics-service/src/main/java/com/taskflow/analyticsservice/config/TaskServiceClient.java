package com.taskflow.analyticsservice.config;

import com.taskflow.analyticsservice.dto.TaskDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

/**
 * WebClient to fetch data from the Task Service via the API Gateway.
 */
@Component
@Slf4j
public class TaskServiceClient {

    private final WebClient webClient;
    @Value("${task.service.url}")
    private String taskServiceUrl; // http://localhost:8080

    public TaskServiceClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * Fetches all tasks (for Admin)
     */
    public Flux<TaskDTO> getAllTasks(String authorizationHeader, String requesterId, String requesterRole) {
        String targetUri = taskServiceUrl + "/tasks";
        log.debug("ANALYTICS-SERVICE: Calling GET {}", targetUri);

        return webClient.get()
                .uri(targetUri)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .header("X-User-Id", requesterId)
                .header("X-User-Role", requesterRole)
                .retrieve()
                .bodyToFlux(TaskDTO.class)
                .doOnError(e -> log.error("Failed to fetch all tasks: {}", e.getMessage()))
                .onErrorResume(e -> Flux.empty());
    }
    
    /**
     * Fetches all tasks for a specific project
     */
    public Flux<TaskDTO> getTasksByProjectId(String projectId, String authorizationHeader, String requesterId, String requesterRole) {
        String targetUri = taskServiceUrl + "/tasks/project/" + projectId;
        log.debug("ANALYTICS-SERVICE: Calling GET {}", targetUri);

        return webClient.get()
                .uri(targetUri)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .header("X-User-Id", requesterId)
                .header("X-User-Role", requesterRole)
                .retrieve()
                .bodyToFlux(TaskDTO.class)
                .doOnError(e -> log.error("Failed to fetch tasks for project {}: {}", projectId, e.getMessage()))
                .onErrorResume(e -> Flux.empty());
    }
}