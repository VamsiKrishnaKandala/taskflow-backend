package com.taskflowpro.taskservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserServiceClientWebClient {

    private final WebClient userServiceWebClient;

    public Mono<Void> getUserById(String userId) {
        return userServiceWebClient.get()
                .uri("/{id}", userId)
                .retrieve()
                .onStatus(status -> status.value() == 404,
                        clientResponse -> {
                            log.warn("User {} not found", userId);
                            return Mono.error(new com.taskflowpro.taskservice.exception.InvalidTaskDataException(
                                    "Invalid Assignee ID: " + userId));
                        })
                .bodyToMono(String.class)
                .doOnNext(body -> log.debug("User {} exists with body: {}", userId, body))
                .then();
    }
}