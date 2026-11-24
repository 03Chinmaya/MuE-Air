-- 0012_search_docs.sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE IF NOT EXISTS search_docs (
  id BIGSERIAL PRIMARY KEY,
  entity_type TEXT NOT NULL,     -- track | artist | album | playlist
  entity_id UUID NOT NULL,
  provider TEXT NOT NULL,        -- jiosaavn (ytmusic allowed for metadata but NOT for playback)
  title TEXT NOT NULL,
  subtitle TEXT,
  keywords TEXT,
  aliases TEXT,
  popularity INT NOT NULL DEFAULT 0,
  available_for_playback BOOLEAN NOT NULL DEFAULT true,  -- false if no JioSaavn match
  tsv TSVECTOR GENERATED ALWAYS AS (
    setweight(to_tsvector('simple', coalesce(title,'')), 'A') ||
    setweight(to_tsvector('simple', coalesce(aliases,'')), 'A') ||
    setweight(to_tsvector('simple', coalesce(subtitle,'')), 'B') ||
    setweight(to_tsvector('simple', coalesce(keywords,'')), 'C')
  ) STORED
);
CREATE INDEX IF NOT EXISTS idx_search_docs_tsv ON search_docs USING gin(tsv);
CREATE INDEX IF NOT EXISTS idx_search_docs_trgm ON search_docs USING gin(title gin_trgm_ops);
CREATE UNIQUE INDEX IF NOT EXISTS idx_search_docs_unique ON search_docs(entity_type, entity_id, provider);

CREATE TABLE IF NOT EXISTS search_queries (
  id BIGSERIAL PRIMARY KEY,
  user_id UUID,
  normalized_query TEXT NOT NULL,
  result_count INT,
  took_ms INT,
  local_count INT,
  remote_count INT,
  clicked_entity_type TEXT,
  clicked_entity_id UUID,
  clicked_position INT,
  clicked_provider TEXT,
  vibe_opened BOOLEAN DEFAULT false,
  created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_search_queries_norm ON search_queries(normalized_query);
CREATE INDEX IF NOT EXISTS idx_search_queries_created ON search_queries(created_at);
