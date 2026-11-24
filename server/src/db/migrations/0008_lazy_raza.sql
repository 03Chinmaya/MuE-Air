CREATE TABLE "chat_push_hints_sent" (
	"message_id" uuid NOT NULL,
	"device_id" uuid NOT NULL,
	"sent_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "chat_push_hints_sent_message_id_device_id_pk" PRIMARY KEY("message_id","device_id")
);
--> statement-breakpoint
CREATE TABLE "imported_playlist_tracks" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"imported_playlist_id" uuid NOT NULL,
	"position" integer NOT NULL,
	"external_video_id" text NOT NULL,
	"title" text NOT NULL,
	"artists" text[] NOT NULL,
	"duration_ms" integer,
	"thumbnail_url" text,
	"jiosaavn_track_id" text,
	"match_confidence" numeric,
	"match_status" text DEFAULT 'pending' NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "imported_playlists" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid NOT NULL,
	"provider" text NOT NULL,
	"external_id" text NOT NULL,
	"title" text NOT NULL,
	"description" text,
	"thumbnail_url" text,
	"track_count" integer DEFAULT 0 NOT NULL,
	"matched_count" integer DEFAULT 0 NOT NULL,
	"last_synced_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "listen_queue_items" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"session_id" uuid NOT NULL,
	"track_id" text NOT NULL,
	"provider" text NOT NULL,
	"title" text NOT NULL,
	"artist" text,
	"artwork_url" text,
	"added_by_user_id" uuid NOT NULL,
	"position" integer NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "password_reset_otps" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid,
	"email" text NOT NULL,
	"otp_hash" text NOT NULL,
	"attempts" integer DEFAULT 0 NOT NULL,
	"expires_at" timestamp with time zone NOT NULL,
	"used_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "search_docs" (
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
);
--> statement-breakpoint
CREATE TABLE "search_queries" (
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
);
--> statement-breakpoint
CREATE TABLE "signup_verification_otps" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"email" text NOT NULL,
	"username" text NOT NULL,
	"password_hash" text NOT NULL,
	"display_name" text NOT NULL,
	"otp_hash" text NOT NULL,
	"attempts" integer DEFAULT 0 NOT NULL,
	"expires_at" timestamp with time zone NOT NULL,
	"used_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "track_similarity" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"track_id" text NOT NULL,
	"similar_track_id" text NOT NULL,
	"score" numeric(5, 4) NOT NULL,
	"reason" text NOT NULL,
	"computed_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "user_music_integrations" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid NOT NULL,
	"provider" text NOT NULL,
	"encrypted_refresh_token" text NOT NULL,
	"email" text,
	"status" text DEFAULT 'connected' NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
ALTER TABLE "chat_conversations" ADD COLUMN "disappearing_ttl_ms" bigint DEFAULT 0 NOT NULL;--> statement-breakpoint
ALTER TABLE "chat_conversations" ADD COLUMN "ttl_set_by_user_id" uuid;--> statement-breakpoint
ALTER TABLE "chat_conversations" ADD COLUMN "ttl_set_at" timestamp with time zone;--> statement-breakpoint
ALTER TABLE "listen_sessions" ADD COLUMN "control_mode" text DEFAULT 'open' NOT NULL;--> statement-breakpoint
ALTER TABLE "users" ADD COLUMN "email_verified_at" timestamp with time zone;--> statement-breakpoint
ALTER TABLE "users" ADD COLUMN "terms_accepted_at" timestamp with time zone;--> statement-breakpoint
ALTER TABLE "users" ADD COLUMN "terms_version" text;--> statement-breakpoint
ALTER TABLE "users" ADD COLUMN "username_changed_at" timestamp with time zone;--> statement-breakpoint
ALTER TABLE "users" ADD COLUMN "username_change_count" integer DEFAULT 0;--> statement-breakpoint
ALTER TABLE "imported_playlist_tracks" ADD CONSTRAINT "imported_playlist_tracks_imported_playlist_id_imported_playlists_id_fk" FOREIGN KEY ("imported_playlist_id") REFERENCES "public"."imported_playlists"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "imported_playlists" ADD CONSTRAINT "imported_playlists_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "listen_queue_items" ADD CONSTRAINT "listen_queue_items_session_id_listen_sessions_id_fk" FOREIGN KEY ("session_id") REFERENCES "public"."listen_sessions"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "listen_queue_items" ADD CONSTRAINT "listen_queue_items_added_by_user_id_users_id_fk" FOREIGN KEY ("added_by_user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "password_reset_otps" ADD CONSTRAINT "password_reset_otps_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "user_music_integrations" ADD CONSTRAINT "user_music_integrations_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
CREATE INDEX "idx_push_hints_sent_at" ON "chat_push_hints_sent" USING btree ("sent_at");--> statement-breakpoint
CREATE INDEX "idx_playlist_pos" ON "imported_playlist_tracks" USING btree ("imported_playlist_id","position");--> statement-breakpoint
CREATE UNIQUE INDEX "uniq_user_provider_ext" ON "imported_playlists" USING btree ("user_id","provider","external_id");--> statement-breakpoint
CREATE INDEX "idx_listen_queue_session" ON "listen_queue_items" USING btree ("session_id","position");--> statement-breakpoint
CREATE INDEX "idx_reset_otps_user" ON "password_reset_otps" USING btree ("user_id");--> statement-breakpoint
CREATE INDEX "idx_reset_otps_email" ON "password_reset_otps" USING btree ("email");--> statement-breakpoint
CREATE INDEX "idx_reset_otps_expires" ON "password_reset_otps" USING btree ("expires_at");--> statement-breakpoint
CREATE UNIQUE INDEX "idx_search_docs_unique" ON "search_docs" USING btree ("entity_type","entity_id","provider");--> statement-breakpoint
CREATE INDEX "idx_search_queries_norm" ON "search_queries" USING btree ("normalized_query");--> statement-breakpoint
CREATE INDEX "idx_search_queries_created" ON "search_queries" USING btree ("created_at");--> statement-breakpoint
CREATE INDEX "idx_signup_otps_email" ON "signup_verification_otps" USING btree ("email");--> statement-breakpoint
CREATE INDEX "idx_signup_otps_expires" ON "signup_verification_otps" USING btree ("expires_at");--> statement-breakpoint
CREATE UNIQUE INDEX "uniq_user_provider" ON "user_music_integrations" USING btree ("user_id","provider");--> statement-breakpoint
ALTER TABLE "chat_conversations" ADD CONSTRAINT "chat_conversations_ttl_set_by_user_id_users_id_fk" FOREIGN KEY ("ttl_set_by_user_id") REFERENCES "public"."users"("id") ON DELETE set null ON UPDATE no action;