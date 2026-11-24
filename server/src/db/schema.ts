/**
 * MuE Cloud PostgreSQL Schema (Drizzle ORM)
 * Master schema per docs/04_DATABASE.md Sections 21-28 and docs/00_STACK_LOCK.md Section 4.
 */

import {
  pgTable,
  uuid,
  text,
  timestamp,
  boolean,
  integer,
  bigint,
  bigserial,
  index,
  uniqueIndex,
  primaryKey,
  jsonb,
  numeric,
  inet,
} from 'drizzle-orm/pg-core';

// ==========================================
// 21. Cloud Core Tables
// ==========================================

export const users = pgTable(
  'users',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    username: text('username').notNull().unique(),
    email: text('email').unique(),
    displayName: text('display_name').notNull(),
    avatarUrl: text('avatar_url'),
    bio: text('bio'),
    isPrivate: boolean('is_private').default(false),
    followerCount: integer('follower_count').default(0),
    followingCount: integer('following_count').default(0),
    passwordHash: text('password_hash'),
    pinHash: text('pin_hash'),
    gender: text('gender'),
    age: integer('age'),
    emailVerified: boolean('email_verified').default(false),
    emailVerifiedAt: timestamp('email_verified_at', { withTimezone: true }),
    twoFactorEnabled: boolean('two_factor_enabled').default(false),
    publicKey: text('public_key'),
    registrationId: integer('registration_id'),
    termsAcceptedAt: timestamp('terms_accepted_at', { withTimezone: true }),
    termsVersion: text('terms_version'),
    isGuest: boolean('is_guest').notNull().default(false),
    mergedInto: uuid('merged_into').references((): any => users.id),
    mergedAt: timestamp('merged_at', { withTimezone: true }),
    deviceFingerprint: text('device_fingerprint'),
    ipAtCreation: inet('ip_at_creation'),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
    lastLoginAt: timestamp('last_login_at', { withTimezone: true }),
    usernameChangedAt: timestamp('username_changed_at', { withTimezone: true }),
    usernameChangeCount: integer('username_change_count').default(0),
  },
  (table) => [
    index('idx_users_email').on(table.email),
    index('idx_users_username').on(table.username),
    index('idx_users_is_private').on(table.isPrivate),
    index('idx_users_merged_into').on(table.mergedInto),
    index('idx_users_device_fingerprint').on(table.deviceFingerprint),
  ]
);

export const usersPublic = pgTable('users_public', {
  id: uuid('id').primaryKey(),
  username: text('username').notNull(),
  displayName: text('display_name').notNull(),
  avatarUrl: text('avatar_url'),
  bio: text('bio'),
  isPrivate: boolean('is_private').default(false),
  followerCount: integer('follower_count').default(0),
  followingCount: integer('following_count').default(0),
  createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
});

export const userDevices = pgTable(
  'user_devices',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    deviceName: text('device_name').notNull(),
    platform: text('platform').notNull().default('android'),
    pushToken: text('push_token'),
    deviceFingerprintHash: text('device_fingerprint_hash'),
    lastSeenAt: timestamp('last_seen_at', { withTimezone: true }),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    index('idx_devices_user').on(table.userId),
    index('idx_user_devices_fingerprint').on(table.deviceFingerprintHash),
  ]
);

export const userSessions = pgTable(
  'user_sessions',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    deviceId: uuid('device_id').references(() => userDevices.id, { onDelete: 'cascade' }),
    refreshTokenHash: text('refresh_token_hash').notNull(),
    previousTokenHash: text('previous_token_hash'),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    expiresAt: timestamp('expires_at', { withTimezone: true }).notNull(),
    lastUsedAt: timestamp('last_used_at', { withTimezone: true }),
    revokedAt: timestamp('revoked_at', { withTimezone: true }),
  },
  (table) => [
    index('idx_sessions_user').on(table.userId),
    index('idx_sessions_token').on(table.refreshTokenHash),
  ]
);

export const signupVerificationOtps = pgTable(
  'signup_verification_otps',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    email: text('email').notNull(),
    username: text('username').notNull(),
    passwordHash: text('password_hash').notNull(),
    displayName: text('display_name').notNull(),
    otpHash: text('otp_hash').notNull(),
    attempts: integer('attempts').notNull().default(0),
    expiresAt: timestamp('expires_at', { withTimezone: true }).notNull(),
    usedAt: timestamp('used_at', { withTimezone: true }),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    index('idx_signup_otps_email').on(table.email),
    index('idx_signup_otps_expires').on(table.expiresAt),
  ]
);

