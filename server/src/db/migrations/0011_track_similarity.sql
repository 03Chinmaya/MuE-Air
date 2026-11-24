CREATE TABLE IF NOT EXISTS track_similarity (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  track_id TEXT NOT NULL,
  similar_track_id TEXT NOT NULL,
  score NUMERIC(5,4) NOT NULL,
  reason TEXT NOT NULL,          -- 'same_artist' | 'genre_mood' | 'co_playlist' | 'collaborative' | 'popular' | 'affinity'
  computed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_similarity_pair ON track_similarity(track_id, similar_track_id);
CREATE INDEX IF NOT EXISTS idx_similarity_source ON track_similarity(track_id, score DESC);
CREATE INDEX IF NOT EXISTS idx_similarity_stale ON track_similarity(computed_at);

ALTER TABLE track_similarity ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS similarity_read ON track_similarity;
DROP POLICY IF EXISTS similarity_all ON track_similarity;
CREATE POLICY similarity_read ON track_similarity FOR SELECT TO app_user USING (true);
CREATE POLICY similarity_all ON track_similarity FOR ALL TO auth_role USING (true) WITH CHECK (true);
REVOKE ALL ON track_similarity FROM app_user;
GRANT SELECT ON track_similarity TO app_user;
