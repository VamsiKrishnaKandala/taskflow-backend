package com.taskflowpro.notificationservice.handler;

import com.taskflowpro.notificationservice.dto.NotificationRequestDTO;
import com.taskflowpro.notificationservice.dto.NotificationResponseDTO;
import com.taskflowpro.notificationservice.exception.AccessDeniedException; // Import
import com.taskflowpro.notificationservice.exception.ErrorResponse; // Import
import com.taskflowpro.notificationservice.model.Notification;
import com.taskflowpro.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders; // Import
import org.springframework.http.HttpStatus; // Import
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime; // Import
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationHandler {

    private final NotificationService notificationService;

    // --- Authorization Header Helpers ---
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
     * Endpoint for internal services (Task, Project) to push new notifications.
     * This endpoint *must* be secured (e.g., Admin/Manager only)
     */
    public Mono<ServerResponse> pushNotification(ServerRequest request) {
        String requesterId = getRequesterId(request);
        String requesterRole = getRequesterRole(request);
        String authHeader = getAuthHeader(request);
        
        // --- AUTHORIZATION CHECK ---
        // Only internal services (identified as Admin/Manager by their token) can create notifications.
        if (!"ROLE_ADMIN".equals(requesterRole) && !"ROLE_MANAGER".equals(requesterRole)) {
            log.warn("Access Denied: User {} ({}) attempted to push a notification.", requesterId, requesterRole);
            return Mono.error(new AccessDeniedException("Not authorized to create notifications."));
        }
        // --- END CHECK ---

        return request.bodyToMono(NotificationRequestDTO.class)
                .flatMap(notificationRequest -> notificationService.createNotification(notificationRequest, authHeader, requesterId, requesterRole))
                .flatMap(saved -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(saved));
        // GlobalExceptionHandler will catch errors
    }

    /**
     * Return stored notifications for a user from DB.
     * Authorization: Admin or the user themselves.
     */
    public Mono<ServerResponse> getNotificationsForUser(ServerRequest request) {
        String userId = request.pathVariable("userId");
        String requesterId = getRequesterId(request);
        String requesterRole = getRequesterRole(request);
        
        log.info("Handler: getNotificationsForUser {} by User: {}, Role: {}", userId, requesterId, requesterRole);

        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(notificationService.getNotificationsForUser(userId, requesterId, requesterRole), NotificationResponseDTO.class)
                // Add handler-level error mapping for 403
                .onErrorResume(AccessDeniedException.class, ex -> buildErrorResponse(ex, HttpStatus.FORBIDDEN, "Access Denied", request));
    }

    /**
     * Return all notifications from DB (Admin only).
     */
    public Mono<ServerResponse> getAllNotifications(ServerRequest request) {
        String requesterId = getRequesterId(request);
        String requesterRole = getRequesterRole(request);
        log.info("Handler: getAllNotifications by User: {}, Role: {}", requesterId, requesterRole);

        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(notificationService.getAllNotifications(requesterId, requesterRole), NotificationResponseDTO.class)
                // Add handler-level error mapping for 403
                .onErrorResume(AccessDeniedException.class, ex -> buildErrorResponse(ex, HttpStatus.FORBIDDEN, "Access Denied", request));
    }

    /**
     * SSE endpoint streaming notifications live.
     * Authorization: User can ONLY stream their own notifications.
     */
    public Mono<ServerResponse> streamNotifications(ServerRequest request) {
        String streamUserId = request.pathVariable("userId"); // Get user ID from path
        String requesterId = getRequesterId(request);
        String requesterRole = getRequesterRole(request);
        log.info("Handler: streamNotifications for {} invoked by User: {}, Role: {}", streamUserId, requesterId, requesterRole);

        // --- AUTHORIZATION CHECK ---
        // A user can ONLY subscribe to their own stream. (Admins cannot spy).
        if (requesterId == null || !requesterId.equals(streamUserId)) {
            log.warn("Access Denied: User {} attempted to stream notifications for user {}", requesterId, streamUserId);
            // We must return Mono.error here so it can be handled
            return Mono.error(new AccessDeniedException("You are not authorized to stream these notifications."));
        }
        // --- END CHECK ---

        return ServerResponse.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(
                    notificationService.notificationStream()
                            // Filter the global stream for this user *only*
                            .filter(n -> requesterId.equals(n.getUserId()))
                            .map(NotificationResponseDTO::fromEntity),
                    NotificationResponseDTO.class
                )
                // This will catch the AccessDeniedException from our check above
                .onErrorResume(AccessDeniedException.class, ex -> buildErrorResponse(ex, HttpStatus.FORBIDDEN, "Access Denied", request));
    }

    /**
     * Mark a single notification as read.
     * Authorization: Admin or owner of the notification.
     */
    public Mono<ServerResponse> markAsRead(ServerRequest request) {
        String id = request.pathVariable("id");
        String requesterId = getRequesterId(request);
        String requesterRole = getRequesterRole(request);
        log.info("Handler: markAsRead {} invoked by User: {}, Role: {}", id, requesterId, requesterRole);
        
        return notificationService.markAsRead(id, requesterId, requesterRole)
                .flatMap(dto -> ServerResponse.ok().bodyValue(dto))
                // Handle expected errors directly
                .onErrorResume(AccessDeniedException.class, ex -> buildErrorResponse(ex, HttpStatus.FORBIDDEN, "Access Denied", request))
                .onErrorResume(IllegalArgumentException.class, ex -> buildErrorResponse(ex, HttpStatus.NOT_FOUND, "Not Found", request)); // Catches "Notification not found"
    }

    // --- ADDED HELPER for handler-level error responses ---
    private Mono<ServerResponse> buildErrorResponse(Throwable ex, HttpStatus status, String message, ServerRequest request) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(message)
                .message(ex.getMessage())
                .build();
        log.warn("Handler mapping error [{}]: {} - {} for path {}", status, message, ex.getMessage(), request.path());
        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(errorResponse);
    }
}