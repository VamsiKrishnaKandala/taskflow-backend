package com.taskflowpro.projectservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class TaskServiceClientWebClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${task.service.url}")
    private String taskServiceUrl;

    /**
     * Delete all tasks for a project in Task Service
     */
    public Mono<Void> deleteTasksByProjectId(String projectId) {
        return webClientBuilder.build()
                .delete()
                .uri(taskServiceUrl + "/project/{projectId}", projectId)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("Deleted tasks for project {}", projectId))
                .doOnError(ex -> log.error("Failed to delete tasks for project {}: {}", projectId, ex.getMessage()));
    }
}
