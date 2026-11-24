DROP TABLE IF EXISTS password_reset_tokens CASCADE;

ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMPTZ;
UPDATE users SET email_verified_at = created_at WHERE email_verified_at IS NULL;

CREATE TABLE IF NOT EXISTS signup_verification_otps (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email TEXT NOT NULL,
  username TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  display_name TEXT NOT NULL,
  otp_hash TEXT NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  expires_at TIMESTAMPTZ NOT NULL,
  used_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_signup_otps_email ON signup_verification_otps(email);
CREATE INDEX IF NOT EXISTS idx_signup_otps_expires ON signup_verification_otps(expires_at);

ALTER TABLE signup_verification_otps ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE tablename = 'signup_verification_otps' AND policyname = 'signup_verification_otps_auth_role_all'
  ) THEN
    CREATE POLICY signup_verification_otps_auth_role_all ON signup_verification_otps
      FOR ALL
      TO auth_role
      USING (true)
      WITH CHECK (true);
  END IF;
END $$;

GRANT ALL ON signup_verification_otps TO auth_role;
DO $$
BEGIN
  GRANT ALL ON signup_verification_otps TO postgres;
EXCEPTION WHEN OTHERS THEN
  NULL;
END $$;
REVOKE ALL ON signup_verification_otps FROM app_user;

CREATE TABLE IF NOT EXISTS password_reset_otps (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID REFERENCES users(id) ON DELETE CASCADE,
  email TEXT NOT NULL,
  otp_hash TEXT NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  expires_at TIMESTAMPTZ NOT NULL,
  used_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_reset_otps_user ON password_reset_otps(user_id);
CREATE INDEX IF NOT EXISTS idx_reset_otps_email ON password_reset_otps(email);
CREATE INDEX IF NOT EXISTS idx_reset_otps_expires ON password_reset_otps(expires_at);

ALTER TABLE password_reset_otps ENABLE ROW LEVEL SECURITY;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies WHERE tablename = 'password_reset_otps' AND policyname = 'password_reset_otps_auth_role_all'
  ) THEN
    CREATE POLICY password_reset_otps_auth_role_all ON password_reset_otps
      FOR ALL
      TO auth_role
      USING (true)
      WITH CHECK (true);
  END IF;
END $$;

GRANT ALL ON password_reset_otps TO auth_role;
DO $$
BEGIN
  GRANT ALL ON password_reset_otps TO postgres;
EXCEPTION WHEN OTHERS THEN
  NULL;
END $$;
REVOKE ALL ON password_reset_otps FROM app_user;