export const passwordResetOtps = pgTable(
  'password_reset_otps',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    userId: uuid('user_id').references(() => users.id, { onDelete: 'cascade' }),
    email: text('email').notNull(),
    otpHash: text('otp_hash').notNull(),
    attempts: integer('attempts').notNull().default(0),
    expiresAt: timestamp('expires_at', { withTimezone: true }).notNull(),
    usedAt: timestamp('used_at', { withTimezone: true }),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    index('idx_reset_otps_user').on(table.userId),
    index('idx_reset_otps_email').on(table.email),
    index('idx_reset_otps_expires').on(table.expiresAt),
  ]
);

export const userProfiles = pgTable(
  'user_profiles',
  {
    userId: uuid('user_id').primaryKey().references(() => users.id, { onDelete: 'cascade' }),
    bio: text('bio'),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  }
);

// ==========================================
// 22. Cloud Social Tables
// ==========================================

export const follows = pgTable(
  'follows',
  {
    followerId: uuid('follower_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    followeeId: uuid('followee_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    primaryKey({ columns: [table.followerId, table.followeeId] }),
    index('idx_follows_followee').on(table.followeeId, table.createdAt),
    index('idx_follows_follower').on(table.followerId, table.createdAt),
  ]
);

export const followRequests = pgTable(
  'follow_requests',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    fromUserId: uuid('from_user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    toUserId: uuid('to_user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    status: text('status').notNull().default('pending'),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    expiresAt: timestamp('expires_at', { withTimezone: true }).notNull(),
  },
  (table) => [
    index('idx_follow_requests_to').on(table.toUserId, table.status, table.createdAt),
    index('idx_follow_requests_from').on(table.fromUserId, table.status, table.createdAt),
  ]
);

export const followedArtists = pgTable(
  'followed_artists',
  {
    userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    artistId: text('artist_id').notNull(),
    artistName: text('artist_name').notNull(),
    thumbnailUrl: text('thumbnail_url'),
    followedAt: timestamp('followed_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    primaryKey({ columns: [table.userId, table.artistId] }),
    index('idx_followed_artists_user').on(table.userId, table.followedAt),
  ]
);

export const blocks = pgTable(
  'blocks',
  {
    blockerId: uuid('blocker_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    blockedId: uuid('blocked_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    primaryKey({ columns: [table.blockerId, table.blockedId] }),
    index('idx_blocks_blocked').on(table.blockedId),
  ]
);

export const notifications = pgTable(
  'notifications',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    type: text('type').notNull(),
    actorId: uuid('actor_id').references(() => users.id, { onDelete: 'set null' }),
    targetId: uuid('target_id'),
    previewCiphertext: text('preview_ciphertext'),
    previewNonce: text('preview_nonce'),
    read: boolean('read').default(false),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    index('idx_notifications_user').on(table.userId, table.createdAt),
    index('idx_notifications_unread').on(table.userId, table.read),
  ]
);

// ==========================================
// 23. Cloud Library Tables
// ==========================================

export const userLikes = pgTable(
  'user_likes',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    provider: text('provider').notNull(),
    providerTrackId: text('provider_track_id').notNull(),
    title: text('title').notNull(),
    artistName: text('artist_name'),
    albumTitle: text('album_title'),
    artworkUrl: text('artwork_url'),
    durationMs: integer('duration_ms'),
    likedAt: timestamp('liked_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    index('idx_likes_user').on(table.userId, table.likedAt),
  ]
);

export const userPlaylists = pgTable(
  'user_playlists',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    name: text('name').notNull(),
    description: text('description'),
    artworkUrl: text('artwork_url'),
    visibility: text('visibility').default('public'),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    index('idx_playlists_user').on(table.userId),
    index('idx_playlists_visibility').on(table.visibility),
  ]
);

export const userPlaylistTracks = pgTable(
  'user_playlist_tracks',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    playlistId: uuid('playlist_id').notNull().references(() => userPlaylists.id, { onDelete: 'cascade' }),
    position: integer('position').notNull(),
    provider: text('provider').notNull(),
    providerTrackId: text('provider_track_id').notNull(),
    title: text('title').notNull(),
    artistName: text('artist_name'),
    albumTitle: text('album_title'),
    artworkUrl: text('artwork_url'),
    durationMs: integer('duration_ms'),
    addedAt: timestamp('added_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    index('idx_playlist_tracks_playlist').on(table.playlistId, table.position),
  ]
);

export const userPlayHistory = pgTable(
  'user_play_history',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    provider: text('provider').notNull(),
    providerTrackId: text('provider_track_id').notNull(),
    title: text('title'),
    artistName: text('artist_name'),
    albumTitle: text('album_title'),
    playedAt: timestamp('played_at', { withTimezone: true }).notNull().defaultNow(),
    durationPlayedMs: integer('duration_played_ms'),
    completed: boolean('completed').default(false),
    source: text('source'),
  },
  (table) => [
    index('idx_history_user_time').on(table.userId, table.playedAt),
  ]
);

