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
public class ProjectServiceClientWebClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${project.service.url}") // http://localhost:8080
    private String projectServiceUrl;

    // --- MODIFIED: Accepts and forwards auth headers ---
    public Mono<Map> getProjectById(String projectId, String authorizationHeader, String requesterId, String requesterRole) {
        if (projectId == null) return Mono.empty();
        
        String targetUri = projectServiceUrl + "/projects/" + projectId;
        
        return webClientBuilder.build()
                .get()
                .uri(targetUri)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader) // Forward token
                .header("X-User-Id", requesterId)
                .header("X-User-Role", requesterRole)
                .retrieve()
                .bodyToMono(Map.class)
                .doOnNext(map -> log.debug("Enriched project {}: {}", projectId, map))
                .onErrorResume(ex -> {
                    log.warn("Failed to fetch project {}: {}", projectId, ex.getMessage());
                    return Mono.empty();
                });
    }
}