package com.taskflowpro.taskservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectServiceClientWebClient {

    private final WebClient projectServiceWebClient;

    public Mono<Void> getProjectById(String projectId) {
        return projectServiceWebClient.get()
                .uri("/{id}", projectId)
                .retrieve()
                .onStatus(status -> status.value() == 404,
                        clientResponse -> {
                            log.warn("Project {} not found", projectId);
                            return Mono.empty();
                        })
                .bodyToMono(String.class) // we just need some body, could also use Map.class
                .doOnNext(body -> log.debug("Project {} exists with body: {}", projectId, body))
                .then(); // converts Mono<String> to Mono<Void> so TaskServiceImpl works
    }
}