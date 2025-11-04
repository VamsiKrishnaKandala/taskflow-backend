package com.taskflow.analyticsservice.config;

import com.taskflow.analyticsservice.dto.ProjectDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

/**
 * WebClient to fetch data from the Project Service via the API Gateway.
 */
@Component
@Slf4j
public class ProjectServiceClient {

    private final WebClient webClient;
    @Value("${project.service.url}")
    private String projectServiceUrl; // http://localhost:8080

    public ProjectServiceClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * Fetches all projects for a specific Manager or Admin
     */
    public Flux<ProjectDTO> getAllProjects(String authorizationHeader, String requesterId, String requesterRole) {
        String targetUri = projectServiceUrl + "/projects";
        log.debug("ANALYTICS-SERVICE: Calling GET {}", targetUri);

        return webClient.get()
                .uri(targetUri)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .header("X-User-Id", requesterId)
                .header("X-User-Role", requesterRole)
                .retrieve()
                .bodyToFlux(ProjectDTO.class)
                .doOnError(e -> log.error("Failed to fetch all projects: {}", e.getMessage()))
                .onErrorResume(e -> Flux.empty());
    }

    // --- ADD THIS NEW METHOD ---
    /**
     * Fetches a single project by its ID.
     * This call is secured and requires the user's auth headers to be forwarded.
     */
    public Mono<ProjectDTO> getProjectById(String projectId, String authorizationHeader, String requesterId, String requesterRole) {
        String targetUri = projectServiceUrl + "/projects/" + projectId;
        
        log.debug("ANALYTICS-SERVICE: Calling Project Service: GET {}", targetUri);

        return webClient.get()
                .uri(targetUri)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .header("X-User-Id", requesterId)
                .header("X-User-Role", requesterRole)
                .retrieve()
                .bodyToMono(ProjectDTO.class)
                .doOnError(e -> log.error("Failed to fetch project {}: {}", projectId, e.getMessage()))
                .onErrorResume(e -> Mono.empty()); // On error, return an empty Mono
    }
    // --- END OF NEW METHOD ---
}