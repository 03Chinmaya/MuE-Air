CREATE TABLE IF NOT EXISTS "listen_queue_items" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "session_id" uuid NOT NULL REFERENCES "listen_sessions"("id") ON DELETE CASCADE,
  "track_id" text NOT NULL,
  "provider" text NOT NULL,
  "title" text NOT NULL,
  "artist" text,
  "artwork_url" text,
  "added_by_user_id" uuid NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
  "position" integer NOT NULL,
  "created_at" timestamp with time zone DEFAULT now() NOT NULL
);

CREATE INDEX IF NOT EXISTS "idx_listen_queue_session" ON "listen_queue_items" ("session_id", "position");

GRANT SELECT, INSERT, UPDATE, DELETE ON "listen_queue_items" TO app_user;

