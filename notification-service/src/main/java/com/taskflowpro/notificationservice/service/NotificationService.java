package com.taskflowpro.notificationservice.service;

import com.taskflowpro.notificationservice.dto.NotificationRequestDTO;
import com.taskflowpro.notificationservice.dto.NotificationResponseDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service API for notification operations.
 */
public interface NotificationService {

    /**
     * Create a notification from an incoming event. Enriches event with task/user/project details (best-effort),
     * saves to DB and emits to SSE sink.
     */
    Mono<NotificationResponseDTO> createNotification(NotificationRequestDTO request);

    /**
     * Get all notifications for a user (from DB).
     */
    Flux<NotificationResponseDTO> getNotificationsForUser(String userId);

    /**
     * Get all notifications in the system (for admin/debugging).
     */
    Flux<NotificationResponseDTO> getAllNotifications();

    /**
     * Stream notifications globally (SSE). Caller can filter by userId on handler level.
     */
    Flux<com.taskflowpro.notificationservice.model.Notification> notificationStream();

    /**
     * Mark notification read (toggle).
     */
    Mono<NotificationResponseDTO> markAsRead(String notificationId);
}