export const userPreferences = pgTable(
  'user_preferences',
  {
    userId: uuid('user_id').primaryKey().references(() => users.id, { onDelete: 'cascade' }),
    favoriteGenres: text('favorite_genres').array(),
    favoriteArtists: text('favorite_artists').array(),
    preferredLanguages: text('preferred_languages').array(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  }
);

export const syncOperations = pgTable(
  'sync_operations',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    type: text('type').notNull(),
    entityType: text('entity_type').notNull(),
    entityId: text('entity_id').notNull(),
    payload: jsonb('payload'),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    processedAt: timestamp('processed_at', { withTimezone: true }),
    status: text('status').default('pending'),
  },
  (table) => [
    index('idx_sync_user').on(table.userId, table.createdAt),
  ]
);

// ==========================================
// 24. Cloud Chat Tables (Ciphertext Only)
// ==========================================

export const chatConversations = pgTable(
  'chat_conversations',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    type: text('type').notNull(), // 'direct' | 'group'
    title: text('title'),
    artworkUrl: text('artwork_url'),
    createdBy: uuid('created_by').references(() => users.id, { onDelete: 'set null' }),
    disappearingTtlMs: bigint('disappearing_ttl_ms', { mode: 'number' }).notNull().default(0),
    ttlSetByUserId: uuid('ttl_set_by_user_id').references(() => users.id, { onDelete: 'set null' }),
    ttlSetAt: timestamp('ttl_set_at', { withTimezone: true }),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
    lastMessageAt: timestamp('last_message_at', { withTimezone: true }),
  },
  (table) => [
    index('idx_conv_last_message').on(table.lastMessageAt),
  ]
);

export const chatParticipants = pgTable(
  'chat_participants',
  {
    conversationId: uuid('conversation_id').notNull().references(() => chatConversations.id, { onDelete: 'cascade' }),
    userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    role: text('role').default('member'),
    joinedAt: timestamp('joined_at', { withTimezone: true }).notNull().defaultNow(),
    lastReadAt: timestamp('last_read_at', { withTimezone: true }),
    muted: boolean('muted').default(false),
  },
  (table) => [
    primaryKey({ columns: [table.conversationId, table.userId] }),
    index('idx_participants_user').on(table.userId),
  ]
);

export const chatMessages = pgTable(
  'chat_messages',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    conversationId: uuid('conversation_id').notNull().references(() => chatConversations.id, { onDelete: 'cascade' }),
    senderId: uuid('sender_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    senderDeviceId: uuid('sender_device_id').references(() => userDevices.id, { onDelete: 'cascade' }),
    contentCiphertext: text('content_ciphertext').notNull(),
    contentNonce: text('content_nonce').notNull(),
    contentAlgorithm: text('content_algorithm').notNull().default('xchacha20-poly1305'),
    contentType: text('content_type').notNull().default('text'),
    metadataCiphertext: text('metadata_ciphertext'),
    metadataNonce: text('metadata_nonce'),
    replyToId: uuid('reply_to_id'),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    editedAt: timestamp('edited_at', { withTimezone: true }),
    deletedAt: timestamp('deleted_at', { withTimezone: true }),
  },
  (table) => [
    index('idx_messages_conv').on(table.conversationId, table.createdAt),
    index('idx_messages_sender').on(table.senderId),
  ]
);

