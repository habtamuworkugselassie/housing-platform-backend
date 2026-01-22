-- Migration: Enhance project management with tasks, team members, milestones, and documents
-- This migration adds comprehensive project management features

-- Project Tasks table
CREATE TABLE IF NOT EXISTS project_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    phase_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'TODO',
    priority VARCHAR(50) NOT NULL DEFAULT 'MEDIUM',
    assigned_to UUID,
    due_date DATE,
    start_date DATE,
    completed_date DATE,
    estimated_hours NUMERIC(10, 2),
    actual_hours NUMERIC(10, 2),
    completion_percentage INTEGER DEFAULT 0 CHECK (completion_percentage >= 0 AND completion_percentage <= 100),
    parent_task_id UUID,
    sequence INTEGER NOT NULL DEFAULT 0,
    tags TEXT,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT fk_task_phase FOREIGN KEY (phase_id) REFERENCES construction_phases(id) ON DELETE CASCADE,
    CONSTRAINT fk_task_assigned FOREIGN KEY (assigned_to) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_task_parent FOREIGN KEY (parent_task_id) REFERENCES project_tasks(id) ON DELETE CASCADE,
    CONSTRAINT chk_task_status CHECK (status IN ('TODO', 'IN_PROGRESS', 'IN_REVIEW', 'COMPLETED', 'BLOCKED', 'CANCELLED')),
    CONSTRAINT chk_task_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT'))
);

-- Project Team Members table
CREATE TABLE IF NOT EXISTS project_team_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(100) NOT NULL DEFAULT 'TEAM_MEMBER',
    phase_id UUID,
    assigned_date DATE NOT NULL DEFAULT CURRENT_DATE,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT fk_team_project FOREIGN KEY (project_id) REFERENCES construction_projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_team_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_team_phase FOREIGN KEY (phase_id) REFERENCES construction_phases(id) ON DELETE SET NULL,
    CONSTRAINT chk_team_role CHECK (role IN ('PROJECT_MANAGER', 'SITE_MANAGER', 'FOREMAN', 'ENGINEER', 'ARCHITECT', 'SUPERVISOR', 'WORKER', 'TEAM_MEMBER', 'CONSULTANT')),
    CONSTRAINT chk_team_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'REMOVED')),
    CONSTRAINT unique_project_user UNIQUE (project_id, user_id)
);

-- Project Milestones table
CREATE TABLE IF NOT EXISTS project_milestones (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    target_date DATE NOT NULL,
    actual_date DATE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    phase_id UUID,
    is_critical BOOLEAN NOT NULL DEFAULT FALSE,
    completion_percentage INTEGER DEFAULT 0 CHECK (completion_percentage >= 0 AND completion_percentage <= 100),
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT fk_milestone_project FOREIGN KEY (project_id) REFERENCES construction_projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_milestone_phase FOREIGN KEY (phase_id) REFERENCES construction_phases(id) ON DELETE SET NULL,
    CONSTRAINT chk_milestone_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'ACHIEVED', 'MISSED', 'CANCELLED'))
);

-- Project Documents table
CREATE TABLE IF NOT EXISTS project_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    phase_id UUID,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    file_name VARCHAR(255) NOT NULL,
    file_data BYTEA,
    content_type VARCHAR(100),
    file_size BIGINT,
    file_url VARCHAR(500),
    document_type VARCHAR(100) NOT NULL DEFAULT 'OTHER',
    uploaded_by UUID NOT NULL,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version_number INTEGER DEFAULT 1,
    is_latest BOOLEAN NOT NULL DEFAULT TRUE,
    tags TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT fk_doc_project FOREIGN KEY (project_id) REFERENCES construction_projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_doc_phase FOREIGN KEY (phase_id) REFERENCES construction_phases(id) ON DELETE SET NULL,
    CONSTRAINT fk_doc_uploader FOREIGN KEY (uploaded_by) REFERENCES users(id),
    CONSTRAINT chk_doc_type CHECK (document_type IN ('PLAN', 'DRAWING', 'PERMIT', 'CONTRACT', 'INVOICE', 'REPORT', 'PHOTO', 'VIDEO', 'OTHER'))
);

-- Project Issues/Risks table
CREATE TABLE IF NOT EXISTS project_issues (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    phase_id UUID,
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    type VARCHAR(50) NOT NULL DEFAULT 'ISSUE',
    severity VARCHAR(50) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    priority VARCHAR(50) NOT NULL DEFAULT 'MEDIUM',
    reported_by UUID NOT NULL,
    assigned_to UUID,
    due_date DATE,
    resolved_date DATE,
    resolution TEXT,
    impact TEXT,
    mitigation_plan TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT fk_issue_project FOREIGN KEY (project_id) REFERENCES construction_projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_issue_phase FOREIGN KEY (phase_id) REFERENCES construction_phases(id) ON DELETE SET NULL,
    CONSTRAINT fk_issue_reporter FOREIGN KEY (reported_by) REFERENCES users(id),
    CONSTRAINT fk_issue_assigned FOREIGN KEY (assigned_to) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT chk_issue_type CHECK (type IN ('ISSUE', 'RISK', 'BLOCKER', 'BUG', 'CHANGE_REQUEST')),
    CONSTRAINT chk_issue_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT chk_issue_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'CANCELLED')),
    CONSTRAINT chk_issue_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT'))
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_project_tasks_phase ON project_tasks(phase_id);
CREATE INDEX IF NOT EXISTS idx_project_tasks_assigned ON project_tasks(assigned_to);
CREATE INDEX IF NOT EXISTS idx_project_tasks_status ON project_tasks(status);
CREATE INDEX IF NOT EXISTS idx_project_tasks_parent ON project_tasks(parent_task_id);
CREATE INDEX IF NOT EXISTS idx_project_tasks_due_date ON project_tasks(due_date);

CREATE INDEX IF NOT EXISTS idx_project_team_project ON project_team_members(project_id);
CREATE INDEX IF NOT EXISTS idx_project_team_user ON project_team_members(user_id);
CREATE INDEX IF NOT EXISTS idx_project_team_phase ON project_team_members(phase_id);
CREATE INDEX IF NOT EXISTS idx_project_team_status ON project_team_members(status);

CREATE INDEX IF NOT EXISTS idx_project_milestones_project ON project_milestones(project_id);
CREATE INDEX IF NOT EXISTS idx_project_milestones_phase ON project_milestones(phase_id);
CREATE INDEX IF NOT EXISTS idx_project_milestones_status ON project_milestones(status);
CREATE INDEX IF NOT EXISTS idx_project_milestones_target_date ON project_milestones(target_date);

CREATE INDEX IF NOT EXISTS idx_project_documents_project ON project_documents(project_id);
CREATE INDEX IF NOT EXISTS idx_project_documents_phase ON project_documents(phase_id);
CREATE INDEX IF NOT EXISTS idx_project_documents_type ON project_documents(document_type);
CREATE INDEX IF NOT EXISTS idx_project_documents_latest ON project_documents(project_id, is_latest);

CREATE INDEX IF NOT EXISTS idx_project_issues_project ON project_issues(project_id);
CREATE INDEX IF NOT EXISTS idx_project_issues_phase ON project_issues(phase_id);
CREATE INDEX IF NOT EXISTS idx_project_issues_status ON project_issues(status);
CREATE INDEX IF NOT EXISTS idx_project_issues_assigned ON project_issues(assigned_to);
CREATE INDEX IF NOT EXISTS idx_project_issues_severity ON project_issues(severity);
