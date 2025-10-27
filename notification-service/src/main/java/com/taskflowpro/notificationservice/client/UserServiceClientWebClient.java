package com.taskflowpro.notificationservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * WebClient to fetch minimal user info for enrichment.
 * Returns Mono.empty() if not found.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserServiceClientWebClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${user.service.url:http://localhost:8081/users}")
    private String userServiceUrl;

    public Mono<Map> getUserById(String userId) {
        if (userId == null) return Mono.empty();
        return webClientBuilder.build()
                .get()
                .uri(userServiceUrl + "/{id}", userId)
                .retrieve()
                .bodyToMono(Map.class)
                .doOnNext(map -> log.debug("Enriched user {}: {}", userId, map))
                .onErrorResume(ex -> {
                    log.warn("Failed to fetch user {}: {}", userId, ex.getMessage());
                    return Mono.empty();
                });
    }
}