export const chatMessageReads = pgTable(
  'chat_message_reads',
  {
    messageId: uuid('message_id').notNull().references(() => chatMessages.id, { onDelete: 'cascade' }),
    userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    readAt: timestamp('read_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    primaryKey({ columns: [table.messageId, table.userId] }),
  ]
);

export const chatReactions = pgTable(
  'chat_reactions',
  {
    messageId: uuid('message_id').notNull().references(() => chatMessages.id, { onDelete: 'cascade' }),
    userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    emoji: text('emoji').notNull(),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    primaryKey({ columns: [table.messageId, table.userId, table.emoji] }),
  ]
);

export const chatAttachments = pgTable(
  'chat_attachments',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    messageId: uuid('message_id').notNull().references(() => chatMessages.id, { onDelete: 'cascade' }),
    blobKey: text('blob_key').notNull(),
    fileSize: integer('file_size').notNull(),
    contentType: text('content_type'),
    encryptionAlgorithm: text('encryption_algorithm').notNull(),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    index('idx_attach_message').on(table.messageId),
  ]
);

export const chatReports = pgTable(
  'chat_reports',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    reporterId: uuid('reporter_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    reportedUserId: uuid('reported_user_id').references(() => users.id, { onDelete: 'set null' }),
    messageId: uuid('message_id').references(() => chatMessages.id, { onDelete: 'set null' }),
    reason: text('reason').notNull(),
    status: text('status').default('pending'),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    index('idx_reports_status').on(table.status, table.createdAt),
  ]
);

// ==========================================
// 25. Cloud E2E Key Directory
// ==========================================

export const e2eIdentityKeys = pgTable(
  'e2e_identity_keys',
  {
    userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    deviceId: uuid('device_id').notNull().references(() => userDevices.id, { onDelete: 'cascade' }),
    publicKey: text('public_key').notNull(),
    registrationId: integer('registration_id').notNull(),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    primaryKey({ columns: [table.userId, table.deviceId] }),
  ]
);

export const e2eSignedPrekeys = pgTable(
  'e2e_signed_prekeys',
  {
    userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    deviceId: uuid('device_id').notNull().references(() => userDevices.id, { onDelete: 'cascade' }),
    keyId: integer('key_id').notNull(),
    publicKey: text('public_key').notNull(),
    signature: text('signature').notNull(),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    primaryKey({ columns: [table.userId, table.deviceId, table.keyId] }),
  ]
);

export const e2eOneTimePrekeys = pgTable(
  'e2e_one_time_prekeys',
  {
    userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    deviceId: uuid('device_id').notNull().references(() => userDevices.id, { onDelete: 'cascade' }),
    keyId: integer('key_id').notNull(),
    publicKey: text('public_key').notNull(),
    used: boolean('used').default(false),
    usedAt: timestamp('used_at', { withTimezone: true }),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    primaryKey({ columns: [table.userId, table.deviceId, table.keyId] }),
    index('idx_prekeys_unused').on(table.userId, table.deviceId, table.used),
  ]
);

