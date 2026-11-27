ALTER TABLE users
  ADD COLUMN IF NOT EXISTS last_seen_at timestamptz NOT NULL DEFAULT now();

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_active_guest_device
  ON users (device_fingerprint)
  WHERE is_guest = true
    AND merged_into IS NULL
    AND device_fingerprint IS NOT NULL;
