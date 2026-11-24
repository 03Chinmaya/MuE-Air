CREATE TABLE IF NOT EXISTS password_reset_tokens (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash TEXT NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  used_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_reset_tokens_user ON password_reset_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_reset_tokens_hash ON password_reset_tokens(token_hash);

-- RLS: auth_role only (no app_user access)
ALTER TABLE password_reset_tokens ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE tablename = 'password_reset_tokens' AND policyname = 'password_reset_tokens_auth_role_all'
  ) THEN
    CREATE POLICY password_reset_tokens_auth_role_all ON password_reset_tokens
      FOR ALL
      TO auth_role
      USING (true)
      WITH CHECK (true);
  END IF;
END $$;

GRANT ALL ON password_reset_tokens TO auth_role;
DO $$
BEGIN
  GRANT ALL ON password_reset_tokens TO postgres;
EXCEPTION WHEN OTHERS THEN
  NULL;
END $$;
REVOKE ALL ON password_reset_tokens FROM app_user;
