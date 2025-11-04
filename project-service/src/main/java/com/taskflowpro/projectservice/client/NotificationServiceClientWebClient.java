package com.taskflowpro.projectservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceClientWebClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${notification.service.url}") // http://localhost:8080
    private String notificationServiceUrl;

    public Mono<Void> sendNotificationEvent(Map<String, Object> payload, String authorizationHeader, String requesterId, String requesterRole) {
        
        // --- THIS IS THE FIX ---
        // The correct, full path to the notification service endpoint
        String targetUri = notificationServiceUrl + "/notifications";
        // ---
        
        log.debug("PROJECT-SERVICE: Sending notification event to {} on behalf of user {}", targetUri, requesterId);

        return webClientBuilder.build()
                .post()
                .uri(targetUri)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader) // Forward token
                .header("X-User-Id", requesterId)
                .header("X-User-Role", requesterRole)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Void.class) // Expect an empty 200 OK
                .doOnSuccess(v -> log.info("Successfully sent notification for user {}", payload.get("recipientUserId")))
                .doOnError(ex -> log.error("Failed to send notification event for user {}: {}", payload.get("recipientUserId"), ex.getMessage()))
                .onErrorResume(ex -> Mono.empty()); // Don't block the main flow
    }
}