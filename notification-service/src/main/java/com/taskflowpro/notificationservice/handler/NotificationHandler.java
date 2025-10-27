package com.taskflowpro.notificationservice.handler;

import com.taskflowpro.notificationservice.dto.NotificationRequestDTO;
import com.taskflowpro.notificationservice.dto.NotificationResponseDTO;
import com.taskflowpro.notificationservice.model.Notification;
import com.taskflowpro.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Functional handler that exposes endpoints:
 *  - POST /notifications -> create notification from event
 *  - GET /notifications/{userId} -> fetch persisted notifications for a user
 *  - GET /notifications -> fetch all notifications
 *  - GET /notifications/stream -> SSE stream (filter by ?userId=)
 *  - PUT /notifications/{id}/read -> mark as read
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationHandler {

    private final NotificationService notificationService;

    /**
     * Receive inbound event (NotificationRequestDTO) and create a notification.
     * This can be called by TaskService / ProjectService / other producers.
     */
    public Mono<ServerResponse> pushNotification(ServerRequest request) {
        return request.bodyToMono(NotificationRequestDTO.class)
                .flatMap(notificationService::createNotification)
                .flatMap(saved -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(saved))
                .onErrorResume(e -> {
                    log.error("Failed to create notification: {}", e.getMessage(), e);
                    return ServerResponse.badRequest()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Map.of("error", e.getMessage()));
                });
    }

    /**
     * Return stored notifications for a user from DB.
     */
    public Mono<ServerResponse> getNotificationsForUser(ServerRequest request) {
        String userId = request.pathVariable("userId");
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(notificationService.getNotificationsForUser(userId), NotificationResponseDTO.class);
    }

    /**
     * Return all notifications from DB (for admin or system view).
     */
    public Mono<ServerResponse> getAllNotifications(ServerRequest request) {
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(notificationService.getAllNotifications(), NotificationResponseDTO.class);
    }

    /**
     * SSE endpoint streaming notifications live.
     * Supports optional query param ?userId= to filter only the recipient's notifications.
     *
     * Replays recent notifications due to sink being configured with replay semantics.
     */
    public Mono<ServerResponse> streamNotifications(ServerRequest request) {
        String userId = request.queryParam("userId").orElse(null);

        return ServerResponse.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(
                        notificationService.notificationStream()
                                .filter(n -> userId == null || userId.equals(n.getUserId()))
                                .map(NotificationResponseDTO::fromEntity),
                        NotificationResponseDTO.class
                );
    }

    /**
     * Mark a single notification as read.
     */
    public Mono<ServerResponse> markAsRead(ServerRequest request) {
        String id = request.pathVariable("id");
        return notificationService.markAsRead(id)
                .flatMap(dto -> ServerResponse.ok().bodyValue(dto))
                .onErrorResume(e -> ServerResponse.badRequest().bodyValue(Map.of("error", e.getMessage())));
    }
}
