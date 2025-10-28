package com.taskflowpro.taskservice.client;

import com.taskflowpro.taskservice.exception.InvalidTaskDataException;
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
public class UserServiceClientWebClient {

    private final WebClient userServiceWebClient;

    @Value("${user.service.url}") // http://localhost:8080
    private String userServiceUrl;

    public Mono<Void> getUserById(String userId, String requesterId, String requesterRole, String authorizationHeader) {
        log.debug("TASK-SERVICE: Checking existence of user {} on behalf of user {}", userId, requesterId);
        
        // Construct the full URL
        String targetUri = userServiceUrl + "/users/" + userId;
        
        return userServiceWebClient.get()
                .uri(targetUri) // Use the full, correct path
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader) // <-- FORWARD THE TOKEN
                .header("X-User-Id", requesterId)
                .header("X-User-Role", requesterRole)
                .retrieve()
                .onStatus(status -> status.value() == 404,
                        clientResponse -> {
                            log.warn("User {} not found (404)", userId);
                            return Mono.error(new InvalidTaskDataException("Invalid Assignee ID: " + userId));
                        })
                .onStatus(status -> status.value() == 403,
                        clientResponse -> {
                             log.warn("Access Denied for user {} trying to access user profile {}", requesterId, userId);
                             return Mono.error(new InvalidTaskDataException("User " + requesterId + " is not allowed to view profile " + userId));
                        })
                .bodyToMono(String.class)
                .doOnNext(body -> log.debug("User {} exists.", userId))
                .then();
    }
}