export const e2eSenderKeyDistributions = pgTable(
  'e2e_sender_key_distributions',
  {
    conversationId: uuid('conversation_id').notNull().references(() => chatConversations.id, { onDelete: 'cascade' }),
    senderUserId: uuid('sender_user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    senderDeviceId: uuid('sender_device_id').notNull().references(() => userDevices.id, { onDelete: 'cascade' }),
    recipientUserId: uuid('recipient_user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    recipientDeviceId: uuid('recipient_device_id').notNull().references(() => userDevices.id, { onDelete: 'cascade' }),
    distributionCiphertext: text('distribution_ciphertext').notNull(),
    distributionNonce: text('distribution_nonce').notNull(),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    primaryKey({
      columns: [
        table.conversationId,
        table.senderUserId,
        table.senderDeviceId,
        table.recipientUserId,
        table.recipientDeviceId,
      ],
    }),
  ]
);

// ==========================================
// 26. Cloud Handoff & Listen Together
// ==========================================

export const handoffSessions = pgTable(
  'handoff_sessions',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    sourceDeviceId: uuid('source_device_id').notNull().references(() => userDevices.id, { onDelete: 'cascade' }),
    targetDeviceId: uuid('target_device_id').references(() => userDevices.id, { onDelete: 'set null' }),
    status: text('status').notNull(), // 'pending' | 'accepted' | 'rejected' | 'completed'
    playbackState: jsonb('playback_state'),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
    expiresAt: timestamp('expires_at', { withTimezone: true }),
  },
  (table) => [
    index('idx_handoff_user').on(table.userId, table.createdAt),
  ]
);

export const listenSessions = pgTable(
  'listen_sessions',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    joinCode: text('join_code').notNull().unique(),
    hostUserId: uuid('host_user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    currentProvider: text('current_provider'),
    currentProviderTrackId: text('current_provider_track_id'),
    positionMs: integer('position_ms').default(0),
    playing: boolean('playing').default(false),
    controlMode: text('control_mode').notNull().default('open'),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
    endedAt: timestamp('ended_at', { withTimezone: true }),
  }
);

export const listenParticipants = pgTable(
  'listen_participants',
  {
    sessionId: uuid('session_id').notNull().references(() => listenSessions.id, { onDelete: 'cascade' }),
    userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    joinedAt: timestamp('joined_at', { withTimezone: true }).notNull().defaultNow(),
    leftAt: timestamp('left_at', { withTimezone: true }),
  },
  (table) => [
    primaryKey({ columns: [table.sessionId, table.userId] }),
  ]
);

export const listenQueueItems = pgTable(
  'listen_queue_items',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    sessionId: uuid('session_id').notNull().references(() => listenSessions.id, { onDelete: 'cascade' }),
    trackId: text('track_id').notNull(),
    provider: text('provider').notNull(),
    title: text('title').notNull(),
    artist: text('artist'),
    artworkUrl: text('artwork_url'),
    addedByUserId: uuid('added_by_user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    position: integer('position').notNull(),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    index('idx_listen_queue_session').on(table.sessionId, table.position),
  ]
);

// ==========================================
// 27. Cloud Aggregate Stats & Analytics
// ==========================================

export const songPlayStats = pgTable(
  'song_play_stats',
  {
    userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    provider: text('provider').notNull(),
    providerTrackId: text('provider_track_id').notNull(),
    playCount: integer('play_count').default(0),
    completionCount: integer('completion_count').default(0),
    skipCount: integer('skip_count').default(0),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    primaryKey({ columns: [table.userId, table.provider, table.providerTrackId] }),
  ]
);

export const artistPlayStats = pgTable(
  'artist_play_stats',
  {
    userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    artistName: text('artist_name').notNull(),
    playCount: integer('play_count').default(0),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    primaryKey({ columns: [table.userId, table.artistName] }),
  ]
);

export const genrePlayStats = pgTable(
  'genre_play_stats',
  {
    userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    genre: text('genre').notNull(),
    playCount: integer('play_count').default(0),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    primaryKey({ columns: [table.userId, table.genre] }),
  ]
);

export const analyticsEvents = pgTable(
  'analytics_events',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    userId: uuid('user_id').references(() => users.id, { onDelete: 'set null' }),
    eventType: text('event_type').notNull(),
    eventData: jsonb('event_data'),
    timestamp: timestamp('timestamp', { withTimezone: true }).notNull().defaultNow(),
  }
);

