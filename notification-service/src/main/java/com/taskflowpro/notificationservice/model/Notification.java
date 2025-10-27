package com.taskflowpro.notificationservice.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Notification entity persisted in R2DBC database (table: notifications).
 *
 * FIX: Uses DB-generated Long sequenceId as the sole primary key to eliminate race conditions.
 * The formatted 'NF-###' ID is now computed dynamically.
 *
 * Implements Persistable<String> for R2DBC management.
 *
 * Fields:
 * - sequenceId: Database-managed auto-increment Long ID (The true Primary Key).
 * - userId: recipient user ID
 * - message: user-facing message
 * - read: whether the notification has been read by the user
 * - metadata: optional JSON-like payload with task/project/event info
 * - createdAt: timestamp of creation
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Table("notifications")
public class Notification implements Persistable<String> {

    // --- FIX: The sole primary key field is the DB-generated Long sequence. ---
    @Id
    @Column("sequence_id") // Maps to the DB's auto-increment column
    private Long sequenceId;

    // The formatted string ID (NF-###) is no longer persisted in a separate column.
    // The previous 'id' field is REMOVED.

    @Column("user_id")
    private String userId;

    private String message;

    @Column("is_read")
    private boolean read;

    private String metadata;

    @Column("created_at")
    private LocalDateTime createdAt;

    // Kept the Transient field, though R2DBC now primarily relies on sequenceId == null
    @Transient
    private boolean isNew;

    /**
     * Calculates the formatted string ID for external use (e.g., DTOs).
     * Since the actual ID is a Long, we format it here for the Persistable contract.
     * If sequenceId is null (new entity), returns null for insert handling.
     */
    @Override
    public String getId() {
        if (sequenceId == null) {
            return null;
        }
        return String.format("NF-%03d", sequenceId);
    }

    // FIX: The persistence logic relies ONLY on the auto-generated sequenceId.
    @Override
    public boolean isNew() {
        // An entity is new if the auto-generated sequenceId hasn't been assigned by the DB yet.
        return sequenceId == null;
    }

    public void markAsRead() {
        this.read = true;
    }
}