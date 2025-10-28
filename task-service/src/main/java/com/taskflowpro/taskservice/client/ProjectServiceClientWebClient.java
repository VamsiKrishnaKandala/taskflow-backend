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
public class ProjectServiceClientWebClient {

    private final WebClient projectServiceWebClient;
    
    @Value("${project.service.url}") // http://localhost:8080
    private String projectServiceUrl;

    public Mono<Void> getProjectById(String projectId, String requesterId, String requesterRole, String authorizationHeader) {
        log.debug("TASK-SERVICE: Checking existence of project {} on behalf of user {}", projectId, requesterId);
        
        // Construct full URL from gateway root
        String targetUri = projectServiceUrl + "/api/v1/projects/" + projectId;
        
        return projectServiceWebClient.get()
                .uri(targetUri) // Use the full gateway path
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader) // <-- FORWARD THE TOKEN
                .header("X-User-Id", requesterId)
                .header("X-User-Role", requesterRole)
                .retrieve()
                .onStatus(status -> status.value() == 404,
                        clientResponse -> {
                            log.warn("Project {} not found (404). Validation will fail.", projectId);
                            return Mono.error(new InvalidTaskDataException("Project with ID " + projectId + " does not exist."));
                        })
                .onStatus(status -> status.value() == 403,
                        clientResponse -> {
                             log.warn("Access Denied for user {} trying to access project {}", requesterId, projectId);
                             return Mono.error(new InvalidTaskDataException("User is not a member of project " + projectId));
                        })
                .bodyToMono(String.class)
                .doOnNext(body -> log.debug("Project {} exists.", projectId))
                .then();
    }
}