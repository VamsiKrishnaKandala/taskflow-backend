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
public class UserServiceClientWebClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${user.service.url}") // http://localhost:8080
    private String userServiceUrl;

    // --- MODIFIED: Accepts and forwards auth headers ---
    public Mono<Map> getUserById(String userId, String authorizationHeader, String requesterId, String requesterRole) {
        if (userId == null) return Mono.empty();
        
        String targetUri = userServiceUrl + "/users/" + userId;
        
        return webClientBuilder.build()
                .get()
                .uri(targetUri)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader) // Forward token
                .header("X-User-Id", requesterId)
                .header("X-User-Role", requesterRole)
                .retrieve()
                .bodyToMono(Map.class)
                .doOnNext(map -> log.debug("Enriched user {}: {}", userId, map))
                .onErrorResume(ex -> {
                    log.warn("Failed to fetch user {}: {}", userId, ex.getMessage());
                    return Mono.empty();
                });
    }
}