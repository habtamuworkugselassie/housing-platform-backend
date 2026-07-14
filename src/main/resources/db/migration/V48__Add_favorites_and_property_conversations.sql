CREATE TABLE favorite_properties (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    property_id UUID NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT,
    CONSTRAINT uq_favorite_properties_user_property UNIQUE (user_id, property_id)
);

CREATE INDEX idx_favorite_properties_user_created
    ON favorite_properties(user_id, created_at DESC);

CREATE TABLE property_conversations (
    id UUID PRIMARY KEY,
    buyer_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    agent_id UUID NOT NULL REFERENCES real_estate_agents(id) ON DELETE CASCADE,
    property_id UUID NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT,
    CONSTRAINT uq_property_conversations_buyer_property UNIQUE (buyer_user_id, property_id)
);

CREATE INDEX idx_property_conversations_buyer_updated
    ON property_conversations(buyer_user_id, updated_at DESC);
CREATE INDEX idx_property_conversations_agent_updated
    ON property_conversations(agent_id, updated_at DESC);

CREATE TABLE property_messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL REFERENCES property_conversations(id) ON DELETE CASCADE,
    sender_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    version BIGINT
);

CREATE INDEX idx_property_messages_conversation_created
    ON property_messages(conversation_id, created_at ASC);
