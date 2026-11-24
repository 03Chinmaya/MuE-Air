ALTER TABLE chat_conversations
  ADD COLUMN IF NOT EXISTS disappearing_ttl_ms BIGINT NOT NULL DEFAULT 0;
ALTER TABLE chat_conversations
  ADD COLUMN IF NOT EXISTS ttl_set_by_user_id UUID REFERENCES users(id) ON DELETE SET NULL;
ALTER TABLE chat_conversations
  ADD COLUMN IF NOT EXISTS ttl_set_at TIMESTAMPTZ;
