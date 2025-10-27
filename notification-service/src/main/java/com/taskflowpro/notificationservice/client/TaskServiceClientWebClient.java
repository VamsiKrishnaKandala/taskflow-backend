package com.taskflowpro.notificationservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * WebClient-based client to query Task Service for enrichment.
 * Returns Mono<Map> so we don't need strict DTO coupling; callers can extract fields they need.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskServiceClientWebClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${task.service.url:http://localhost:8082/tasks}")
    private String taskServiceUrl;

    /**
     * Fetches a task's details from Task Service by id.
     * Returns Mono.empty() if not found or error (non-blocking; best-effort).
     */
    public Mono<Map> getTaskById(String taskId) {
        if (taskId == null) return Mono.empty();
        return webClientBuilder.build()
                .get()
                .uri(taskServiceUrl + "/{id}", taskId)
                .retrieve()
                .bodyToMono(Map.class)
                .doOnNext(body -> log.debug("Enriched task {} with body {}", taskId, body))
                .onErrorResume(ex -> {
                    log.warn("Failed to get task {}: {}", taskId, ex.getMessage());
                    return Mono.empty();
                });
    }
}