export const chatPushHintsSent = pgTable(
  'chat_push_hints_sent',
  {
    messageId: uuid('message_id').notNull(),
    deviceId: uuid('device_id').notNull(),
    sentAt: timestamp('sent_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    primaryKey({ columns: [table.messageId, table.deviceId] }),
    index('idx_push_hints_sent_at').on(table.sentAt),
  ]
);

export const trackSimilarity = pgTable('track_similarity', {
  id: uuid('id').primaryKey().defaultRandom(),
  trackId: text('track_id').notNull(),
  similarTrackId: text('similar_track_id').notNull(),
  score: numeric('score', { precision: 5, scale: 4 }).notNull(),
  reason: text('reason').notNull(),
  computedAt: timestamp('computed_at', { withTimezone: true }).notNull().defaultNow(),
});

export const searchDocs = pgTable(
  'search_docs',
  {
    id: bigserial('id', { mode: 'number' }).primaryKey(),
    entityType: text('entity_type').notNull(),
    entityId: uuid('entity_id').notNull(),
    provider: text('provider').notNull(),
    title: text('title').notNull(),
    subtitle: text('subtitle'),
    keywords: text('keywords'),
    aliases: text('aliases'),
    popularity: integer('popularity').notNull().default(0),
    availableForPlayback: boolean('available_for_playback').notNull().default(true),
  },
  (table) => [
    uniqueIndex('idx_search_docs_unique').on(table.entityType, table.entityId, table.provider),
  ]
);

export const searchQueries = pgTable(
  'search_queries',
  {
    id: bigserial('id', { mode: 'number' }).primaryKey(),
    userId: uuid('user_id'),
    normalizedQuery: text('normalized_query').notNull(),
    resultCount: integer('result_count'),
    tookMs: integer('took_ms'),
    localCount: integer('local_count'),
    remoteCount: integer('remote_count'),
    clickedEntityType: text('clicked_entity_type'),
    clickedEntityId: uuid('clicked_entity_id'),
    clickedPosition: integer('clicked_position'),
    clickedProvider: text('clicked_provider'),
    vibeOpened: boolean('vibe_opened').default(false),
    createdAt: timestamp('created_at', { withTimezone: true }).defaultNow(),
  },
  (table) => [
    index('idx_search_queries_norm').on(table.normalizedQuery),
    index('idx_search_queries_created').on(table.createdAt),
  ]
);

// ==========================================
// 26. User Music Integrations & Imported Playlists
// ==========================================

export const userMusicIntegrations = pgTable(
  'user_music_integrations',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    provider: text('provider').notNull(),
    encryptedRefreshToken: text('encrypted_refresh_token').notNull(),
    accessToken: text('access_token'),
    accessTokenExpiresAt: timestamp('access_token_expires_at', { withTimezone: true }),
    spotifyUserId: text('spotify_user_id'),
    email: text('email'),
    status: text('status').notNull().default('connected'),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    uniqueIndex('uniq_user_provider').on(table.userId, table.provider),
  ]
);

export const importedPlaylists = pgTable(
  'imported_playlists',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    provider: text('provider').notNull(),
    externalId: text('external_id').notNull(),
    title: text('title').notNull(),
    description: text('description'),
    thumbnailUrl: text('thumbnail_url'),
    trackCount: integer('track_count').notNull().default(0),
    matchedCount: integer('matched_count').notNull().default(0),
    lastSyncedAt: timestamp('last_synced_at', { withTimezone: true }),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    uniqueIndex('uniq_user_provider_ext').on(table.userId, table.provider, table.externalId),
  ]
);

export const importedPlaylistTracks = pgTable(
  'imported_playlist_tracks',
  {
    id: uuid('id').primaryKey().defaultRandom(),
    importedPlaylistId: uuid('imported_playlist_id').notNull().references(() => importedPlaylists.id, { onDelete: 'cascade' }),
    position: integer('position').notNull(),
    externalVideoId: text('external_video_id').notNull(),
    title: text('title').notNull(),
    artists: text('artists').array().notNull(),
    durationMs: integer('duration_ms'),
    thumbnailUrl: text('thumbnail_url'),
    jiosaavnTrackId: text('jiosaavn_track_id'),
    matchConfidence: numeric('match_confidence'),
    matchStatus: text('match_status').notNull().default('pending'),
    createdAt: timestamp('created_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    index('idx_playlist_pos').on(table.importedPlaylistId, table.position),
  ]
);

// ==========================================
// 27. Guest Merge & Sync Log
// ==========================================

export const guestMergeLog = pgTable(
  'guest_merge_log',
  {
    id: uuid('id').defaultRandom().primaryKey(),
    guestId: uuid('guest_id').notNull(),
    authId: uuid('auth_id').notNull(),
    deviceId: uuid('device_id'),
    status: text('status').notNull(),
    error: text('error'),
    clientBatchId: text('client_batch_id').unique(),
    mergedAt: timestamp('merged_at', { withTimezone: true }).defaultNow(),
  },
  (table) => [
    index('idx_guest_merge_log_guest').on(table.guestId),
  ]
);

// ==========================================
// 28. User Settings (Cloud Sync)
// ==========================================

export const userSettings = pgTable(
  'user_settings',
  {
    userId: uuid('user_id').notNull().references(() => users.id, { onDelete: 'cascade' }),
    key: text('key').notNull(),
    value: jsonb('value').notNull(),
    deviceId: text('device_id'), // null = synced across devices, non-null = device-specific
    updatedAt: timestamp('updated_at', { withTimezone: true }).notNull().defaultNow(),
  },
  (table) => [
    primaryKey({ columns: [table.userId, table.key] }),
    index('idx_user_settings_user').on(table.userId),
  ]
);




