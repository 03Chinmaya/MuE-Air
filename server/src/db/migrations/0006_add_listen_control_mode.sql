ALTER TABLE listen_sessions ADD COLUMN IF NOT EXISTS control_mode TEXT NOT NULL DEFAULT 'open';
