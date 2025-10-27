package com.taskflowpro.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO returned to clients (API or SSE).
 * Mirrors Notification entity but without DB internals.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponseDTO {
    private String id;
    private String userId;
    private String message;
    private String metadata;
    private boolean read;
    private LocalDateTime createdAt;

    public static NotificationResponseDTO fromEntity(com.taskflowpro.notificationservice.model.Notification n) {
        return NotificationResponseDTO.builder()
                .id(n.getId())
                .userId(n.getUserId())
                .message(n.getMessage())
                .metadata(n.getMetadata())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
