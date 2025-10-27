package com.taskflowpro.taskservice.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Client to send notification events to Notification Service.
 * Used by TaskService to trigger live updates & alerts.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceClientWebClient {

    private final WebClient.Builder webClientBuilder;

    // The base URL includes http://localhost:8084/api/v1
    @Value("${notification.service.url}")
    private String notificationServiceUrl;

    public Mono<Void> sendNotificationEvent(Map<String, Object> payload) {
        // FIX: Configure the builder to use the explicit base URL property.
        // We ensure the base URL is set correctly, then append the resource path.
        WebClient client = webClientBuilder
            .baseUrl(notificationServiceUrl) 
            .build();

        return client.post()
                // Use the correct resource path relative to the base URL
                .uri("/notifications") 
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("✅ Notification event sent successfully to {}", notificationServiceUrl + "/notifications"))
                .doOnError(e -> log.error("❌ Failed to send notification event to {}: {}", notificationServiceUrl + "/notifications", e.getMessage()));
    }
}