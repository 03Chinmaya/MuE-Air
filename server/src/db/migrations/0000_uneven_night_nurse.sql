CREATE TABLE "analytics_events" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid,
	"event_type" text NOT NULL,
	"event_data" jsonb,
	"timestamp" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "artist_play_stats" (
	"user_id" uuid NOT NULL,
	"artist_name" text NOT NULL,
	"play_count" integer DEFAULT 0,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "artist_play_stats_user_id_artist_name_pk" PRIMARY KEY("user_id","artist_name")
);
--> statement-breakpoint
CREATE TABLE "blocks" (
	"blocker_id" uuid NOT NULL,
	"blocked_id" uuid NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "blocks_blocker_id_blocked_id_pk" PRIMARY KEY("blocker_id","blocked_id")
);
--> statement-breakpoint
CREATE TABLE "chat_attachments" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"message_id" uuid NOT NULL,
	"r2_key" text NOT NULL,
	"file_size" integer NOT NULL,
	"content_type" text,
	"encryption_algorithm" text NOT NULL,
	"encryption_key_ciphertext" text NOT NULL,
	"encryption_key_nonce" text NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "chat_conversations" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"type" text NOT NULL,
	"title" text,
	"artwork_url" text,
	"created_by" uuid,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL,
	"last_message_at" timestamp with time zone
);
--> statement-breakpoint
CREATE TABLE "chat_message_reads" (
	"message_id" uuid NOT NULL,
	"user_id" uuid NOT NULL,
	"read_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "chat_message_reads_message_id_user_id_pk" PRIMARY KEY("message_id","user_id")
);
--> statement-breakpoint
CREATE TABLE "chat_messages" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"conversation_id" uuid NOT NULL,
	"sender_id" uuid NOT NULL,
	"sender_device_id" uuid,
	"content_ciphertext" text NOT NULL,
	"content_nonce" text NOT NULL,
	"content_algorithm" text DEFAULT 'xchacha20-poly1305' NOT NULL,
	"content_type" text DEFAULT 'text' NOT NULL,
	"metadata_ciphertext" text,
	"metadata_nonce" text,
	"reply_to_id" uuid,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"edited_at" timestamp with time zone,
	"deleted_at" timestamp with time zone
);
--> statement-breakpoint
CREATE TABLE "chat_participants" (
	"conversation_id" uuid NOT NULL,
	"user_id" uuid NOT NULL,
	"role" text DEFAULT 'member',
	"joined_at" timestamp with time zone DEFAULT now() NOT NULL,
	"last_read_at" timestamp with time zone,
	"muted" boolean DEFAULT false,
	CONSTRAINT "chat_participants_conversation_id_user_id_pk" PRIMARY KEY("conversation_id","user_id")
);
--> statement-breakpoint
CREATE TABLE "chat_reactions" (
	"message_id" uuid NOT NULL,
	"user_id" uuid NOT NULL,
	"emoji" text NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "chat_reactions_message_id_user_id_emoji_pk" PRIMARY KEY("message_id","user_id","emoji")
);
--> statement-breakpoint
CREATE TABLE "chat_reports" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"reporter_id" uuid NOT NULL,
	"reported_user_id" uuid,
	"message_id" uuid,
	"reason" text NOT NULL,
	"status" text DEFAULT 'pending',
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "e2e_identity_keys" (
	"user_id" uuid NOT NULL,
	"device_id" uuid NOT NULL,
	"public_key" text NOT NULL,
	"registration_id" integer NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "e2e_identity_keys_user_id_device_id_pk" PRIMARY KEY("user_id","device_id")
);
--> statement-breakpoint
CREATE TABLE "e2e_one_time_prekeys" (
	"user_id" uuid NOT NULL,
	"device_id" uuid NOT NULL,
	"key_id" integer NOT NULL,
	"public_key" text NOT NULL,
	"used" boolean DEFAULT false,
	"used_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "e2e_one_time_prekeys_user_id_device_id_key_id_pk" PRIMARY KEY("user_id","device_id","key_id")
);
--> statement-breakpoint
CREATE TABLE "e2e_sender_key_distributions" (
	"conversation_id" uuid NOT NULL,
	"sender_user_id" uuid NOT NULL,
	"sender_device_id" uuid NOT NULL,
	"recipient_user_id" uuid NOT NULL,
	"recipient_device_id" uuid NOT NULL,
	"distribution_ciphertext" text NOT NULL,
	"distribution_nonce" text NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "e2e_sender_key_distributions_conversation_id_sender_user_id_sender_device_id_recipient_user_id_recipient_device_id_pk" PRIMARY KEY("conversation_id","sender_user_id","sender_device_id","recipient_user_id","recipient_device_id")
);
--> statement-breakpoint
CREATE TABLE "e2e_signed_prekeys" (
	"user_id" uuid NOT NULL,
	"device_id" uuid NOT NULL,
	"key_id" integer NOT NULL,
	"public_key" text NOT NULL,
	"signature" text NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "e2e_signed_prekeys_user_id_device_id_key_id_pk" PRIMARY KEY("user_id","device_id","key_id")
);
--> statement-breakpoint
CREATE TABLE "follow_requests" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"from_user_id" uuid NOT NULL,
	"to_user_id" uuid NOT NULL,
	"status" text DEFAULT 'pending' NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"expires_at" timestamp with time zone NOT NULL
);
--> statement-breakpoint
CREATE TABLE "follows" (
	"follower_id" uuid NOT NULL,
	"followee_id" uuid NOT NULL,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "follows_follower_id_followee_id_pk" PRIMARY KEY("follower_id","followee_id")
);
--> statement-breakpoint
CREATE TABLE "genre_play_stats" (
	"user_id" uuid NOT NULL,
	"genre" text NOT NULL,
	"play_count" integer DEFAULT 0,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "genre_play_stats_user_id_genre_pk" PRIMARY KEY("user_id","genre")
);
--> statement-breakpoint
CREATE TABLE "handoff_sessions" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid NOT NULL,
	"source_device_id" uuid NOT NULL,
	"target_device_id" uuid,
	"status" text NOT NULL,
	"playback_state" jsonb,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL,
	"expires_at" timestamp with time zone
);
--> statement-breakpoint
CREATE TABLE "listen_participants" (
	"session_id" uuid NOT NULL,
	"user_id" uuid NOT NULL,
	"joined_at" timestamp with time zone DEFAULT now() NOT NULL,
	"left_at" timestamp with time zone,
	CONSTRAINT "listen_participants_session_id_user_id_pk" PRIMARY KEY("session_id","user_id")
);
--> statement-breakpoint
CREATE TABLE "listen_sessions" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"host_user_id" uuid NOT NULL,
	"current_provider" text,
	"current_provider_track_id" text,
	"position_ms" integer DEFAULT 0,
	"playing" boolean DEFAULT false,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL,
	"ended_at" timestamp with time zone
);
--> statement-breakpoint
CREATE TABLE "notifications" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid NOT NULL,
	"type" text NOT NULL,
	"actor_id" uuid,
	"target_id" uuid,
	"preview_ciphertext" text,
	"preview_nonce" text,
	"read" boolean DEFAULT false,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "song_play_stats" (
	"user_id" uuid NOT NULL,
	"provider" text NOT NULL,
	"provider_track_id" text NOT NULL,
	"play_count" integer DEFAULT 0,
	"completion_count" integer DEFAULT 0,
	"skip_count" integer DEFAULT 0,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "song_play_stats_user_id_provider_provider_track_id_pk" PRIMARY KEY("user_id","provider","provider_track_id")
);
--> statement-breakpoint
CREATE TABLE "sync_operations" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid NOT NULL,
	"type" text NOT NULL,
	"entity_type" text NOT NULL,
	"entity_id" text NOT NULL,
	"payload" jsonb,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"processed_at" timestamp with time zone,
	"status" text DEFAULT 'pending'
);
--> statement-breakpoint
CREATE TABLE "user_devices" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid NOT NULL,
	"device_name" text NOT NULL,
	"platform" text DEFAULT 'android' NOT NULL,
	"push_token" text,
	"last_seen_at" timestamp with time zone,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "user_likes" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid NOT NULL,
	"provider" text NOT NULL,
	"provider_track_id" text NOT NULL,
	"title" text NOT NULL,
	"artist_name" text,
	"album_title" text,
	"artwork_url" text,
	"duration_ms" integer,
	"liked_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "user_play_history" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid NOT NULL,
	"provider" text NOT NULL,
	"provider_track_id" text NOT NULL,
	"title" text,
	"artist_name" text,
	"album_title" text,
	"played_at" timestamp with time zone DEFAULT now() NOT NULL,
	"duration_played_ms" integer,
	"completed" boolean DEFAULT false,
	"source" text
);
--> statement-breakpoint
CREATE TABLE "user_playlist_tracks" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"playlist_id" uuid NOT NULL,
	"position" integer NOT NULL,
	"provider" text NOT NULL,
	"provider_track_id" text NOT NULL,
	"title" text NOT NULL,
	"artist_name" text,
	"album_title" text,
	"artwork_url" text,
	"duration_ms" integer,
	"added_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "user_playlists" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid NOT NULL,
	"name" text NOT NULL,
	"description" text,
	"artwork_url" text,
	"visibility" text DEFAULT 'public',
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "user_preferences" (
	"user_id" uuid PRIMARY KEY NOT NULL,
	"favorite_genres" text[],
	"favorite_artists" text[],
	"preferred_languages" text[],
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "user_profiles" (
	"user_id" uuid PRIMARY KEY NOT NULL,
	"bio" text,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
CREATE TABLE "user_sessions" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"user_id" uuid NOT NULL,
	"device_id" uuid,
	"refresh_token_hash" text NOT NULL,
	"previous_token_hash" text,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"expires_at" timestamp with time zone NOT NULL,
	"last_used_at" timestamp with time zone,
	"revoked_at" timestamp with time zone
);
--> statement-breakpoint
CREATE TABLE "users" (
	"id" uuid PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
	"username" text NOT NULL,
	"email" text,
	"display_name" text NOT NULL,
	"avatar_url" text,
	"bio" text,
	"is_private" boolean DEFAULT false,
	"follower_count" integer DEFAULT 0,
	"following_count" integer DEFAULT 0,
	"password_hash" text,
	"pin_hash" text,
	"gender" text,
	"age" integer,
	"email_verified" boolean DEFAULT false,
	"two_factor_enabled" boolean DEFAULT false,
	"public_key" text,
	"registration_id" integer,
	"created_at" timestamp with time zone DEFAULT now() NOT NULL,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL,
	"last_login_at" timestamp with time zone,
	CONSTRAINT "users_username_unique" UNIQUE("username"),
	CONSTRAINT "users_email_unique" UNIQUE("email")
);
--> statement-breakpoint
ALTER TABLE "analytics_events" ADD CONSTRAINT "analytics_events_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE set null ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "artist_play_stats" ADD CONSTRAINT "artist_play_stats_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "blocks" ADD CONSTRAINT "blocks_blocker_id_users_id_fk" FOREIGN KEY ("blocker_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "blocks" ADD CONSTRAINT "blocks_blocked_id_users_id_fk" FOREIGN KEY ("blocked_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "chat_attachments" ADD CONSTRAINT "chat_attachments_message_id_chat_messages_id_fk" FOREIGN KEY ("message_id") REFERENCES "public"."chat_messages"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "chat_conversations" ADD CONSTRAINT "chat_conversations_created_by_users_id_fk" FOREIGN KEY ("created_by") REFERENCES "public"."users"("id") ON DELETE set null ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "chat_message_reads" ADD CONSTRAINT "chat_message_reads_message_id_chat_messages_id_fk" FOREIGN KEY ("message_id") REFERENCES "public"."chat_messages"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "chat_message_reads" ADD CONSTRAINT "chat_message_reads_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "chat_messages" ADD CONSTRAINT "chat_messages_conversation_id_chat_conversations_id_fk" FOREIGN KEY ("conversation_id") REFERENCES "public"."chat_conversations"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "chat_messages" ADD CONSTRAINT "chat_messages_sender_id_users_id_fk" FOREIGN KEY ("sender_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "chat_messages" ADD CONSTRAINT "chat_messages_sender_device_id_user_devices_id_fk" FOREIGN KEY ("sender_device_id") REFERENCES "public"."user_devices"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "chat_participants" ADD CONSTRAINT "chat_participants_conversation_id_chat_conversations_id_fk" FOREIGN KEY ("conversation_id") REFERENCES "public"."chat_conversations"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "chat_participants" ADD CONSTRAINT "chat_participants_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "chat_reactions" ADD CONSTRAINT "chat_reactions_message_id_chat_messages_id_fk" FOREIGN KEY ("message_id") REFERENCES "public"."chat_messages"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "chat_reactions" ADD CONSTRAINT "chat_reactions_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "chat_reports" ADD CONSTRAINT "chat_reports_reporter_id_users_id_fk" FOREIGN KEY ("reporter_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "chat_reports" ADD CONSTRAINT "chat_reports_reported_user_id_users_id_fk" FOREIGN KEY ("reported_user_id") REFERENCES "public"."users"("id") ON DELETE set null ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "chat_reports" ADD CONSTRAINT "chat_reports_message_id_chat_messages_id_fk" FOREIGN KEY ("message_id") REFERENCES "public"."chat_messages"("id") ON DELETE set null ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "e2e_identity_keys" ADD CONSTRAINT "e2e_identity_keys_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "e2e_identity_keys" ADD CONSTRAINT "e2e_identity_keys_device_id_user_devices_id_fk" FOREIGN KEY ("device_id") REFERENCES "public"."user_devices"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "e2e_one_time_prekeys" ADD CONSTRAINT "e2e_one_time_prekeys_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "e2e_one_time_prekeys" ADD CONSTRAINT "e2e_one_time_prekeys_device_id_user_devices_id_fk" FOREIGN KEY ("device_id") REFERENCES "public"."user_devices"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "e2e_sender_key_distributions" ADD CONSTRAINT "e2e_sender_key_distributions_conversation_id_chat_conversations_id_fk" FOREIGN KEY ("conversation_id") REFERENCES "public"."chat_conversations"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "e2e_sender_key_distributions" ADD CONSTRAINT "e2e_sender_key_distributions_sender_user_id_users_id_fk" FOREIGN KEY ("sender_user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "e2e_sender_key_distributions" ADD CONSTRAINT "e2e_sender_key_distributions_sender_device_id_user_devices_id_fk" FOREIGN KEY ("sender_device_id") REFERENCES "public"."user_devices"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "e2e_sender_key_distributions" ADD CONSTRAINT "e2e_sender_key_distributions_recipient_user_id_users_id_fk" FOREIGN KEY ("recipient_user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "e2e_sender_key_distributions" ADD CONSTRAINT "e2e_sender_key_distributions_recipient_device_id_user_devices_id_fk" FOREIGN KEY ("recipient_device_id") REFERENCES "public"."user_devices"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "e2e_signed_prekeys" ADD CONSTRAINT "e2e_signed_prekeys_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "e2e_signed_prekeys" ADD CONSTRAINT "e2e_signed_prekeys_device_id_user_devices_id_fk" FOREIGN KEY ("device_id") REFERENCES "public"."user_devices"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "follow_requests" ADD CONSTRAINT "follow_requests_from_user_id_users_id_fk" FOREIGN KEY ("from_user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "follow_requests" ADD CONSTRAINT "follow_requests_to_user_id_users_id_fk" FOREIGN KEY ("to_user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "follows" ADD CONSTRAINT "follows_follower_id_users_id_fk" FOREIGN KEY ("follower_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "follows" ADD CONSTRAINT "follows_followee_id_users_id_fk" FOREIGN KEY ("followee_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "genre_play_stats" ADD CONSTRAINT "genre_play_stats_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "handoff_sessions" ADD CONSTRAINT "handoff_sessions_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "handoff_sessions" ADD CONSTRAINT "handoff_sessions_source_device_id_user_devices_id_fk" FOREIGN KEY ("source_device_id") REFERENCES "public"."user_devices"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "handoff_sessions" ADD CONSTRAINT "handoff_sessions_target_device_id_user_devices_id_fk" FOREIGN KEY ("target_device_id") REFERENCES "public"."user_devices"("id") ON DELETE set null ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "listen_participants" ADD CONSTRAINT "listen_participants_session_id_listen_sessions_id_fk" FOREIGN KEY ("session_id") REFERENCES "public"."listen_sessions"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "listen_participants" ADD CONSTRAINT "listen_participants_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "listen_sessions" ADD CONSTRAINT "listen_sessions_host_user_id_users_id_fk" FOREIGN KEY ("host_user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "notifications" ADD CONSTRAINT "notifications_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "notifications" ADD CONSTRAINT "notifications_actor_id_users_id_fk" FOREIGN KEY ("actor_id") REFERENCES "public"."users"("id") ON DELETE set null ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "song_play_stats" ADD CONSTRAINT "song_play_stats_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "sync_operations" ADD CONSTRAINT "sync_operations_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "user_devices" ADD CONSTRAINT "user_devices_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "user_likes" ADD CONSTRAINT "user_likes_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "user_play_history" ADD CONSTRAINT "user_play_history_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "user_playlist_tracks" ADD CONSTRAINT "user_playlist_tracks_playlist_id_user_playlists_id_fk" FOREIGN KEY ("playlist_id") REFERENCES "public"."user_playlists"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "user_playlists" ADD CONSTRAINT "user_playlists_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "user_preferences" ADD CONSTRAINT "user_preferences_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "user_profiles" ADD CONSTRAINT "user_profiles_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "user_sessions" ADD CONSTRAINT "user_sessions_user_id_users_id_fk" FOREIGN KEY ("user_id") REFERENCES "public"."users"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "user_sessions" ADD CONSTRAINT "user_sessions_device_id_user_devices_id_fk" FOREIGN KEY ("device_id") REFERENCES "public"."user_devices"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
CREATE INDEX "idx_blocks_blocked" ON "blocks" USING btree ("blocked_id");--> statement-breakpoint
CREATE INDEX "idx_attach_message" ON "chat_attachments" USING btree ("message_id");--> statement-breakpoint
CREATE INDEX "idx_conv_last_message" ON "chat_conversations" USING btree ("last_message_at");--> statement-breakpoint
CREATE INDEX "idx_messages_conv" ON "chat_messages" USING btree ("conversation_id","created_at");--> statement-breakpoint
CREATE INDEX "idx_messages_sender" ON "chat_messages" USING btree ("sender_id");--> statement-breakpoint
CREATE INDEX "idx_participants_user" ON "chat_participants" USING btree ("user_id");--> statement-breakpoint
CREATE INDEX "idx_reports_status" ON "chat_reports" USING btree ("status","created_at");--> statement-breakpoint
CREATE INDEX "idx_prekeys_unused" ON "e2e_one_time_prekeys" USING btree ("user_id","device_id","used");--> statement-breakpoint
CREATE INDEX "idx_follow_requests_to" ON "follow_requests" USING btree ("to_user_id","status","created_at");--> statement-breakpoint
CREATE INDEX "idx_follow_requests_from" ON "follow_requests" USING btree ("from_user_id","status","created_at");--> statement-breakpoint
CREATE INDEX "idx_follows_followee" ON "follows" USING btree ("followee_id","created_at");--> statement-breakpoint
CREATE INDEX "idx_follows_follower" ON "follows" USING btree ("follower_id","created_at");--> statement-breakpoint
CREATE INDEX "idx_handoff_user" ON "handoff_sessions" USING btree ("user_id","created_at");--> statement-breakpoint
CREATE INDEX "idx_notifications_user" ON "notifications" USING btree ("user_id","created_at");--> statement-breakpoint
CREATE INDEX "idx_notifications_unread" ON "notifications" USING btree ("user_id","read");--> statement-breakpoint
CREATE INDEX "idx_sync_user" ON "sync_operations" USING btree ("user_id","created_at");--> statement-breakpoint
CREATE INDEX "idx_devices_user" ON "user_devices" USING btree ("user_id");--> statement-breakpoint
CREATE INDEX "idx_likes_user" ON "user_likes" USING btree ("user_id","liked_at");--> statement-breakpoint
CREATE INDEX "idx_history_user_time" ON "user_play_history" USING btree ("user_id","played_at");--> statement-breakpoint
CREATE INDEX "idx_playlist_tracks_playlist" ON "user_playlist_tracks" USING btree ("playlist_id","position");--> statement-breakpoint
CREATE INDEX "idx_playlists_user" ON "user_playlists" USING btree ("user_id");--> statement-breakpoint
CREATE INDEX "idx_playlists_visibility" ON "user_playlists" USING btree ("visibility");--> statement-breakpoint
CREATE INDEX "idx_sessions_user" ON "user_sessions" USING btree ("user_id");--> statement-breakpoint
CREATE INDEX "idx_sessions_token" ON "user_sessions" USING btree ("refresh_token_hash");--> statement-breakpoint
CREATE INDEX "idx_users_email" ON "users" USING btree ("email");--> statement-breakpoint
CREATE INDEX "idx_users_username" ON "users" USING btree ("username");--> statement-breakpoint
CREATE INDEX "idx_users_is_private" ON "users" USING btree ("is_private");