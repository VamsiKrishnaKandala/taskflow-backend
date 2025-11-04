package com.taskflowpro.notificationservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders; // Import
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class TaskServiceClientWebClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${task.service.url}") // http://localhost:8080
    private String taskServiceUrl;

    // --- MODIFIED: Accepts and forwards auth headers ---
    public Mono<Map> getTaskById(String taskId, String authorizationHeader, String requesterId, String requesterRole) {
        if (taskId == null) return Mono.empty();
        
        String targetUri = taskServiceUrl + "/tasks/" + taskId;
        
        return webClientBuilder.build()
                .get()
                .uri(targetUri)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader) // Forward token
                .header("X-User-Id", requesterId)
                .header("X-User-Role", requesterRole)
                .retrieve()
                .bodyToMono(Map.class)
                .doOnNext(body -> log.debug("Enriched task {} with body {}", taskId, body))
                .onErrorResume(ex -> {
                    log.warn("Failed to get task {}: {}", taskId, ex.getMessage());
                    return Mono.empty();
                });
    }
}