CREATE TABLE IF NOT EXISTS exhibition_video_feedback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submitter_name VARCHAR(120) NOT NULL,
    submitter_email VARCHAR(255) NOT NULL,
    submitter_role VARCHAR(20) NOT NULL DEFAULT 'VISITOR',
    company_name VARCHAR(255),
    caption TEXT,
    video_url VARCHAR(512) NOT NULL,
    content_type VARCHAR(100),
    submitter_ip VARCHAR(64),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_exhibition_video_feedback_status ON exhibition_video_feedback(status);
CREATE INDEX idx_exhibition_video_feedback_created_at ON exhibition_video_feedback(created_at);
CREATE INDEX idx_exhibition_video_feedback_submitter_ip ON exhibition_video_feedback(submitter_ip);
