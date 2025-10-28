package com.taskflowpro.notificationservice.service;

import com.taskflowpro.notificationservice.client.ProjectServiceClientWebClient;
import com.taskflowpro.notificationservice.client.TaskServiceClientWebClient;
import com.taskflowpro.notificationservice.client.UserServiceClientWebClient;
import com.taskflowpro.notificationservice.dto.NotificationRequestDTO;
import com.taskflowpro.notificationservice.dto.NotificationResponseDTO;
import com.taskflowpro.notificationservice.exception.AccessDeniedException; // Import
import com.taskflowpro.notificationservice.exception.NotificationNotFoundException; // You may need to create this
import com.taskflowpro.notificationservice.model.Notification;
import com.taskflowpro.notificationservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap; // Import HashMap
import java.util.concurrent.atomic.AtomicBoolean; // Keep existing imports
import reactor.core.scheduler.Scheduler; // Keep existing imports
import reactor.core.scheduler.Schedulers; // Keep existing imports


/**
 * Core Notification Service implementation.
 * Handles event processing, enrichment, persistence, and live SSE streaming.
 * NOW INCLUDES ROLE-BASED AUTHORIZATION.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService { // Implements the new interface

    private final NotificationRepository notificationRepository;
    private final TaskServiceClientWebClient taskClient;
    private final UserServiceClientWebClient userClient;
    private final ProjectServiceClientWebClient projectClient;
    private final Sinks.Many<Notification> notificationSink;

    // --- ID GENERATION LEGACY FIELDS (Kept from original) ---
    private final AtomicBoolean idLock = new AtomicBoolean(false);
    private final Mono<Long> idGeneratorGate = Mono.just(1L).cache();
    private final Scheduler idGenerationScheduler = Schedulers.newSingle("notification-id-creator");

    // --- Helper for Role Checking ---
    private boolean isAdmin(String requesterRole) {
        return "ROLE_ADMIN".equals(requesterRole);
    }

    /**
     * Creates a new notification.
     * Forwards auth headers to WebClients for data enrichment.
     */
    @Override
    public Mono<NotificationResponseDTO> createNotification(NotificationRequestDTO request, String authorizationHeader, String requesterId, String requesterRole) {
        
        // --- MODIFIED: Pass auth headers to enrichment clients ---
        Mono<Map> taskMono = taskClient.getTaskById(request.getTaskId(), authorizationHeader, requesterId, requesterRole);
        Mono<Map> recipientMono = userClient.getUserById(request.getRecipientUserId(), authorizationHeader, requesterId, requesterRole);
        Mono<Map> projectMono = projectClient.getProjectById(request.getProjectId(), authorizationHeader, requesterId, requesterRole);

        return Mono.zipDelayError(
                taskMono.defaultIfEmpty(Map.of()),
                recipientMono.defaultIfEmpty(Map.of()),
                projectMono.defaultIfEmpty(Map.of())
            )
            .flatMap(tuple -> {
                Map task = tuple.getT1();
                Map recipient = tuple.getT2();
                Map project = tuple.getT3();

                String message = buildMessage(request, task, recipient, project);

                Notification n = Notification.builder()
                        .userId(request.getRecipientUserId())
                        .message(message)
                        .read(false)
                        .metadata(buildMetadata(request, task, recipient, project))
                        .createdAt(request.getOccurredAt() == null ? LocalDateTime.now() : request.getOccurredAt())
                        .build();

                return notificationRepository.save(n)
                        .flatMap(saved -> {
                            Sinks.EmitResult result = notificationSink.tryEmitNext(saved);
                            if (result.isFailure()) {
                                log.error("⚠️ Failed to emit notification to sink: {} result={}", saved.getId(), result);
                            } else {
                                log.info("✅ Notification emitted: {} -> user {}", saved.getId(), saved.getUserId());
                            }
                            return Mono.just(saved);
                        })
                        .map(NotificationResponseDTO::fromEntity)
                        .switchIfEmpty(Mono.error(new RuntimeException("Notification could not be saved")));
            })
            .doOnError(e -> log.error("❌ Error creating notification: {}", e.getMessage(), e));
    }

    // ... (buildMessage, coalesce, buildMetadata helpers remain the same) ...
    private String buildMessage(NotificationRequestDTO request, Map task, Map recipient, Map project) {
        switch (request.getEventType() == null ? com.taskflowpro.notificationservice.dto.EventType.GENERIC : request.getEventType()) {
            case TASK_CREATED:
                return String.format("Task '%s' created in project %s",
                        coalesce(request.getTitle(), task != null ? String.valueOf(task.get("title")) : "Task"),
                        request.getProjectId() != null ? request.getProjectId() : (project != null ? String.valueOf(project.get("name")) : ""));
            case TASK_ASSIGNED:
                return String.format("You were assigned to '%s' by %s",
                        coalesce(request.getTitle(), task != null ? String.valueOf(task.get("title")) : "Task"),
                        coalesce(request.getInitiatorUserId(), "someone"));
            case TASK_UPDATED:
                return String.format("Task '%s' updated", coalesce(request.getTitle(), task != null ? String.valueOf(task.get("title")) : "Task"));
            case TASK_STATUS_CHANGED:
                if (request.getPayload() != null && request.getPayload().containsKey("to")) {
                    return String.format("Task '%s' status changed to %s",
                            coalesce(request.getTitle(), task != null ? String.valueOf(task.get("title")) : "Task"),
                            request.getPayload().get("to"));
                }
                return "Task status updated";
            case PROJECT_MEMBER_ADDED:
                return String.format("You were added to project %s", project != null ? project.get("name") : request.getProjectId());
            case PROJECT_UPDATED:
                return String.format("Project %s has been updated", project != null ? project.get("name") : request.getProjectId());
            case PROJECT_DELETED:
                return String.format("Project %s has been deleted", 
                        coalesce(request.getTitle(), project != null ? String.valueOf(project.get("name")) : request.getProjectId()));
            default:
                return coalesce(request.getTitle(), "You have a new notification");
        }
    }
    private String coalesce(String a, String b) {
        return (a != null && !a.isBlank()) ? a : (b != null ? b : "");
    }
    private String buildMetadata(NotificationRequestDTO request, Map task, Map recipient, Map project) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        if (request.getTaskId() != null) sb.append("\"taskId\":\"").append(request.getTaskId()).append("\",");
        if (request.getProjectId() != null) sb.append("\"projectId\":\"").append(request.getProjectId()).append("\",");
        if (request.getInitiatorUserId() != null) sb.append("\"initiator\":\"").append(request.getInitiatorUserId()).append("\",");
        sb.append("\"event\":\"").append(request.getEventType()).append("\",");
        sb.append("\"rawPayloadPresent\":").append(request.getPayload() != null);
        sb.append("}");
        return sb.toString();
    }


    /**
     * Fetches notifications for a specific user, sorted by creation time.
     * Authorization: Only the user themselves or an Admin can access.
     */
    @Override
    public Flux<NotificationResponseDTO> getNotificationsForUser(String userId, String requesterId, String requesterRole) {
        log.info("Attempt to get notifications for user {} by requester {}", userId, requesterId);
        
        // --- AUTHORIZATION CHECK ---
        if (!isAdmin(requesterRole) && !requesterId.equals(userId)) {
            log.warn("Access Denied: User {} ({}) attempted to read notifications for user {}", requesterId, requesterRole, userId);
            return Flux.error(new AccessDeniedException("You are not authorized to view these notifications."));
        }
        // --- END CHECK ---
        
        log.debug("Access granted for user {} to view notifications for {}", requesterId, userId);
        return notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
                .map(NotificationResponseDTO::fromEntity);
    }

    /**
     * Fetches all notifications in the system, sorted by creation time.
     * Authorization: Admin only.
     */
    @Override
    public Flux<NotificationResponseDTO> getAllNotifications(String requesterId, String requesterRole) {
        log.info("Attempt to get all notifications by requester {}", requesterId);
        
        // --- AUTHORIZATION CHECK ---
        if (!isAdmin(requesterRole)) {
            log.warn("Access Denied: User {} ({}) attempted to list all notifications.", requesterId, requesterRole);
            return Flux.error(new AccessDeniedException("Only ADMIN users can list all notifications."));
        }
        // --- END CHECK ---
        
        log.debug("Admin access granted for getAllNotifications");
        return notificationRepository.findAll()
                .sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .map(NotificationResponseDTO::fromEntity);
    }

    /**
     * Provides a live stream of notifications using Reactor's sink.
     */
    @Override
    public Flux<Notification> notificationStream() {
        return notificationSink.asFlux();
    }

    /**
     * Marks a notification as read by its ID.
     * Authorization: Only the user who owns the notification or an Admin.
     */
    @Override
    public Mono<NotificationResponseDTO> markAsRead(String notificationId, String requesterId, String requesterRole) {
        log.info("Attempt to mark notification {} as read by user {}", notificationId, requesterId);
        
        return notificationRepository.findById(notificationId)
                // Use a custom exception if you created one, otherwise IllegalArgumentException is fine
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Notification not found: " + notificationId))) 
                .flatMap(n -> {
                    
                    // --- AUTHORIZATION CHECK ---
                    if (!n.getUserId().equals(requesterId) && !isAdmin(requesterRole)) {
                         log.warn("Access Denied: User {} ({}) attempted to mark notification {} as read, but it belongs to user {}",
                                 requesterId, requesterRole, notificationId, n.getUserId());
                         return Mono.error(new AccessDeniedException("You are not authorized to modify this notification."));
                    }
                    // --- END CHECK ---
                    
                    log.info("Access granted. Marking notification {} as read", notificationId);
                    n.markAsRead(); // This method is in your Notification model, sets read=true
                    return notificationRepository.save(n);
                })
                .map(NotificationResponseDTO::fromEntity);
    }
}