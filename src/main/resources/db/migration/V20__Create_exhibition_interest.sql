-- Exhibition interest: capture "Register your interest" form submissions (exhibitor/visitor)
CREATE TABLE IF NOT EXISTS exhibition_interest (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    interest_type VARCHAR(50) NOT NULL,
    company VARCHAR(500),
    message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_exhibition_interest_email ON exhibition_interest(email);
CREATE INDEX idx_exhibition_interest_created_at ON exhibition_interest(created_at);
CREATE INDEX idx_exhibition_interest_interest_type ON exhibition_interest(interest_type);
