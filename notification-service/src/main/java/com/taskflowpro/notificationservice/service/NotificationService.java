package com.taskflowpro.notificationservice.service;

import com.taskflowpro.notificationservice.dto.NotificationRequestDTO;
import com.taskflowpro.notificationservice.dto.NotificationResponseDTO;
import com.taskflowpro.notificationservice.model.Notification;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface NotificationService {

    /**
     * Creates a new notification.
     * (Called by other services, auth headers are for enrichment).
     */
    Mono<NotificationResponseDTO> createNotification(NotificationRequestDTO request, String authorizationHeader, String requesterId, String requesterRole);

    /**
     * Gets notifications for a specific user.
     * Must check if requesterId == userId OR requesterRole == ADMIN.
     */
    Flux<NotificationResponseDTO> getNotificationsForUser(String userId, String requesterId, String requesterRole);

    /**
     * Gets all notifications in the system.
     * Must check if requesterRole == ADMIN.
     */
    Flux<NotificationResponseDTO> getAllNotifications(String requesterId, String requesterRole);

    /**
     * Returns the live stream sink.
     */
    Flux<Notification> notificationStream();

    /**
     * Marks a specific notification as read.
     * Must check if requesterId == notification.userId OR requesterRole == ADMIN.
     */
    Mono<NotificationResponseDTO> markAsRead(String notificationId, String requesterId, String requesterRole);
}