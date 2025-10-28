package com.taskflow.analyticsservice.handler;

import com.taskflow.analyticsservice.dto.AnalyticsData;
import com.taskflow.analyticsservice.dto.AnalyticsSummary;
import com.taskflow.analyticsservice.exception.AccessDeniedException;
import com.taskflow.analyticsservice.exception.ErrorResponse;
import com.taskflow.analyticsservice.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime; // Make sure this is imported if ErrorResponse needs it

@Component
@RequiredArgsConstructor
@Slf4j
public class AnalyticsHandler {

    private final AnalyticsService analyticsService;

    // --- Authorization Header Helpers (no changes) ---
    private String getRequesterId(ServerRequest request) {
        return request.headers().firstHeader("X-User-Id");
    }
    private String getRequesterRole(ServerRequest request) {
        return request.headers().firstHeader("X-User-Role");
    }
    private String getAuthHeader(ServerRequest request) {
        return request.headers().firstHeader(HttpHeaders.AUTHORIZATION);
    }
    // ---

    /**
     * Handles GET /analytics/project/{projectId}/velocity
     * Access: ADMIN, MANAGER
     */
    public Mono<ServerResponse> getProjectVelocity(ServerRequest request) {
        String projectId = request.pathVariable("projectId");
        String requesterId = getRequesterId(request);
        String requesterRole = getRequesterRole(request);
        String authHeader = getAuthHeader(request);
        log.info("Handler: getProjectVelocity for {} invoked by User: {}, Role: {}", projectId, requesterId, requesterRole);

        // --- FIX: Wrap logic in Mono.fromCallable ---
        return Mono.fromCallable(() -> {
            // --- AUTHORIZATION CHECK ---
            if (!"ROLE_ADMIN".equals(requesterRole) && !"ROLE_MANAGER".equals(requesterRole)) {
                log.warn("Access Denied: User {} ({}) attempted to get project analytics.", requesterId, requesterRole);
                // Throw exception instead of returning Mono.error
                throw new AccessDeniedException("Only ADMIN or MANAGER users can view project analytics.");
            }
            // If auth passes, return the Flux for the chain
            return analyticsService.getProjectVelocity(projectId, authHeader, requesterId, requesterRole);
        })
        .flatMapMany(velocityFlux -> velocityFlux) // Flatten the Mono<Flux> to just a Flux
        .collectList()
        .flatMap(dataList ->
            ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(dataList)
        )
        // This chain will now catch the AccessDeniedException from the 'if' check
        .onErrorResume(AccessDeniedException.class, ex -> buildErrorResponse(ex, HttpStatus.FORBIDDEN, "Access Denied", request))
        .onErrorResume(e -> buildErrorResponse(e, HttpStatus.INTERNAL_SERVER_ERROR, "Error processing velocity", request));
    }

    /**
     * Handles GET /analytics/user/{userId}/summary
     * Access: ADMIN, MANAGER
     */
    public Mono<ServerResponse> getUserSummary(ServerRequest request) {
        String userId = request.pathVariable("userId");
        String requesterId = getRequesterId(request);
        String requesterRole = getRequesterRole(request);
        String authHeader = getAuthHeader(request);
        log.info("Handler: getUserSummary for {} invoked by User: {}, Role: {}", userId, requesterId, requesterRole);

        // --- FIX: Wrap logic in Mono.fromCallable ---
        return Mono.fromCallable(() -> {
            // --- AUTHORIZATION CHECK ---
            if (!"ROLE_ADMIN".equals(requesterRole) && !"ROLE_MANAGER".equals(requesterRole)) {
                log.warn("Access Denied: User {} ({}) attempted to get user analytics.", requesterId, requesterRole);
                // Throw exception instead of returning Mono.error
                throw new AccessDeniedException("Only ADMIN or MANAGER users can view user analytics.");
            }
            // If auth passes, return the Mono for the chain
            return analyticsService.getUserSummary(userId, authHeader, requesterId, requesterRole);
        })
        .flatMap(summaryMono -> summaryMono) // Flatten the Mono<Mono> to just a Mono
        .flatMap(summary -> ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(summary))
        // This chain will now catch the AccessDeniedException from the 'if' check
        .onErrorResume(AccessDeniedException.class, ex -> buildErrorResponse(ex, HttpStatus.FORBIDDEN, "Access Denied", request))
        .onErrorResume(e -> buildErrorResponse(e, HttpStatus.INTERNAL_SERVER_ERROR, "Error processing summary", request));
    }

    /**
     * Helper to build standard error responses within the handler.
     * (No changes needed here, your ErrorResponse constructor handles the String -> List)
     */
    private Mono<ServerResponse> buildErrorResponse(Throwable ex, HttpStatus status, String message, ServerRequest request) {
        ErrorResponse errorBody = new ErrorResponse(
            status.value(),
            message,
            ex.getMessage() // Pass the string detail, your record constructor will handle it
        );
        log.warn("Handler mapping error [{}]: {} - {} for path {}", status, message, ex.getMessage(), request.path());
        return ServerResponse.status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(errorBody);
    }
}