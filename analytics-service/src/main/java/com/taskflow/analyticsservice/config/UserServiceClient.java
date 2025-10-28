package com.taskflow.analyticsservice.config;

import com.taskflow.analyticsservice.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class UserServiceClient {

    private final WebClient webClient;
    @Value("${user.service.url}")
    private String userServiceUrl; // http://localhost:8080

    public UserServiceClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * Fetches a single user by ID.
     */
    public Mono<UserDTO> getUserById(String userId, String authorizationHeader, String requesterId, String requesterRole) {
        String targetUri = userServiceUrl + "/users/" + userId;
        log.debug("ANALYTICS-SERVICE: Calling GET {}", targetUri);

        return webClient.get()
                .uri(targetUri)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .header("X-User-Id", requesterId)
                .header("X-User-Role", requesterRole)
                .retrieve()
                .bodyToMono(UserDTO.class)
                .doOnError(e -> log.error("Failed to fetch user {}: {}", userId, e.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }
}