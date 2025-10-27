package com.taskflowpro.taskservice.client;

import com.taskflowpro.taskservice.exception.InvalidTaskDataException; // You need this import
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
                .uri("/projects/{id}", projectId)
                .retrieve()
                // FIX: On 404, we MUST return a Mono.error to fail the Task creation chain.
                .onStatus(status -> status.value() == 404,
                        clientResponse -> {
                            log.warn("Project {} not found (404). Validation will fail.", projectId);
                            // Throwing a recognized exception will stop the createTask flow.
                            return Mono.error(new InvalidTaskDataException("Project with ID " + projectId + " does not exist."));
                        })
                // FIX: For all other successful statuses, we map the body to a String (or Map)
                .bodyToMono(String.class)
                .doOnNext(body -> log.debug("Project {} exists with body: {}", projectId, body))
                .then(); // converts Mono<String> to Mono<Void>
    }
}