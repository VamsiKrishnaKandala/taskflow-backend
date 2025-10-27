package com.taskflowpro.projectservice.client;


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

    @Value("${notification.service.url}")
    private String notificationServiceUrl;

    public Mono<Void> sendNotificationEvent(Map<String, Object> payload) {
        return webClientBuilder.build()
                .post()
                .uri(notificationServiceUrl)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("✅ Notification event sent successfully"))
                .doOnError(e -> log.error("❌ Failed to send notification event: {}", e.getMessage()));
    }
}
