-- RLS Policies for MuE Supabase PostgreSQL Database
-- docs/00_STACK_LOCK.md & docs/04_DATABASE.md

-- ==========================================
-- 1. USERS & PUBLIC VIEW
-- ==========================================
GRANT USAGE ON SCHEMA auth TO app_user;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA auth TO app_user;
GRANT USAGE ON SCHEMA auth TO auth_role;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA auth TO auth_role;

ALTER TABLE users ENABLE ROW LEVEL SECURITY;

CREATE OR REPLACE VIEW users_public AS
  SELECT id, username, display_name, avatar_url, bio, is_private,
         follower_count, following_count, created_at
  FROM users;

-- Permissions on users table:
-- Revoke all by default, then grant SELECT on safe columns and UPDATE on editable columns.
-- Sensitive columns (password_hash, pin_hash) are strictly excluded from app_user.
REVOKE ALL ON users FROM app_user;
GRANT SELECT (id, username, display_name, avatar_url, bio, is_private, follower_count, following_count, created_at) ON users TO app_user;
GRANT UPDATE (username, display_name, avatar_url, bio) ON users TO app_user;
GRANT SELECT ON users_public TO app_user;

-- auth_role scoped strictly to CRUD (no REFERENCES, TRIGGER, TRUNCATE)
REVOKE ALL ON users FROM auth_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON users TO auth_role;
GRANT SELECT ON users_public TO auth_role;

DROP POLICY IF EXISTS users_auth_role_all ON users;
DROP POLICY IF EXISTS auth_role_all_users ON users;
CREATE POLICY auth_role_all_users ON users FOR ALL TO auth_role USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS users_select_own ON users;
CREATE POLICY users_select_own ON users FOR SELECT TO app_user
  USING (id = auth.uid());

DROP POLICY IF EXISTS users_update_own ON users;
CREATE POLICY users_update_own ON users FOR UPDATE TO app_user
  USING (id = auth.uid())
  WITH CHECK (id = auth.uid());

-- ==========================================
-- 2. USER DEVICES
-- ==========================================
ALTER TABLE user_devices ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON user_devices FROM auth_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON user_devices TO auth_role;

DROP POLICY IF EXISTS user_devices_auth_role_all ON user_devices;
DROP POLICY IF EXISTS auth_role_all_user_devices ON user_devices;
CREATE POLICY auth_role_all_user_devices ON user_devices FOR ALL TO auth_role USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS user_devices_select_own ON user_devices;
CREATE POLICY user_devices_select_own ON user_devices FOR SELECT TO app_user
  USING (user_id = auth.uid());

DROP POLICY IF EXISTS user_devices_insert_own ON user_devices;
CREATE POLICY user_devices_insert_own ON user_devices FOR INSERT TO app_user
  WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS user_devices_update_own ON user_devices;
CREATE POLICY user_devices_update_own ON user_devices FOR UPDATE TO app_user
  USING (user_id = auth.uid());

DROP POLICY IF EXISTS user_devices_delete_own ON user_devices;
CREATE POLICY user_devices_delete_own ON user_devices FOR DELETE TO app_user
  USING (user_id = auth.uid());

-- ==========================================
-- 3. USER SESSIONS
-- ==========================================
ALTER TABLE user_sessions ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON user_sessions FROM auth_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON user_sessions TO auth_role;

DROP POLICY IF EXISTS user_sessions_auth_role_all ON user_sessions;
DROP POLICY IF EXISTS auth_role_all_user_sessions ON user_sessions;
CREATE POLICY auth_role_all_user_sessions ON user_sessions FOR ALL TO auth_role USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS user_sessions_select_own ON user_sessions;
CREATE POLICY user_sessions_select_own ON user_sessions FOR SELECT TO app_user
  USING (user_id = auth.uid());

DROP POLICY IF EXISTS user_sessions_delete_own ON user_sessions;
CREATE POLICY user_sessions_delete_own ON user_sessions FOR DELETE TO app_user
  USING (user_id = auth.uid());

-- ==========================================
-- 4. E2E KEYS (Identity, Prekeys, Sender Keys)
-- ==========================================
ALTER TABLE e2e_identity_keys ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON e2e_identity_keys FROM auth_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON e2e_identity_keys TO auth_role;

