CREATE TABLE IF NOT EXISTS "followed_artists" (
	"user_id" uuid NOT NULL REFERENCES "users"("id") ON DELETE cascade,
	"artist_id" text NOT NULL,
	"artist_name" text NOT NULL,
	"thumbnail_url" text,
	"followed_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "followed_artists_user_id_artist_id_pk" PRIMARY KEY("user_id","artist_id")
);
CREATE INDEX IF NOT EXISTS "idx_followed_artists_user" ON "followed_artists" ("user_id","followed_at");

