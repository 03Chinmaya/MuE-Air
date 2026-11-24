-- 0013_music_integrations.sql
CREATE TABLE IF NOT EXISTS user_music_integrations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  provider TEXT NOT NULL,
  encrypted_refresh_token TEXT NOT NULL,
  email TEXT,
  status TEXT NOT NULL DEFAULT 'connected',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_user_provider ON user_music_integrations(user_id, provider);

CREATE TABLE IF NOT EXISTS imported_playlists (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  provider TEXT NOT NULL,
  external_id TEXT NOT NULL,
  title TEXT NOT NULL,
  description TEXT,
  thumbnail_url TEXT,
  track_count INT NOT NULL DEFAULT 0,
  matched_count INT NOT NULL DEFAULT 0,
  last_synced_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_user_provider_ext ON imported_playlists(user_id, provider, external_id);

CREATE TABLE IF NOT EXISTS imported_playlist_tracks (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  imported_playlist_id UUID NOT NULL REFERENCES imported_playlists(id) ON DELETE CASCADE,
  position INT NOT NULL,
  external_video_id TEXT NOT NULL,
  title TEXT NOT NULL,
  artists TEXT[] NOT NULL,
  duration_ms INT,
  thumbnail_url TEXT,
  jiosaavn_track_id TEXT,
  match_confidence NUMERIC,
  match_status TEXT NOT NULL DEFAULT 'pending',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_playlist_pos ON imported_playlist_tracks(imported_playlist_id, position);