DROP POLICY IF EXISTS e2e_identity_keys_auth_role_all ON e2e_identity_keys;
DROP POLICY IF EXISTS auth_role_all_e2e_identity_keys ON e2e_identity_keys;
CREATE POLICY auth_role_all_e2e_identity_keys ON e2e_identity_keys FOR ALL TO auth_role USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS e2e_identity_keys_select_any ON e2e_identity_keys;
CREATE POLICY e2e_identity_keys_select_any ON e2e_identity_keys FOR SELECT TO app_user
  USING (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS e2e_identity_keys_write_own ON e2e_identity_keys;
CREATE POLICY e2e_identity_keys_write_own ON e2e_identity_keys FOR INSERT TO app_user
  WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS e2e_identity_keys_update_own ON e2e_identity_keys;
CREATE POLICY e2e_identity_keys_update_own ON e2e_identity_keys FOR UPDATE TO app_user
  USING (user_id = auth.uid());

ALTER TABLE e2e_signed_prekeys ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON e2e_signed_prekeys FROM auth_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON e2e_signed_prekeys TO auth_role;

DROP POLICY IF EXISTS e2e_signed_prekeys_auth_role_all ON e2e_signed_prekeys;
DROP POLICY IF EXISTS auth_role_all_e2e_signed_prekeys ON e2e_signed_prekeys;
CREATE POLICY auth_role_all_e2e_signed_prekeys ON e2e_signed_prekeys FOR ALL TO auth_role USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS e2e_signed_prekeys_select_any ON e2e_signed_prekeys;
CREATE POLICY e2e_signed_prekeys_select_any ON e2e_signed_prekeys FOR SELECT TO app_user
  USING (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS e2e_signed_prekeys_write_own ON e2e_signed_prekeys;
CREATE POLICY e2e_signed_prekeys_write_own ON e2e_signed_prekeys FOR INSERT TO app_user
  WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS e2e_signed_prekeys_update_own ON e2e_signed_prekeys;
CREATE POLICY e2e_signed_prekeys_update_own ON e2e_signed_prekeys FOR UPDATE TO app_user
  USING (user_id = auth.uid());

ALTER TABLE e2e_one_time_prekeys ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON e2e_one_time_prekeys FROM auth_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON e2e_one_time_prekeys TO auth_role;

DROP POLICY IF EXISTS e2e_one_time_prekeys_auth_role_all ON e2e_one_time_prekeys;
DROP POLICY IF EXISTS auth_role_all_e2e_one_time_prekeys ON e2e_one_time_prekeys;
CREATE POLICY auth_role_all_e2e_one_time_prekeys ON e2e_one_time_prekeys FOR ALL TO auth_role USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS e2e_one_time_prekeys_select_any ON e2e_one_time_prekeys;
CREATE POLICY e2e_one_time_prekeys_select_any ON e2e_one_time_prekeys FOR SELECT TO app_user
  USING (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS e2e_one_time_prekeys_write_own ON e2e_one_time_prekeys;
CREATE POLICY e2e_one_time_prekeys_write_own ON e2e_one_time_prekeys FOR INSERT TO app_user
  WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS e2e_one_time_prekeys_delete_own ON e2e_one_time_prekeys;
CREATE POLICY e2e_one_time_prekeys_delete_own ON e2e_one_time_prekeys FOR DELETE TO app_user
  USING (user_id = auth.uid());

ALTER TABLE e2e_sender_key_distributions ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON e2e_sender_key_distributions FROM auth_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON e2e_sender_key_distributions TO auth_role;

DROP POLICY IF EXISTS e2e_sender_key_auth_role_all ON e2e_sender_key_distributions;
DROP POLICY IF EXISTS auth_role_all_e2e_sender_key_distributions ON e2e_sender_key_distributions;
CREATE POLICY auth_role_all_e2e_sender_key_distributions ON e2e_sender_key_distributions FOR ALL TO auth_role USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS e2e_sender_key_select ON e2e_sender_key_distributions;
CREATE POLICY e2e_sender_key_select ON e2e_sender_key_distributions FOR SELECT TO app_user
  USING (sender_user_id = auth.uid() OR recipient_user_id = auth.uid());

DROP POLICY IF EXISTS e2e_sender_key_write_own ON e2e_sender_key_distributions;
CREATE POLICY e2e_sender_key_write_own ON e2e_sender_key_distributions FOR INSERT TO app_user
  WITH CHECK (sender_user_id = auth.uid());

DROP POLICY IF EXISTS e2e_sender_key_delete_own ON e2e_sender_key_distributions;
CREATE POLICY e2e_sender_key_delete_own ON e2e_sender_key_distributions FOR DELETE TO app_user
  USING (sender_user_id = auth.uid());

-- ==========================================
-- 5. SOCIAL & MESSAGING
-- ==========================================
ALTER TABLE user_profiles ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS user_profiles_select ON user_profiles;
CREATE POLICY user_profiles_select ON user_profiles FOR SELECT TO app_user USING (auth.uid() IS NOT NULL);
DROP POLICY IF EXISTS user_profiles_insert ON user_profiles;
CREATE POLICY user_profiles_insert ON user_profiles FOR INSERT TO app_user WITH CHECK (user_id = auth.uid());
DROP POLICY IF EXISTS user_profiles_update ON user_profiles;
CREATE POLICY user_profiles_update ON user_profiles FOR UPDATE TO app_user USING (user_id = auth.uid()) WITH CHECK (user_id = auth.uid());

ALTER TABLE user_preferences ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS user_preferences_select ON user_preferences;
CREATE POLICY user_preferences_select ON user_preferences FOR SELECT TO app_user USING (user_id = auth.uid());
DROP POLICY IF EXISTS user_preferences_insert ON user_preferences;
CREATE POLICY user_preferences_insert ON user_preferences FOR INSERT TO app_user WITH CHECK (user_id = auth.uid());
DROP POLICY IF EXISTS user_preferences_update ON user_preferences;
CREATE POLICY user_preferences_update ON user_preferences FOR UPDATE TO app_user USING (user_id = auth.uid());
DROP POLICY IF EXISTS user_preferences_delete ON user_preferences;
CREATE POLICY user_preferences_delete ON user_preferences FOR DELETE TO app_user USING (user_id = auth.uid());

ALTER TABLE user_likes ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS user_likes_select ON user_likes;
CREATE POLICY user_likes_select ON user_likes FOR SELECT TO app_user USING (user_id = auth.uid());
DROP POLICY IF EXISTS user_likes_insert ON user_likes;
CREATE POLICY user_likes_insert ON user_likes FOR INSERT TO app_user WITH CHECK (user_id = auth.uid());
DROP POLICY IF EXISTS user_likes_delete ON user_likes;
CREATE POLICY user_likes_delete ON user_likes FOR DELETE TO app_user USING (user_id = auth.uid());

ALTER TABLE user_playlists ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS user_playlists_select ON user_playlists;
CREATE POLICY user_playlists_select ON user_playlists FOR SELECT TO app_user USING (user_id = auth.uid() OR visibility = 'public');
DROP POLICY IF EXISTS user_playlists_insert ON user_playlists;
CREATE POLICY user_playlists_insert ON user_playlists FOR INSERT TO app_user WITH CHECK (user_id = auth.uid());
DROP POLICY IF EXISTS user_playlists_update ON user_playlists;
CREATE POLICY user_playlists_update ON user_playlists FOR UPDATE TO app_user USING (user_id = auth.uid()) WITH CHECK (user_id = auth.uid());
DROP POLICY IF EXISTS user_playlists_delete ON user_playlists;
CREATE POLICY user_playlists_delete ON user_playlists FOR DELETE TO app_user USING (user_id = auth.uid());

ALTER TABLE user_playlist_tracks ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS user_playlist_tracks_select ON user_playlist_tracks;
CREATE POLICY user_playlist_tracks_select ON user_playlist_tracks FOR SELECT TO app_user USING (
  EXISTS (SELECT 1 FROM user_playlists WHERE id = playlist_id AND user_id = auth.uid())
);
DROP POLICY IF EXISTS user_playlist_tracks_insert ON user_playlist_tracks;
CREATE POLICY user_playlist_tracks_insert ON user_playlist_tracks FOR INSERT TO app_user WITH CHECK (
  EXISTS (SELECT 1 FROM user_playlists WHERE id = playlist_id AND user_id = auth.uid())
);
DROP POLICY IF EXISTS user_playlist_tracks_delete ON user_playlist_tracks;
CREATE POLICY user_playlist_tracks_delete ON user_playlist_tracks FOR DELETE TO app_user USING (
  EXISTS (SELECT 1 FROM user_playlists WHERE id = playlist_id AND user_id = auth.uid())
);
DROP POLICY IF EXISTS user_playlist_tracks_update ON user_playlist_tracks;
CREATE POLICY user_playlist_tracks_update ON user_playlist_tracks FOR UPDATE TO app_user USING (
  EXISTS (SELECT 1 FROM user_playlists WHERE id = playlist_id AND user_id = auth.uid())
) WITH CHECK (
  EXISTS (SELECT 1 FROM user_playlists WHERE id = playlist_id AND user_id = auth.uid())
);

ALTER TABLE user_play_history ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS user_play_history_select ON user_play_history;
CREATE POLICY user_play_history_select ON user_play_history FOR SELECT TO app_user USING (user_id = auth.uid());
DROP POLICY IF EXISTS user_play_history_insert ON user_play_history;
CREATE POLICY user_play_history_insert ON user_play_history FOR INSERT TO app_user WITH CHECK (user_id = auth.uid());
DROP POLICY IF EXISTS user_play_history_delete ON user_play_history;
CREATE POLICY user_play_history_delete ON user_play_history FOR DELETE TO app_user USING (user_id = auth.uid());

ALTER TABLE follows ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS follows_select ON follows;
DROP POLICY IF EXISTS follows_select_own ON follows;
CREATE POLICY follows_select_own ON follows FOR SELECT TO app_user
  USING (follower_id = auth.uid() OR followee_id = auth.uid());

CREATE OR REPLACE FUNCTION is_account_public(target_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
STABLE
AS $$
  SELECT EXISTS (
    SELECT 1 FROM users
    WHERE id = target_id AND is_private = FALSE
  );
$$;

CREATE OR REPLACE FUNCTION is_following(viewer_id UUID, target_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
STABLE
AS $$
  SELECT EXISTS (
    SELECT 1 FROM follows
    WHERE follower_id = viewer_id AND followee_id = target_id
  );
$$;

CREATE OR REPLACE FUNCTION is_blocked(user1 UUID, user2 UUID)
RETURNS BOOLEAN
LANGUAGE sql
SECURITY DEFINER
SET search_path = public
STABLE
AS $$
  SELECT EXISTS (
    SELECT 1 FROM blocks
    WHERE (blocker_id = user1 AND blocked_id = user2)
       OR (blocker_id = user2 AND blocked_id = user1)
  );
$$;

GRANT EXECUTE ON FUNCTION is_account_public(UUID) TO app_user;
GRANT EXECUTE ON FUNCTION is_following(UUID, UUID) TO app_user;
GRANT EXECUTE ON FUNCTION is_blocked(UUID, UUID) TO app_user;

DROP POLICY IF EXISTS follows_select_public ON follows;
CREATE POLICY follows_select_public ON follows FOR SELECT TO app_user
  USING (is_account_public(followee_id));

DROP POLICY IF EXISTS follows_select_follower ON follows;
CREATE POLICY follows_select_follower ON follows FOR SELECT TO app_user
  USING (is_following(auth.uid(), followee_id));

DROP POLICY IF EXISTS follows_insert ON follows;
CREATE POLICY follows_insert ON follows FOR INSERT TO app_user WITH CHECK (follower_id = auth.uid() OR followee_id = auth.uid());

DROP POLICY IF EXISTS follows_delete ON follows;
CREATE POLICY follows_delete ON follows FOR DELETE TO app_user USING (follower_id = auth.uid());

GRANT SELECT, INSERT, DELETE ON follows TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON follows TO auth_role;

DROP POLICY IF EXISTS auth_role_all_follows ON follows;
CREATE POLICY auth_role_all_follows ON follows FOR ALL TO auth_role USING (true) WITH CHECK (true);

ALTER TABLE follow_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE follow_requests ALTER COLUMN expires_at SET DEFAULT (NOW() + INTERVAL '30 days');
DROP POLICY IF EXISTS follow_requests_select ON follow_requests;
CREATE POLICY follow_requests_select ON follow_requests FOR SELECT TO app_user USING (from_user_id = auth.uid() OR to_user_id = auth.uid());
DROP POLICY IF EXISTS follow_requests_insert ON follow_requests;
CREATE POLICY follow_requests_insert ON follow_requests FOR INSERT TO app_user WITH CHECK (from_user_id = auth.uid());
DROP POLICY IF EXISTS follow_requests_update ON follow_requests;
CREATE POLICY follow_requests_update ON follow_requests FOR UPDATE TO app_user USING (to_user_id = auth.uid());
DROP POLICY IF EXISTS follow_requests_delete ON follow_requests;
CREATE POLICY follow_requests_delete ON follow_requests FOR DELETE TO app_user USING (from_user_id = auth.uid() OR to_user_id = auth.uid());

GRANT SELECT, INSERT, UPDATE, DELETE ON follow_requests TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON follow_requests TO auth_role;

DROP POLICY IF EXISTS auth_role_all_follow_requests ON follow_requests;
CREATE POLICY auth_role_all_follow_requests ON follow_requests FOR ALL TO auth_role USING (true) WITH CHECK (true);

ALTER TABLE blocks ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS blocks_select ON blocks;
CREATE POLICY blocks_select ON blocks FOR SELECT TO app_user USING (blocker_id = auth.uid());
DROP POLICY IF EXISTS blocks_insert ON blocks;
CREATE POLICY blocks_insert ON blocks FOR INSERT TO app_user WITH CHECK (blocker_id = auth.uid());
DROP POLICY IF EXISTS blocks_delete ON blocks;
CREATE POLICY blocks_delete ON blocks FOR DELETE TO app_user USING (blocker_id = auth.uid());

GRANT SELECT, INSERT, DELETE ON blocks TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON blocks TO auth_role;

DROP POLICY IF EXISTS auth_role_all_blocks ON blocks;
CREATE POLICY auth_role_all_blocks ON blocks FOR ALL TO auth_role USING (true) WITH CHECK (true);

DROP TRIGGER IF EXISTS trg_chat_participants_mutual_follow ON chat_participants;


DROP POLICY IF EXISTS auth_role_all_chat_conversations ON chat_conversations;
CREATE POLICY auth_role_all_chat_conversations ON chat_conversations FOR ALL TO auth_role USING (true) WITH CHECK (true);

-- 2. chat_participants
ALTER TABLE chat_participants ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS chat_participants_select ON chat_participants;
CREATE POLICY chat_participants_select ON chat_participants FOR SELECT TO app_user USING (
  user_id = auth.uid() OR
  EXISTS (SELECT 1 FROM chat_participants cp2 WHERE cp2.conversation_id = chat_participants.conversation_id AND cp2.user_id = auth.uid())
);
DROP POLICY IF EXISTS chat_participants_insert ON chat_participants;
DROP POLICY IF EXISTS chat_participants_update ON chat_participants;
DROP POLICY IF EXISTS chat_participants_delete ON chat_participants;

DROP POLICY IF EXISTS auth_role_all_chat_participants ON chat_participants;
CREATE POLICY auth_role_all_chat_participants ON chat_participants FOR ALL TO auth_role USING (true) WITH CHECK (true);

-- 3. chat_messages (ciphertext only)
ALTER TABLE chat_messages ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS chat_messages_select ON chat_messages;
CREATE POLICY chat_messages_select ON chat_messages FOR SELECT TO app_user USING (
  EXISTS (SELECT 1 FROM chat_participants WHERE conversation_id = chat_messages.conversation_id AND user_id = auth.uid())
);
DROP POLICY IF EXISTS chat_messages_insert ON chat_messages; -- backend only inserts via auth_role
DROP POLICY IF EXISTS chat_messages_update ON chat_messages;
CREATE POLICY chat_messages_update ON chat_messages FOR UPDATE TO app_user USING (sender_id = auth.uid());
DROP POLICY IF EXISTS chat_messages_delete ON chat_messages;
CREATE POLICY chat_messages_delete ON chat_messages FOR DELETE TO app_user USING (sender_id = auth.uid());

DROP POLICY IF EXISTS auth_role_all_chat_messages ON chat_messages;
CREATE POLICY auth_role_all_chat_messages ON chat_messages FOR ALL TO auth_role USING (true) WITH CHECK (true);

ALTER TABLE chat_message_reads ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS chat_message_reads_select ON chat_message_reads;
CREATE POLICY chat_message_reads_select ON chat_message_reads FOR SELECT TO app_user USING (user_id = auth.uid());
DROP POLICY IF EXISTS chat_message_reads_insert ON chat_message_reads;
CREATE POLICY chat_message_reads_insert ON chat_message_reads FOR INSERT TO app_user WITH CHECK (user_id = auth.uid());

ALTER TABLE chat_reactions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS chat_reactions_select ON chat_reactions;
CREATE POLICY chat_reactions_select ON chat_reactions FOR SELECT TO app_user USING (auth.uid() IS NOT NULL);
DROP POLICY IF EXISTS chat_reactions_insert ON chat_reactions;
CREATE POLICY chat_reactions_insert ON chat_reactions FOR INSERT TO app_user WITH CHECK (user_id = auth.uid());
DROP POLICY IF EXISTS chat_reactions_delete ON chat_reactions;
CREATE POLICY chat_reactions_delete ON chat_reactions FOR DELETE TO app_user USING (user_id = auth.uid());

ALTER TABLE chat_attachments ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON chat_attachments FROM app_user;
GRANT SELECT ON chat_attachments TO app_user;
REVOKE ALL ON chat_attachments FROM auth_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON chat_attachments TO auth_role;

DROP POLICY IF EXISTS chat_attachments_select ON chat_attachments;
CREATE POLICY chat_attachments_select ON chat_attachments FOR SELECT TO app_user
  USING (
    EXISTS (
      SELECT 1 FROM chat_messages m
      JOIN chat_participants p ON p.conversation_id = m.conversation_id
      WHERE m.id = chat_attachments.message_id
        AND p.user_id = auth.uid()
    )
  );

DROP POLICY IF EXISTS chat_attachments_insert ON chat_attachments;
-- No client INSERT. Backend auth_role only.

DROP POLICY IF EXISTS auth_role_all_chat_attachments ON chat_attachments;
CREATE POLICY auth_role_all_chat_attachments ON chat_attachments FOR ALL TO auth_role USING (true) WITH CHECK (true);

ALTER TABLE chat_reports ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS chat_reports_select_own ON chat_reports;
CREATE POLICY chat_reports_select_own ON chat_reports FOR SELECT TO app_user USING (reporter_id = auth.uid());
DROP POLICY IF EXISTS chat_reports_insert ON chat_reports;
CREATE POLICY chat_reports_insert ON chat_reports FOR INSERT TO app_user WITH CHECK (reporter_id = auth.uid());

ALTER TABLE notifications ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS notifications_select ON notifications;
CREATE POLICY notifications_select ON notifications FOR SELECT TO app_user USING (user_id = auth.uid());
DROP POLICY IF EXISTS notifications_insert ON notifications;
CREATE POLICY notifications_insert ON notifications FOR INSERT TO app_user WITH CHECK (actor_id = auth.uid() OR user_id = auth.uid());
DROP POLICY IF EXISTS notifications_update ON notifications;
CREATE POLICY notifications_update ON notifications FOR UPDATE TO app_user USING (user_id = auth.uid());
DROP POLICY IF EXISTS notifications_delete ON notifications;
CREATE POLICY notifications_delete ON notifications FOR DELETE TO app_user USING (user_id = auth.uid());

GRANT SELECT, INSERT, UPDATE, DELETE ON notifications TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON notifications TO auth_role;

DROP POLICY IF EXISTS auth_role_all_notifications ON notifications;
CREATE POLICY auth_role_all_notifications ON notifications FOR ALL TO auth_role USING (true) WITH CHECK (true);

ALTER TABLE listen_sessions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS listen_sessions_select ON listen_sessions;
CREATE POLICY listen_sessions_select ON listen_sessions FOR SELECT TO app_user USING (auth.uid() IS NOT NULL);
DROP POLICY IF EXISTS listen_sessions_insert ON listen_sessions;
CREATE POLICY listen_sessions_insert ON listen_sessions FOR INSERT TO app_user WITH CHECK (host_user_id = auth.uid());
DROP POLICY IF EXISTS listen_sessions_update ON listen_sessions;
CREATE POLICY listen_sessions_update ON listen_sessions FOR UPDATE TO app_user
  USING (
    host_user_id = auth.uid()
    OR (
      control_mode = 'open'
      AND EXISTS (
        SELECT 1 FROM listen_participants
        WHERE session_id = listen_sessions.id
          AND user_id = auth.uid()
          AND left_at IS NULL
      )
    )
  )
  WITH CHECK (
    host_user_id IS NOT NULL
  );
DROP POLICY IF EXISTS listen_sessions_delete ON listen_sessions;
CREATE POLICY listen_sessions_delete ON listen_sessions FOR DELETE TO app_user USING (host_user_id = auth.uid());
REVOKE ALL ON listen_sessions FROM auth_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON listen_sessions TO auth_role;
DROP POLICY IF EXISTS auth_role_all_listen_sessions ON listen_sessions;
CREATE POLICY auth_role_all_listen_sessions ON listen_sessions FOR ALL TO auth_role USING (true) WITH CHECK (true);

ALTER TABLE listen_participants ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS listen_participants_select ON listen_participants;
CREATE POLICY listen_participants_select ON listen_participants FOR SELECT TO app_user USING (auth.uid() IS NOT NULL);
DROP POLICY IF EXISTS listen_participants_insert ON listen_participants;
CREATE POLICY listen_participants_insert ON listen_participants FOR INSERT TO app_user WITH CHECK (user_id = auth.uid());
DROP POLICY IF EXISTS listen_participants_delete ON listen_participants;
CREATE POLICY listen_participants_delete ON listen_participants FOR DELETE TO app_user USING (user_id = auth.uid());
REVOKE ALL ON listen_participants FROM auth_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON listen_participants TO auth_role;
DROP POLICY IF EXISTS auth_role_all_listen_participants ON listen_participants;
CREATE POLICY auth_role_all_listen_participants ON listen_participants FOR ALL TO auth_role USING (true) WITH CHECK (true);

-- ---------------------------------------------------------------------------
-- listen_queue_items
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS listen_queue_items (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id uuid NOT NULL REFERENCES listen_sessions(id) ON DELETE CASCADE,
  track_id text NOT NULL,
  provider text NOT NULL,
  title text NOT NULL,
  artist text,
  artwork_url text,
  added_by_user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  position integer NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_listen_queue_session ON listen_queue_items (session_id, position);

GRANT ALL ON listen_queue_items TO app_user;
GRANT ALL ON listen_queue_items TO auth_role;

ALTER TABLE listen_queue_items ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS listen_queue_items_select ON listen_queue_items;
CREATE POLICY listen_queue_items_select ON listen_queue_items FOR SELECT TO app_user USING (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS listen_queue_items_insert ON listen_queue_items;
CREATE POLICY listen_queue_items_insert ON listen_queue_items FOR INSERT TO app_user WITH CHECK (
  added_by_user_id = auth.uid()
  AND (
    EXISTS (
      SELECT 1 FROM listen_sessions WHERE id = listen_queue_items.session_id AND host_user_id = auth.uid()
    )
    OR EXISTS (
      SELECT 1 FROM listen_participants WHERE session_id = listen_queue_items.session_id AND user_id = auth.uid() AND left_at IS NULL
    )
  )
);

DROP POLICY IF EXISTS listen_queue_items_delete ON listen_queue_items;
CREATE POLICY listen_queue_items_delete ON listen_queue_items FOR DELETE TO app_user USING (
  added_by_user_id = auth.uid()
  OR EXISTS (
    SELECT 1 FROM listen_sessions WHERE id = listen_queue_items.session_id AND host_user_id = auth.uid()
  )
);

DROP POLICY IF EXISTS auth_role_all_listen_queue_items ON listen_queue_items;
CREATE POLICY auth_role_all_listen_queue_items ON listen_queue_items FOR ALL TO auth_role USING (true) WITH CHECK (true);

ALTER TABLE handoff_sessions ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS handoff_sessions_select ON handoff_sessions;
CREATE POLICY handoff_sessions_select ON handoff_sessions FOR SELECT TO app_user USING (user_id = auth.uid());
DROP POLICY IF EXISTS handoff_sessions_insert ON handoff_sessions;
CREATE POLICY handoff_sessions_insert ON handoff_sessions FOR INSERT TO app_user WITH CHECK (user_id = auth.uid());
DROP POLICY IF EXISTS handoff_sessions_update ON handoff_sessions;
CREATE POLICY handoff_sessions_update ON handoff_sessions FOR UPDATE TO app_user USING (user_id = auth.uid());
DROP POLICY IF EXISTS handoff_sessions_delete ON handoff_sessions;
CREATE POLICY handoff_sessions_delete ON handoff_sessions FOR DELETE TO app_user USING (user_id = auth.uid());
REVOKE ALL ON handoff_sessions FROM auth_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON handoff_sessions TO auth_role;
DROP POLICY IF EXISTS auth_role_all_handoff_sessions ON handoff_sessions;
CREATE POLICY auth_role_all_handoff_sessions ON handoff_sessions FOR ALL TO auth_role USING (true) WITH CHECK (true);

ALTER TABLE sync_operations ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON sync_operations FROM auth_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON sync_operations TO auth_role;
DROP POLICY IF EXISTS auth_role_all_sync_operations ON sync_operations;
CREATE POLICY auth_role_all_sync_operations ON sync_operations FOR ALL TO auth_role USING (true) WITH CHECK (true);
DROP POLICY IF EXISTS sync_operations_select ON sync_operations;
CREATE POLICY sync_operations_select ON sync_operations FOR SELECT TO app_user USING (user_id = auth.uid());
DROP POLICY IF EXISTS sync_operations_insert ON sync_operations;
CREATE POLICY sync_operations_insert ON sync_operations FOR INSERT TO app_user WITH CHECK (user_id = auth.uid());
DROP POLICY IF EXISTS sync_operations_update ON sync_operations;
DROP POLICY IF EXISTS sync_operations_delete ON sync_operations;

ALTER TABLE artist_play_stats ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS artist_play_stats_select ON artist_play_stats;
CREATE POLICY artist_play_stats_select ON artist_play_stats FOR SELECT TO app_user USING (user_id = auth.uid());
DROP POLICY IF EXISTS artist_play_stats_insert ON artist_play_stats;
CREATE POLICY artist_play_stats_insert ON artist_play_stats FOR INSERT TO app_user WITH CHECK (user_id = auth.uid());
DROP POLICY IF EXISTS artist_play_stats_update ON artist_play_stats;
CREATE POLICY artist_play_stats_update ON artist_play_stats FOR UPDATE TO app_user USING (user_id = auth.uid());

ALTER TABLE genre_play_stats ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS genre_play_stats_select ON genre_play_stats;
CREATE POLICY genre_play_stats_select ON genre_play_stats FOR SELECT TO app_user USING (user_id = auth.uid());
DROP POLICY IF EXISTS genre_play_stats_insert ON genre_play_stats;
CREATE POLICY genre_play_stats_insert ON genre_play_stats FOR INSERT TO app_user WITH CHECK (user_id = auth.uid());
DROP POLICY IF EXISTS genre_play_stats_update ON genre_play_stats;
CREATE POLICY genre_play_stats_update ON genre_play_stats FOR UPDATE TO app_user USING (user_id = auth.uid());

ALTER TABLE song_play_stats ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS song_play_stats_select ON song_play_stats;
CREATE POLICY song_play_stats_select ON song_play_stats FOR SELECT TO app_user USING (user_id = auth.uid());
DROP POLICY IF EXISTS song_play_stats_insert ON song_play_stats;
CREATE POLICY song_play_stats_insert ON song_play_stats FOR INSERT TO app_user WITH CHECK (user_id = auth.uid());
DROP POLICY IF EXISTS song_play_stats_update ON song_play_stats;
CREATE POLICY song_play_stats_update ON song_play_stats FOR UPDATE TO app_user USING (user_id = auth.uid());

ALTER TABLE analytics_events ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS analytics_events_select ON analytics_events;
CREATE POLICY analytics_events_select ON analytics_events FOR SELECT TO app_user USING (user_id = auth.uid());
DROP POLICY IF EXISTS analytics_events_insert ON analytics_events;
CREATE POLICY analytics_events_insert ON analytics_events FOR INSERT TO app_user WITH CHECK (user_id = auth.uid() OR auth.uid() IS NOT NULL);

-- ==========================================
-- 15. CHAT CONVERSATIONS, PARTICIPANTS & MESSAGES (Phase 11C.1)
-- ==========================================
CREATE OR REPLACE FUNCTION check_mutual_follow(user_a uuid, user_b uuid)
RETURNS boolean
LANGUAGE sql SECURITY DEFINER
SET search_path = public
AS $$
  SELECT
    EXISTS (SELECT 1 FROM follows WHERE follower_id = user_a AND followee_id = user_b)
    AND
    EXISTS (SELECT 1 FROM follows WHERE follower_id = user_b AND followee_id = user_a)
$$;

CREATE OR REPLACE FUNCTION enforce_mutual_follow_on_participant()
RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
  v_conv_type text;
  v_created_by uuid;
  v_is_mutual boolean;
BEGIN
  SELECT type, created_by INTO v_conv_type, v_created_by
  FROM chat_conversations
  WHERE id = NEW.conversation_id;

  -- 1. Direct conversations: enforce mutual follow between creator and target
  IF v_conv_type = 'direct' AND NEW.user_id != v_created_by THEN
    SELECT check_mutual_follow(v_created_by, NEW.user_id) INTO v_is_mutual;
    IF NOT COALESCE(v_is_mutual, false) THEN
      RAISE EXCEPTION 'Mutual follow required for direct conversation';
    END IF;
  ELSIF v_conv_type = 'group' THEN
    IF NEW.user_id != v_created_by THEN
      SELECT check_mutual_follow(v_created_by, NEW.user_id) INTO v_is_mutual;
      IF NOT COALESCE(v_is_mutual, false) THEN
        RAISE EXCEPTION 'Mutual follow with group creator required';
      END IF;
    END IF;
  END IF;

  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_enforce_mutual_follow_on_participant ON chat_participants;
CREATE TRIGGER trg_enforce_mutual_follow_on_participant
BEFORE INSERT ON chat_participants
FOR EACH ROW
EXECUTE FUNCTION enforce_mutual_follow_on_participant();

ALTER TABLE chat_conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat_participants ENABLE ROW LEVEL SECURITY;
ALTER TABLE chat_messages ENABLE ROW LEVEL SECURITY;

-- app_user scoped chat mutation policies
GRANT SELECT, INSERT, UPDATE, DELETE ON chat_conversations TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON chat_participants TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON chat_messages TO app_user;

-- auth_role scoped strictly to CRUD
REVOKE ALL ON chat_conversations FROM auth_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON chat_conversations TO auth_role;

REVOKE ALL ON chat_participants FROM auth_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON chat_participants TO auth_role;

REVOKE ALL ON chat_messages FROM auth_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON chat_messages TO auth_role;

DROP POLICY IF EXISTS auth_role_all_chat_conversations ON chat_conversations;
CREATE POLICY auth_role_all_chat_conversations ON chat_conversations FOR ALL TO auth_role USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS auth_role_all_chat_participants ON chat_participants;
CREATE POLICY auth_role_all_chat_participants ON chat_participants FOR ALL TO auth_role USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS auth_role_all_chat_messages ON chat_messages;
CREATE POLICY auth_role_all_chat_messages ON chat_messages FOR ALL TO auth_role USING (true) WITH CHECK (true);

-- Helper function to avoid policy recursion on chat_participants
CREATE OR REPLACE FUNCTION is_chat_participant(conv_id uuid, u_id uuid)
RETURNS boolean
LANGUAGE sql SECURITY DEFINER
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1 FROM chat_participants
    WHERE conversation_id = conv_id AND user_id = u_id
  );
$$;

-- app_user RLS policies
DROP POLICY IF EXISTS chat_conversations_select ON chat_conversations;
CREATE POLICY chat_conversations_select ON chat_conversations FOR SELECT TO app_user
  USING (created_by = auth.uid() OR is_chat_participant(id, auth.uid()));

DROP POLICY IF EXISTS chat_conversations_insert ON chat_conversations;
CREATE POLICY chat_conversations_insert ON chat_conversations FOR INSERT TO app_user
  WITH CHECK (created_by = auth.uid());

DROP POLICY IF EXISTS chat_conversations_update ON chat_conversations;
CREATE POLICY chat_conversations_update ON chat_conversations FOR UPDATE TO app_user
  USING (is_chat_participant(id, auth.uid()));

DROP POLICY IF EXISTS chat_conversations_delete ON chat_conversations;
CREATE POLICY chat_conversations_delete ON chat_conversations FOR DELETE TO app_user
  USING (created_by = auth.uid());

DROP POLICY IF EXISTS chat_participants_select ON chat_participants;
CREATE POLICY chat_participants_select ON chat_participants FOR SELECT TO app_user
  USING (is_chat_participant(conversation_id, auth.uid()));

DROP POLICY IF EXISTS chat_participants_insert ON chat_participants;
CREATE POLICY chat_participants_insert ON chat_participants FOR INSERT TO app_user
  WITH CHECK (auth.uid() IS NOT NULL);

DROP POLICY IF EXISTS chat_participants_delete ON chat_participants;
CREATE POLICY chat_participants_delete ON chat_participants FOR DELETE TO app_user
  USING (user_id = auth.uid() OR is_chat_participant(conversation_id, auth.uid()));

DROP POLICY IF EXISTS chat_messages_select ON chat_messages;
CREATE POLICY chat_messages_select ON chat_messages FOR SELECT TO app_user
  USING (is_chat_participant(conversation_id, auth.uid()));

DROP POLICY IF EXISTS chat_messages_insert ON chat_messages;
CREATE POLICY chat_messages_insert ON chat_messages FOR INSERT TO app_user
  WITH CHECK (sender_id = auth.uid() AND is_chat_participant(conversation_id, auth.uid()));

DROP POLICY IF EXISTS chat_messages_update ON chat_messages;
CREATE POLICY chat_messages_update ON chat_messages FOR UPDATE TO app_user
  USING (sender_id = auth.uid() OR is_chat_participant(conversation_id, auth.uid()));

DROP POLICY IF EXISTS chat_messages_delete ON chat_messages;
CREATE POLICY chat_messages_delete ON chat_messages FOR DELETE TO app_user
  USING (sender_id = auth.uid());

-- ==========================================
-- CHAT PUSH HINTS SENT (Phase 11C.5 Concern 1 & 3)
-- ==========================================
ALTER TABLE chat_push_hints_sent ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON chat_push_hints_sent FROM app_user;
REVOKE ALL ON chat_push_hints_sent FROM auth_role;
GRANT SELECT, INSERT, DELETE ON chat_push_hints_sent TO auth_role;
DROP POLICY IF EXISTS auth_role_all_chat_push_hints_sent ON chat_push_hints_sent;
CREATE POLICY auth_role_all_chat_push_hints_sent ON chat_push_hints_sent
  FOR ALL TO auth_role USING (true) WITH CHECK (true);

-- ==========================================
-- USER MUSIC INTEGRATIONS & IMPORTED PLAYLISTS
-- ==========================================
ALTER TABLE user_music_integrations ENABLE ROW LEVEL SECURITY;
ALTER TABLE imported_playlists ENABLE ROW LEVEL SECURITY;
ALTER TABLE imported_playlist_tracks ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON user_music_integrations FROM app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON user_music_integrations TO app_user;
DROP POLICY IF EXISTS integrations_select_own ON user_music_integrations;
DROP POLICY IF EXISTS integrations_all_own ON user_music_integrations;
CREATE POLICY integrations_all_own ON user_music_integrations
  FOR ALL TO app_user USING (user_id = auth.uid()) WITH CHECK (user_id = auth.uid());

REVOKE ALL ON imported_playlists FROM app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON imported_playlists TO app_user;
DROP POLICY IF EXISTS playlists_select_own ON imported_playlists;
DROP POLICY IF EXISTS playlists_all_own ON imported_playlists;
CREATE POLICY playlists_all_own ON imported_playlists
  FOR ALL TO app_user USING (user_id = auth.uid()) WITH CHECK (user_id = auth.uid());

REVOKE ALL ON imported_playlist_tracks FROM app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON imported_playlist_tracks TO app_user;
DROP POLICY IF EXISTS tracks_select_own ON imported_playlist_tracks;
DROP POLICY IF EXISTS tracks_all_own ON imported_playlist_tracks;
CREATE POLICY tracks_all_own ON imported_playlist_tracks
  FOR ALL TO app_user
  USING (imported_playlist_id IN (SELECT id FROM imported_playlists WHERE user_id = auth.uid()))
  WITH CHECK (imported_playlist_id IN (SELECT id FROM imported_playlists WHERE user_id = auth.uid()));

-- ==========================================
-- 10. GUEST MERGE SERVICE (auth_role)
-- ==========================================

GRANT SELECT, INSERT, UPDATE, DELETE ON user_play_history TO auth_role;
DROP POLICY IF EXISTS auth_role_all_user_play_history ON user_play_history;
CREATE POLICY auth_role_all_user_play_history ON user_play_history FOR ALL TO auth_role USING (true) WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON user_likes TO auth_role;
DROP POLICY IF EXISTS auth_role_all_user_likes ON user_likes;
CREATE POLICY auth_role_all_user_likes ON user_likes FOR ALL TO auth_role USING (true) WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON song_play_stats TO auth_role;
DROP POLICY IF EXISTS auth_role_all_song_play_stats ON song_play_stats;
CREATE POLICY auth_role_all_song_play_stats ON song_play_stats FOR ALL TO auth_role USING (true) WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON artist_play_stats TO auth_role;
DROP POLICY IF EXISTS auth_role_all_artist_play_stats ON artist_play_stats;
CREATE POLICY auth_role_all_artist_play_stats ON artist_play_stats FOR ALL TO auth_role USING (true) WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON genre_play_stats TO auth_role;
DROP POLICY IF EXISTS auth_role_all_genre_play_stats ON genre_play_stats;
CREATE POLICY auth_role_all_genre_play_stats ON genre_play_stats FOR ALL TO auth_role USING (true) WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON user_playlists TO auth_role;
DROP POLICY IF EXISTS auth_role_all_user_playlists ON user_playlists;
CREATE POLICY auth_role_all_user_playlists ON user_playlists FOR ALL TO auth_role USING (true) WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON user_playlist_tracks TO auth_role;
DROP POLICY IF EXISTS auth_role_all_user_playlist_tracks ON user_playlist_tracks;
CREATE POLICY auth_role_all_user_playlist_tracks ON user_playlist_tracks FOR ALL TO auth_role USING (true) WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON imported_playlists TO auth_role;
DROP POLICY IF EXISTS auth_role_all_imported_playlists ON imported_playlists;
CREATE POLICY auth_role_all_imported_playlists ON imported_playlists FOR ALL TO auth_role USING (true) WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON imported_playlist_tracks TO auth_role;
DROP POLICY IF EXISTS auth_role_all_imported_playlist_tracks ON imported_playlist_tracks;
CREATE POLICY auth_role_all_imported_playlist_tracks ON imported_playlist_tracks FOR ALL TO auth_role USING (true) WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON user_music_integrations TO auth_role;
DROP POLICY IF EXISTS auth_role_all_user_music_integrations ON user_music_integrations;
CREATE POLICY auth_role_all_user_music_integrations ON user_music_integrations FOR ALL TO auth_role USING (true) WITH CHECK (true);

GRANT SELECT, INSERT, UPDATE, DELETE ON guest_merge_log TO auth_role;
ALTER TABLE guest_merge_log ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS auth_role_all_guest_merge_log ON guest_merge_log;
CREATE POLICY auth_role_all_guest_merge_log ON guest_merge_log FOR ALL TO auth_role USING (true) WITH CHECK (true);

GRANT SELECT, INSERT ON guest_merge_log TO app_user;
DROP POLICY IF EXISTS app_user_all_guest_merge_log ON guest_merge_log;
CREATE POLICY app_user_all_guest_merge_log ON guest_merge_log FOR ALL TO app_user
  USING (auth_id = auth.uid() OR guest_id = auth.uid())
  WITH CHECK (auth_id = auth.uid() OR guest_id = auth.uid());

GRANT SELECT, INSERT, UPDATE, DELETE ON user_preferences TO auth_role;
DROP POLICY IF EXISTS auth_role_all_user_preferences ON user_preferences;
CREATE POLICY auth_role_all_user_preferences ON user_preferences FOR ALL TO auth_role USING (true) WITH CHECK (true);

-- ==========================================
-- 28. AUTH OTPS (signup_verification_otps, password_reset_otps)
-- ==========================================
ALTER TABLE signup_verification_otps ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON signup_verification_otps FROM app_user;
REVOKE ALL ON signup_verification_otps FROM auth_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON signup_verification_otps TO auth_role;
DROP POLICY IF EXISTS auth_role_all_signup_verification_otps ON signup_verification_otps;
CREATE POLICY auth_role_all_signup_verification_otps ON signup_verification_otps FOR ALL TO auth_role USING (true) WITH CHECK (true);

ALTER TABLE password_reset_otps ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON password_reset_otps FROM app_user;
REVOKE ALL ON password_reset_otps FROM auth_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON password_reset_otps TO auth_role;
DROP POLICY IF EXISTS auth_role_all_password_reset_otps ON password_reset_otps;
CREATE POLICY auth_role_all_password_reset_otps ON password_reset_otps FOR ALL TO auth_role USING (true) WITH CHECK (true);

-- ==========================================
-- 29. TRACK SIMILARITY & SEARCH CATALOG
-- ==========================================
ALTER TABLE track_similarity ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON track_similarity FROM auth_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON track_similarity TO auth_role;
DROP POLICY IF EXISTS auth_role_all_track_similarity ON track_similarity;
CREATE POLICY auth_role_all_track_similarity ON track_similarity FOR ALL TO auth_role USING (true) WITH CHECK (true);

REVOKE ALL ON track_similarity FROM app_user;
GRANT SELECT ON track_similarity TO app_user;
DROP POLICY IF EXISTS track_similarity_select_all ON track_similarity;
CREATE POLICY track_similarity_select_all ON track_similarity FOR SELECT TO app_user USING (true);

ALTER TABLE search_docs ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON search_docs FROM auth_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON search_docs TO auth_role;
DROP POLICY IF EXISTS auth_role_all_search_docs ON search_docs;
CREATE POLICY auth_role_all_search_docs ON search_docs FOR ALL TO auth_role USING (true) WITH CHECK (true);

REVOKE ALL ON search_docs FROM app_user;
GRANT SELECT ON search_docs TO app_user;
DROP POLICY IF EXISTS search_docs_select_all ON search_docs;
CREATE POLICY search_docs_select_all ON search_docs FOR SELECT TO app_user USING (true);

-- ==========================================
-- 30. SEARCH QUERIES (Tenant Isolated)
-- ==========================================
ALTER TABLE search_queries ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON search_queries FROM auth_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON search_queries TO auth_role;
DROP POLICY IF EXISTS auth_role_all_search_queries ON search_queries;
CREATE POLICY auth_role_all_search_queries ON search_queries FOR ALL TO auth_role USING (true) WITH CHECK (true);

REVOKE ALL ON search_queries FROM app_user;
GRANT SELECT, INSERT, DELETE ON search_queries TO app_user;
DROP POLICY IF EXISTS search_queries_select_own ON search_queries;
CREATE POLICY search_queries_select_own ON search_queries FOR SELECT TO app_user
  USING (user_id = auth.uid());

DROP POLICY IF EXISTS search_queries_insert_own ON search_queries;
CREATE POLICY search_queries_insert_own ON search_queries FOR INSERT TO app_user
  WITH CHECK (user_id = auth.uid() OR user_id IS NULL);

DROP POLICY IF EXISTS search_queries_delete_own ON search_queries;
CREATE POLICY search_queries_delete_own ON search_queries FOR DELETE TO app_user
  USING (user_id = auth.uid());

-- ==========================================
-- 31. USER SETTINGS (Tenant Isolated)
-- ==========================================
ALTER TABLE user_settings ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON user_settings FROM auth_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON user_settings TO auth_role;
DROP POLICY IF EXISTS auth_role_all_user_settings ON user_settings;
CREATE POLICY auth_role_all_user_settings ON user_settings FOR ALL TO auth_role USING (true) WITH CHECK (true);

REVOKE ALL ON user_settings FROM app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON user_settings TO app_user;

DROP POLICY IF EXISTS user_settings_select_own ON user_settings;
CREATE POLICY user_settings_select_own ON user_settings FOR SELECT TO app_user
  USING (user_id = auth.uid());

DROP POLICY IF EXISTS user_settings_insert_own ON user_settings;
CREATE POLICY user_settings_insert_own ON user_settings FOR INSERT TO app_user
  WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS user_settings_update_own ON user_settings;
CREATE POLICY user_settings_update_own ON user_settings FOR UPDATE TO app_user
  USING (user_id = auth.uid())
  WITH CHECK (user_id = auth.uid());

DROP POLICY IF EXISTS user_settings_delete_own ON user_settings;
CREATE POLICY user_settings_delete_own ON user_settings FOR DELETE TO app_user
  USING (user_id = auth.uid());






