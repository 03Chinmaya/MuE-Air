CREATE TABLE IF NOT EXISTS "search_docs" (
	"id" bigserial PRIMARY KEY NOT NULL,
	"entity_type" text NOT NULL,
	"entity_id" uuid NOT NULL,
	"provider" text NOT NULL,
	"title" text NOT NULL,
	"subtitle" text,
	"keywords" text,
	"aliases" text,
	"popularity" integer DEFAULT 0 NOT NULL,
	"available_for_playback" boolean DEFAULT true NOT NULL
);--> statement-breakpoint
CREATE UNIQUE INDEX IF NOT EXISTS "idx_search_docs_unique" ON "search_docs" USING btree ("entity_type","entity_id","provider");--> statement-breakpoint
CREATE TABLE IF NOT EXISTS "search_queries" (
	"id" bigserial PRIMARY KEY NOT NULL,
	"user_id" uuid,
	"normalized_query" text NOT NULL,
	"result_count" integer,
	"took_ms" integer,
	"local_count" integer,
	"remote_count" integer,
	"clicked_entity_type" text,
	"clicked_entity_id" uuid,
	"clicked_position" integer,
	"clicked_provider" text,
	"vibe_opened" boolean DEFAULT false,
	"created_at" timestamp with time zone DEFAULT now()
);--> statement-breakpoint
CREATE INDEX IF NOT EXISTS "idx_search_queries_norm" ON "search_queries" USING btree ("normalized_query");--> statement-breakpoint
CREATE INDEX IF NOT EXISTS "idx_search_queries_created" ON "search_queries" USING btree ("created_at");
