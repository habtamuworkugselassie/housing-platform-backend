CREATE TABLE reviews
(
    id             UUID PRIMARY KEY      DEFAULT gen_random_uuid(),
    property_id    UUID REFERENCES properties (id) ON DELETE CASCADE,
    user_id        UUID REFERENCES users(id) ON DELETE CASCADE,
    rating         INTEGER      NOT NULL,
    comment        TEXT,
    created_at     TIMESTAMP,
    updated_at     TIMESTAMP,
    created_by     VARCHAR(255),
    updated_by     VARCHAR(255),
    version        BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT fk_reviews_property FOREIGN KEY (property_id) REFERENCES properties (id)
);

CREATE INDEX idx_reviews_property_id ON reviews (property_id);

-- Add user_id column to media_attachments table
ALTER TABLE media_attachments
    ADD COLUMN IF NOT EXISTS user_id UUID REFERENCES users(id) ON DELETE CASCADE;
