package com.taskflowpro.notificationservice.service;

import com.taskflowpro.notificationservice.client.ProjectServiceClientWebClient;
import com.taskflowpro.notificationservice.client.TaskServiceClientWebClient;
import com.taskflowpro.notificationservice.client.UserServiceClientWebClient;
import com.taskflowpro.notificationservice.dto.NotificationRequestDTO;
import com.taskflowpro.notificationservice.dto.NotificationResponseDTO;
import com.taskflowpro.notificationservice.model.Notification;
import com.taskflowpro.notificationservice.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Core Notification Service implementation.
 * Handles event processing, enrichment, persistence, and live SSE streaming.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    // Dependencies injected via constructor or framework
    private final NotificationRepository notificationRepository;
    private final TaskServiceClientWebClient taskClient;
    private final UserServiceClientWebClient userClient;
    private final ProjectServiceClientWebClient projectClient;
    private final Sinks.Many<Notification> notificationSink;

    // --- ID GENERATION LEGACY FIELDS (Kept for completeness but unused) ---
    private final AtomicBoolean idLock = new AtomicBoolean(false);
    private final Mono<Long> idGeneratorGate = Mono.just(1L).cache();
    private final Scheduler idGenerationScheduler = Schedulers.newSingle("notification-id-creator"); 

    /**
     * Creates a new notification based on the request DTO.
     * It relies on the R2DBC database to generate the sequence ID automatically,
     * and uses the simplified entity model where the formatted ID is computed on demand.
     */
    @Override
    public Mono<NotificationResponseDTO> createNotification(NotificationRequestDTO request) {
        Mono<Map> taskMono = (request.getTaskId() == null) ? Mono.empty() : taskClient.getTaskById(request.getTaskId());
        Mono<Map> recipientMono = (request.getRecipientUserId() == null) ? Mono.empty() : userClient.getUserById(request.getRecipientUserId());
        Mono<Map> projectMono = (request.getProjectId() == null) ? Mono.empty() : projectClient.getProjectById(request.getProjectId());

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

                // 1. Build entity for INSERT (sequenceId is null)
                Notification n = Notification.builder()
                    .userId(request.getRecipientUserId())
                    .message(message)
                    .read(false)
                    .metadata(buildMetadata(request, task, recipient, project))
                    .createdAt(request.getOccurredAt() == null ? LocalDateTime.now() : request.getOccurredAt())
                    .build();

                // 2. Single Save: INSERT the record and retrieve the auto-generated sequenceId.
                // The DB ID is now correct, and the formatted ID is available via saved.getId().
                return notificationRepository.save(n)
                    .flatMap(saved -> {
                        Sinks.EmitResult result = notificationSink.tryEmitNext(saved);
                        if (result.isFailure()) {
                            log.error("⚠️ Failed to emit notification to sink: {} result={}", saved.getId(), result);
                        } else {
                            // saved.getId() automatically returns the formatted NF-### string.
                            log.info("✅ Notification emitted: {} -> user {}", saved.getId(), saved.getUserId());
                        }
                        return Mono.just(saved);
                    })
                    .map(NotificationResponseDTO::fromEntity)
                    .switchIfEmpty(Mono.error(new RuntimeException("Notification could not be saved")));
            })
            .doOnError(e -> log.error("❌ Error creating notification: {}", e.getMessage(), e));
    }

    /**
     * Builds a human-readable message based on the event type and related entities.
     */
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
                
            // === FIX: Add handling for PROJECT_DELETED ===
            case PROJECT_DELETED:
                // Use coalesce(request.getTitle(), ...) as a fallback for the project name if the enrichment failed
                return String.format("Project %s has been deleted", 
                        coalesce(request.getTitle(), project != null ? String.valueOf(project.get("name")) : request.getProjectId()));
                
            default:
                return coalesce(request.getTitle(), "You have a new notification");
        }
    }

    /**
     * Utility method to return the first non-blank string.
     */
    private String coalesce(String a, String b) {
        return (a != null && !a.isBlank()) ? a : (b != null ? b : "");
    }

    /**
     * Builds metadata JSON string for the notification.
     */
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
     */
    @Override
    public Flux<NotificationResponseDTO> getNotificationsForUser(String userId) {
        return notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId)
            .map(NotificationResponseDTO::fromEntity);
    }

    /**
     * Fetches all notifications in the system, sorted by creation time.
     */
    @Override
    public Flux<NotificationResponseDTO> getAllNotifications() {
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
     */
    @Override
    public Mono<NotificationResponseDTO> markAsRead(String notificationId) {
        return notificationRepository.findById(notificationId)
            .switchIfEmpty(Mono.error(new IllegalArgumentException("Notification not found: " + notificationId)))
            .flatMap(n -> {
                n.markAsRead();
                return notificationRepository.save(n);
            })
            .map(NotificationResponseDTO::fromEntity);
    }
}