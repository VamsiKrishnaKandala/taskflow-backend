package com.taskflowpro.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO used by other services to push events to the Notification Service.
 *
 * Example:
 * {
 *   "eventType":"TASK_CREATED",
 *   "taskId":"TF-001",
 *   "projectId":"PF-001",
 *   "recipientUserId":"U001",
 *   "initiatorUserId":"U005",
 *   "title":"Create login endpoint",
 *   "payload": {...}
 * }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationRequestDTO {
    private EventType eventType;
    private String taskId;
    private String projectId;
    private String recipientUserId; // who should receive the notification
    private String initiatorUserId; // who triggered the event
    private String title;           // e.g. task title
    private Map<String, Object> payload;
    private LocalDateTime occurredAt;
}
