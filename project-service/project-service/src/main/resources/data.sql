-- ============================================================
-- 🗂️ TaskFlow Pro - Project Service
-- Sample Data for Reactive Project Entity
-- ============================================================

-- Clean up before insert (for dev/testing environments)
DELETE FROM projects;

-- ============================================================
-- Insert sample project records (PF-001 to PF-005)
-- ============================================================

INSERT INTO projects (id, name, description, deadline, member_ids, tags)
VALUES
-- 🧱 PF-001
('PF-001', 
 'TaskFlow Core Setup', 
 'Set up reactive microservices architecture with Spring Boot WebFlux and R2DBC.',
 DATE_ADD(CURDATE(), INTERVAL 30 DAY),
 JSON_ARRAY('U001', 'U002', 'U003'),
 JSON_ARRAY('backend', 'springboot', 'architecture')),

-- 💻 PF-002
('PF-002', 
 'TaskFlow Frontend', 
 'Develop Next.js + TypeScript frontend with TanStack Query for reactive UI.',
 DATE_ADD(CURDATE(), INTERVAL 45 DAY),
 JSON_ARRAY('U004', 'U005'),
 JSON_ARRAY('frontend', 'nextjs', 'typescript')),

-- 🧩 PF-003
('PF-003', 
 'Notification Service', 
 'Implement Reactive Notification microservice using WebFlux and RabbitMQ.',
 DATE_ADD(CURDATE(), INTERVAL 60 DAY),
 JSON_ARRAY('U002', 'U006'),
 JSON_ARRAY('notifications', 'webflux', 'rabbitmq')),

-- 📊 PF-004
('PF-004', 
 'Analytics Service', 
 'Build analytics module using reactive streams and data aggregation logic.',
 DATE_ADD(CURDATE(), INTERVAL 75 DAY),
 JSON_ARRAY('U005', 'U007'),
 JSON_ARRAY('analytics', 'reactive', 'dataflow')),

-- 🧠 PF-005
('PF-005', 
 'Task Management UI', 
 'Design and build the reactive task board with drag-and-drop and live updates.',
 DATE_ADD(CURDATE(), INTERVAL 90 DAY),
 JSON_ARRAY('U001', 'U004', 'U008'),
 JSON_ARRAY('ui', 'dragdrop', 'realtime'));