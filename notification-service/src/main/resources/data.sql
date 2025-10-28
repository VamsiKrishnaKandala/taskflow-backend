/*-- ============================================================
-- DATA: Sample Notifications (Final Structure)
-- Description: Initial demo data for Notification Service
-- ============================================================

-- NOTE: We only insert into columns that exist in the new schema.

INSERT INTO notifications (sequence_id, user_id, message, is_read, metadata, created_at)
VALUES
(1, 'USR-001', 'Task "Design Dashboard" created in project Alpha', FALSE,
 '{"taskId":"TSK-101","projectId":"PRJ-001","event":"TASK_CREATED"}', NOW()),

(2, 'USR-002', 'You were assigned to "Fix Bug #42" by USR-003', FALSE,
 '{"taskId":"TSK-102","projectId":"PRJ-001","event":"TASK_ASSIGNED"}', NOW()),

(3, 'USR-001', 'Project Alpha has been updated', TRUE,
 '{"projectId":"PRJ-001","event":"PROJECT_UPDATED"}', NOW()),

(4, 'USR-003', 'Task "Code Review" updated', FALSE,
 '{"taskId":"TSK-103","projectId":"PRJ-002","event":"TASK_UPDATED"}', NOW()),

(5, 'USR-002', 'You were added to project Beta', FALSE,
 '{"projectId":"PRJ-002","event":"PROJECT_MEMBER_ADDED"}', NOW());

-- Set the starting point for the AUTO_INCREMENT sequence
-- This ensures the next new notification gets sequence_id = 6 (formatted as NF-006).
ALTER TABLE notifications AUTO_INCREMENT = 6;*/