package com.taskflowpro.notificationservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Optional client to fetch project details.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectServiceClientWebClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${project.service.url:http://localhost:8083/projects}")
    private String projectServiceUrl;

    public Mono<Map> getProjectById(String projectId) {
        if (projectId == null) return Mono.empty();
        return webClientBuilder.build()
                .get()
                .uri(projectServiceUrl + "/{id}", projectId)
                .retrieve()
                .bodyToMono(Map.class)
                .doOnNext(map -> log.debug("Enriched project {}: {}", projectId, map))
                .onErrorResume(ex -> {
                    log.warn("Failed to fetch project {}: {}", projectId, ex.getMessage());
                    return Mono.empty();
                });
    }
}
