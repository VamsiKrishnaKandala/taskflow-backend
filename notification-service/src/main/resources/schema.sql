-- ============================================================
-- SCHEMA: Notification Service (FINAL CLEANUP - Single ID Source)
-- Description: Stores notification records for TaskFlow Pro, using DB sequence only.
-- Author: TaskFlow Pro Team
-- ============================================================

DROP TABLE IF EXISTS notifications;

CREATE TABLE notifications (
    -- FIX: This is the ONLY ID field. It is BIGINT and auto-incremented.
    sequence_id BIGINT PRIMARY KEY AUTO_INCREMENT,

    -- The 'id' VARCHAR(20) column has been REMOVED as its value ('NF-XXX')
    -- is now computed dynamically by the application from sequence_id.

    user_id VARCHAR(50) NOT NULL,                 -- Target user for notification
    message VARCHAR(255) NOT NULL,                -- Human-readable message
    is_read BOOLEAN DEFAULT FALSE,                -- Whether the user has read this notification
    metadata TEXT,                                -- Optional JSON-like metadata
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP -- Timestamp when created
);

-- ============================================================
-- Notes:
-- 1. The primary key is now ONLY the auto-increment 'sequence_id'.
-- 2. The application's Notification.getId() method computes the 'NF-XXX' string.
-- 3. The race condition is permanently eliminated.
-- 4. Indexes can be added if queries on user_id are frequent:
   CREATE INDEX idx_notifications_user_id ON notifications(user_id);
-- ============================================================