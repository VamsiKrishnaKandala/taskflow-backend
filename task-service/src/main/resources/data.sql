-- ==============================================
-- Sample data for Task Service
-- ==============================================

INSERT INTO tasks (id, project_id, title, description, status, priority, assignee_ids, tags, created_by, created_at, due_date)
VALUES
('TF-001', 'PF-001', 'Design Homepage', 'Create initial homepage mockups', 'TODO', 'HIGH', 'user1,user2', 'UI,frontend', 'manager1', NOW(), '2025-10-30'),
('TF-002', 'PF-001', 'Implement Login API', 'Develop login backend', 'IN_PROGRESS', 'MEDIUM', 'user3', 'backend,auth', 'manager1', NOW(), '2025-10-28'),
('TF-003', 'PF-002', 'Write Unit Tests', 'Cover task service with unit tests', 'REVIEW', 'LOW', 'user2', 'testing', 'manager2', NOW(), '2025-11-02');
