-- ==============================================
-- TaskFlow Pro - Task Service Table Schema
-- ==============================================

DROP TABLE IF EXISTS tasks;

CREATE TABLE tasks (
    id VARCHAR(20) PRIMARY KEY,
    project_id VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'TODO',       -- TODO | IN_PROGRESS | REVIEW | DONE
    priority VARCHAR(20) DEFAULT 'MEDIUM',            -- LOW | MEDIUM | HIGH
    assignee_ids TEXT,                                -- comma-separated user IDs
    tags TEXT,                                        -- comma-separated tags
    created_by VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL,
    due_date DATE
);

-- Optional: index for faster project lookup
-- CREATE INDEX idx_project_id ON tasks(project_id);
