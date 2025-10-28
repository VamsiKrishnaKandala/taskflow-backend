package com.taskflowpro.projectservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders; // Import
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class TaskServiceClientWebClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${task.service.url}") // http://localhost:8080
    private String taskServiceUrl;

    public Mono<Void> deleteTasksByProjectId(String projectId, String requesterId, String requesterRole, String authorizationHeader) {
        log.debug("PROJECT-SERVICE: Deleting tasks for project {} on behalf of user {}", projectId, requesterId);
        
        // Construct full path from gateway root
        String targetUri = taskServiceUrl + "/api/v1/tasks/project/" + projectId;

        return webClientBuilder.build()
                .delete()
                .uri(targetUri)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader) // <-- FORWARD THE TOKEN
                .header("X-User-Id", requesterId)
                .header("X-User-Role", requesterRole)
                .retrieve()
                .onStatus(status -> status.isError(),
                        clientResponse -> {
                             log.warn("Failed to delete tasks for project {}. Status: {}", projectId, clientResponse.statusCode());
                             return clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(
                                    new RuntimeException("Task service failed: " + errorBody)
                                ));
                        })
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("Successfully deleted tasks for project {}", projectId))
                .doOnError(ex -> log.error("Failed to delete tasks for project {}: {}", projectId, ex.getMessage()));
    }
}