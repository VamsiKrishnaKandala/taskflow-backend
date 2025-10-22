-- ============================================================
-- 🗂️ TaskFlow Pro - Project Service
-- Database Schema Definition (Reactive R2DBC compatible)
-- ============================================================

-- Drop the table if it exists (for development/testing)
DROP TABLE IF EXISTS projects;

-- ============================================================
-- Create the "projects" table
-- ============================================================

CREATE TABLE projects (
    id              VARCHAR(20)  PRIMARY KEY,             -- e.g., PF-001
    name            VARCHAR(255) NOT NULL,                -- Project name
    description     TEXT,                                 -- Detailed description
    deadline        DATE,                                 -- Project deadline
    member_ids      JSON,                                 -- Array of user IDs assigned to the project
    tags            JSON,                                 -- Project tags/labels
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,  -- Record creation time
    updated_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP 
                     ON UPDATE CURRENT_TIMESTAMP           -- Auto-update on modification
);

-- ============================================================
-- Optional Indexes for Better Query Performance
-- ============================================================

---CREATE INDEX idx_projects_deadline ON projects(deadline);
---CREATE INDEX idx_projects_name ON projects(name);

-- ============================================================
-- End of schema
-- ============================================================