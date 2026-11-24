import { safeError } from '../lib/errors.js';
/**
 * Listen Together API Routes (Hono)
 * Manages synchronized listening sessions and participants.
 * Strictly adheres to docs/00_STACK_LOCK.md Section 6, docs/05_API.md Section 22,
 * docs/28_CHAT.md Section 6.2/9, and Phase 11D specifications.
 */

import { Hono } from 'hono';
import { eq, and, isNull, asc } from 'drizzle-orm';
import { listenSessions, listenParticipants, listenQueueItems, users } from '../db/schema.js';
import { requireAuthOrGuest } from '../middleware/honoAuth.js';
import { AppEnv } from '../types.js';
import { getRealtimeServer, broadcastListenQueue, MAX_PARTICIPANTS } from '../realtime/socketServer.js';
import { generateUniqueJoinCode } from '../services/listenService.js';

export const listenRoutes = new Hono<AppEnv>();

listenRoutes.use('*', requireAuthOrGuest);

// ==========================================
// 1. POST /api/listen/sessions (Host creates session)
// ==========================================
listenRoutes.post('/sessions', async (c) => {
  const db = c.get('db');
  const userId = c.get('userId');
  try {
    const body = await c.req.json().catch(() => ({}));
    const {
      trackId,
      provider = 'jiosaavn',
      startPositionMs = 0,
      currentProvider = provider,
      currentProviderTrackId = trackId,
      positionMs = startPositionMs,
      isPlaying = true,
      playing = isPlaying,
      controlMode = 'open',
    } = body;

    const joinCode = await generateUniqueJoinCode(db);

    const [session] = await db
      .insert(listenSessions)
      .values({
        joinCode,
        hostUserId: userId,
        currentProvider: currentProvider || 'jiosaavn',
        currentProviderTrackId: currentProviderTrackId || '',
        positionMs: Number(positionMs) || 0,
        playing: Boolean(playing),
        controlMode: controlMode === 'host-only' ? 'host-only' : 'open',
        createdAt: new Date(),
        updatedAt: new Date(),
      })
      .returning();

    // Automatically add host as participant
    await db
      .insert(listenParticipants)
      .values({
        sessionId: session.id,
        userId,
        joinedAt: new Date(),
      })
      .onConflictDoNothing();


    return c.json(
      {
        ...session,
        sessionId: session.id,
        joinCode: session.joinCode,
        hostToken: session.id,
      },
      201
    );
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
});

// ==========================================
// 1b. GET /api/listen/join/:joinCode (Resolve state by join code)
// ==========================================
listenRoutes.get('/join/:joinCode', async (c) => {
  const db = c.get('db');
  const joinCode = c.req.param('joinCode')?.trim().toUpperCase();
  if (!joinCode) {
    return c.json({ error: 'Join code required' }, 400);
  }
  try {
    const [session] = await db
      .select()
      .from(listenSessions)
      .where(and(eq(listenSessions.joinCode, joinCode), isNull(listenSessions.endedAt)))
      .limit(1);

    if (!session) {
      return c.json({ error: 'Session not found or ended' }, 404);
    }

    const participants = await db
      .select({
        userId: listenParticipants.userId,
        sessionId: listenParticipants.sessionId,
        joinedAt: listenParticipants.joinedAt,
        leftAt: listenParticipants.leftAt,
        username: users.username,
        displayName: users.displayName,
        avatarUrl: users.avatarUrl,
      })
      .from(listenParticipants)
      .leftJoin(users, eq(users.id, listenParticipants.userId))
      .where(and(eq(listenParticipants.sessionId, session.id), isNull(listenParticipants.leftAt)));

    return c.json({
      ...session,
      sessionId: session.id,
      participants,
    });
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
});

// ==========================================
// 2. GET /api/listen/sessions/:id (State snapshot)
// ==========================================
listenRoutes.get('/sessions/:id', async (c) => {
  const db = c.get('db');
  const sessionId = c.req.param('id');
  try {
    const [session] = await db
      .select()
      .from(listenSessions)
      .where(eq(listenSessions.id, sessionId))
      .limit(1);

    if (!session || session.endedAt) {
      return c.json({ error: 'Session not found or ended' }, 404);
    }

    const participants = await db
      .select({
        userId: listenParticipants.userId,
        sessionId: listenParticipants.sessionId,
        joinedAt: listenParticipants.joinedAt,
        leftAt: listenParticipants.leftAt,
        username: users.username,
        displayName: users.displayName,
        avatarUrl: users.avatarUrl,
      })
      .from(listenParticipants)
      .leftJoin(users, eq(users.id, listenParticipants.userId))
      .where(and(eq(listenParticipants.sessionId, sessionId), isNull(listenParticipants.leftAt)));

    return c.json({
      ...session,
      participants,
    });
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
});

// Helper for joining session by UUID or 6-char join code
async function handleJoinSession(c: any, rawParam: string | undefined) {
  const db = c.get('db');
  const userId = c.get('userId');
  const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
  const codeRegex = /^[2-9a-hj-np-z]{6}$/i;
  if (!rawParam || (!uuidRegex.test(rawParam) && !codeRegex.test(rawParam))) {
    return c.json({ error: 'Invalid session ID' }, 400);
  }
  try {
    // 1. Verify session exists and is not ended (supports UUID or joinCode)
    const isUuid = uuidRegex.test(rawParam);
    const [session] = await db
      .select()
      .from(listenSessions)
      .where(
        and(
          isUuid ? eq(listenSessions.id, rawParam) : eq(listenSessions.joinCode, rawParam.toUpperCase()),
          isNull(listenSessions.endedAt)
        )
      )
      .limit(1);

    if (!session) {
      return c.json({ error: 'Session not found or ended' }, 404);
    }

    const sessionId = session.id;

    // 2. Check participant limit: max 20 per session per Section 22.5
    const existing = await db
      .select()
      .from(listenParticipants)
      .where(and(eq(listenParticipants.sessionId, sessionId), isNull(listenParticipants.leftAt)));

    const alreadyJoined = existing.some((p: any) => p.userId === userId);
    if (!alreadyJoined && existing.length >= MAX_PARTICIPANTS) {
      return c.json({ error: 'Session is full (max 20 participants)' }, 400);
    }

    // 3. Insert participant if not already joined
    if (!alreadyJoined) {
      await db
        .insert(listenParticipants)
        .values({
          sessionId,
          userId,
          joinedAt: new Date(),
          leftAt: null,
        });
    }

    // 4. Notify room via socket if active
    const io = getRealtimeServer();
    if (io) {
      const joinPayload = {
        sessionId,
        joinCode: session.joinCode,
        userId,
        role: 'guest',
        joinedAt: Date.now(),
      };
      io.to(`listen:${sessionId}`).emit('listen_together_participant_joined', {
        type: 'listen_together_participant_joined',
        room: `listen:${sessionId}`,
        payload: joinPayload,
        timestamp: Date.now(),
      });
      io.to(`listen:${session.joinCode}`).emit('listen_together_participant_joined', {
        type: 'listen_together_participant_joined',
        room: `listen:${session.joinCode}`,
        payload: joinPayload,
        timestamp: Date.now(),
      });
      io.to(`listen:${sessionId}`).emit('listen_participant_joined', {
        type: 'listen_participant_joined',
        room: `listen:${sessionId}`,
        payload: joinPayload,
        timestamp: Date.now(),
      });
      io.to(`listen:${session.joinCode}`).emit('listen_participant_joined', {
        type: 'listen_participant_joined',
        room: `listen:${session.joinCode}`,
        payload: joinPayload,
        timestamp: Date.now(),
      });
    }

    // Return current playback state
    return c.json({
      status: 'joined',
      sessionId: session.id,
      joinCode: session.joinCode,
      hostUserId: session.hostUserId,
      trackId: session.currentProviderTrackId,
      provider: session.currentProvider,
      positionMs: session.positionMs,
      playing: session.playing,
      updatedAt: session.updatedAt,
    });
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
}

// ==========================================
// 3. POST /api/listen/sessions/:id/join (Guest joins by UUID or join code)
// ==========================================
listenRoutes.post('/sessions/:id/join', async (c) => {
  return handleJoinSession(c, c.req.param('id'));
});

// ==========================================
// 3b. POST /api/listen/join/:joinCode (Guest joins by join code)
// ==========================================
listenRoutes.post('/join/:joinCode', async (c) => {
  return handleJoinSession(c, c.req.param('joinCode'));
});

// ==========================================
// 4. POST /api/listen/sessions/:id/leave (Leave or end)
// ==========================================
listenRoutes.post('/sessions/:id/leave', async (c) => {
  const db = c.get('db');
  const userId = c.get('userId');
  const sessionId = c.req.param('id');
  try {
    const [session] = await db
      .select()
      .from(listenSessions)
      .where(eq(listenSessions.id, sessionId))
      .limit(1);

    if (!session) {
      return c.json({ error: 'Session not found' }, 404);
    }

    const io = getRealtimeServer();

    // If host leaves → end session (broadcast listen_together_ended)
    if (session.hostUserId === userId) {
      await db
        .update(listenSessions)
        .set({ endedAt: new Date() })
        .where(eq(listenSessions.id, sessionId));

      await db
        .delete(listenParticipants)
        .where(eq(listenParticipants.sessionId, sessionId));

      if (io) {
        io.to(`listen:${sessionId}`).emit('listen_together_ended', {
          type: 'listen_together_ended',
          room: `listen:${sessionId}`,
          payload: { sessionId },
          timestamp: Date.now(),
        });
        io.to(`listen:${sessionId}`).emit('listen_ended', {
          type: 'listen_ended',
          room: `listen:${sessionId}`,
          payload: { sessionId },
          timestamp: Date.now(),
        });
      }

      return c.json({ status: 'ended', isHost: true });
    }

    // Guest leaves
    await db
      .delete(listenParticipants)
      .where(and(eq(listenParticipants.sessionId, sessionId), eq(listenParticipants.userId, userId)));

    if (io) {
      io.to(`listen:${sessionId}`).emit('listen_together_participant_left', {
        type: 'listen_together_participant_left',
        room: `listen:${sessionId}`,
        payload: { sessionId, userId },
        timestamp: Date.now(),
      });
      io.to(`listen:${sessionId}`).emit('listen_participant_left', {
        type: 'listen_participant_left',
        room: `listen:${sessionId}`,
        payload: { sessionId, userId },
        timestamp: Date.now(),
      });
    }

    return c.json({ status: 'left', isHost: false });
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
});

// ==========================================
// 5. POST /api/listen/sessions/:id/sync (Host pushes state)
// ==========================================
listenRoutes.post('/sessions/:id/sync', async (c) => {
  const db = c.get('db');
  const userId = c.get('userId');
  const sessionId = c.req.param('id');
  try {
    const body = await c.req.json().catch(() => ({}));
    const { trackId, providerId, positionMs, playing, timestamp } = body;

    const [session] = await db
      .select()
      .from(listenSessions)
      .where(eq(listenSessions.id, sessionId))
      .limit(1);

    if (!session || session.endedAt) {
      return c.json({ error: 'Session not found or ended' }, 404);
    }

    // Check permission based on controlMode
    if (session.controlMode === 'host-only') {
      if (session.hostUserId !== userId) {
        return c.json({ error: 'Only session host can sync playback state in host-only mode' }, 403);
      }
    } else {
      // 'open' mode: host or any active participant can sync
      if (session.hostUserId !== userId) {
        const [isParticipant] = await db
          .select()
          .from(listenParticipants)
          .where(
            and(
              eq(listenParticipants.sessionId, sessionId),
              eq(listenParticipants.userId, userId),
              isNull(listenParticipants.leftAt)
            )
          )
          .limit(1);

        if (!isParticipant) {
          return c.json({ error: 'Only active session participants can sync playback state' }, 403);
        }
      }
    }

    const updated = await db
      .update(listenSessions)
      .set({
        currentProviderTrackId: trackId ?? session.currentProviderTrackId,
        currentProvider: providerId ?? session.currentProvider,
        positionMs: typeof positionMs === 'number' ? positionMs : session.positionMs,
        playing: typeof playing === 'boolean' ? playing : session.playing,
        updatedAt: new Date(),
      })
      .where(eq(listenSessions.id, sessionId))
      .returning();

    const io = getRealtimeServer();
    if (io) {
      const syncMsg = {
        sessionId,
        trackId: trackId ?? session.currentProviderTrackId,
        providerId: providerId ?? session.currentProvider,
        positionMs: typeof positionMs === 'number' ? positionMs : session.positionMs,
        playing: typeof playing === 'boolean' ? playing : session.playing,
        timestamp: timestamp || Date.now(),
      };

      io.to(`listen:${sessionId}`).emit('listen_together_sync', {
        type: 'listen_together_sync',
        room: `listen:${sessionId}`,
        payload: syncMsg,
        timestamp: Date.now(),
      });
      io.to(`listen:${sessionId}`).emit('listen_sync', {
        type: 'listen_sync',
        room: `listen:${sessionId}`,
        payload: syncMsg,
        timestamp: Date.now(),
      });
    }

    return c.json({
      status: 'synced',
      session: updated[0],
    });
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
});

// ==========================================
// 6. PATCH /api/listen/sessions/:id/mode (Host toggles open <-> host-only)
// ==========================================
listenRoutes.patch('/sessions/:id/mode', async (c) => {
  const db = c.get('db');
  const userId = c.get('userId');
  const sessionId = c.req.param('id');
  try {
    const body = await c.req.json().catch(() => ({}));
    const { controlMode } = body;

    if (controlMode !== 'open' && controlMode !== 'host-only') {
      return c.json({ error: "controlMode must be 'open' or 'host-only'" }, 400);
    }

    const [session] = await db
      .select()
      .from(listenSessions)
      .where(eq(listenSessions.id, sessionId))
      .limit(1);

    if (!session || session.endedAt) {
      return c.json({ error: 'Session not found or ended' }, 404);
    }

    if (session.hostUserId !== userId) {
      return c.json({ error: 'Only session host can change control mode' }, 403);
    }

    const [updated] = await db
      .update(listenSessions)
      .set({ controlMode, updatedAt: new Date() })
      .where(eq(listenSessions.id, sessionId))
      .returning();

    const io = getRealtimeServer();
    if (io) {
      io.to(`listen:${sessionId}`).emit('listen_together_mode_changed', {
        type: 'listen_together_mode_changed',
        room: `listen:${sessionId}`,
        payload: { sessionId, controlMode: updated.controlMode },
        timestamp: Date.now(),
      });
    }

    return c.json({ status: 'updated', controlMode: updated.controlMode });
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
});

// ==========================================
// 7. GET /api/listen/sessions/:id/queue (Fetch queue)
// ==========================================
listenRoutes.get('/sessions/:id/queue', async (c) => {
  const db = c.get('db');
  const sessionId = c.req.param('id');
  try {
    const queue = await db
      .select({
        id: listenQueueItems.id,
        sessionId: listenQueueItems.sessionId,
        trackId: listenQueueItems.trackId,
        provider: listenQueueItems.provider,
        title: listenQueueItems.title,
        artist: listenQueueItems.artist,
        artworkUrl: listenQueueItems.artworkUrl,
        addedByUserId: listenQueueItems.addedByUserId,
        position: listenQueueItems.position,
        createdAt: listenQueueItems.createdAt,
        addedByName: users.displayName,
        addedByAvatar: users.avatarUrl,
      })
      .from(listenQueueItems)
      .leftJoin(users, eq(users.id, listenQueueItems.addedByUserId))
      .where(eq(listenQueueItems.sessionId, sessionId))
      .orderBy(asc(listenQueueItems.position));

    return c.json({ queue });
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
});

// ==========================================
// 8. POST /api/listen/sessions/:id/queue (Add to queue)
// ==========================================
listenRoutes.post('/sessions/:id/queue', async (c) => {
  const db = c.get('db');
  const userId = c.get('userId');
  const sessionId = c.req.param('id');
  try {
    const body = await c.req.json().catch(() => ({}));
    const { trackId, provider = 'jiosaavn', title, artist, artworkUrl } = body;

    if (!trackId || !title) {
      return c.json({ error: 'trackId and title are required' }, 400);
    }

    const [session] = await db
      .select()
      .from(listenSessions)
      .where(eq(listenSessions.id, sessionId))
      .limit(1);

    if (!session || session.endedAt) {
      return c.json({ error: 'Session not found or ended' }, 404);
    }

    // Validate user is host or active participant
    if (session.hostUserId !== userId) {
      const [participant] = await db
        .select()
        .from(listenParticipants)
        .where(
          and(
            eq(listenParticipants.sessionId, sessionId),
            eq(listenParticipants.userId, userId),
            isNull(listenParticipants.leftAt)
          )
        )
        .limit(1);

      if (!participant) {
        return c.json({ error: 'Only active session participants can queue tracks' }, 403);
      }
    }

    const existingItems = await db
      .select({ position: listenQueueItems.position })
      .from(listenQueueItems)
      .where(eq(listenQueueItems.sessionId, sessionId))
      .orderBy(asc(listenQueueItems.position));

    const nextPosition = existingItems.length > 0 ? existingItems[existingItems.length - 1].position + 1 : 1;

    const [inserted] = await db
      .insert(listenQueueItems)
      .values({
        sessionId,
        trackId: String(trackId),
        provider: String(provider || 'jiosaavn'),
        title: String(title),
        artist: artist ? String(artist) : null,
        artworkUrl: artworkUrl ? String(artworkUrl) : null,
        addedByUserId: userId,
        position: nextPosition,
        createdAt: new Date(),
      })
      .returning();

    const io = getRealtimeServer();
    if (io) {
      await broadcastListenQueue(io, db, sessionId);
    }

    return c.json(inserted, 201);
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
});

// ==========================================
// 9. DELETE /api/listen/sessions/:id/queue/:itemId (Remove / Host Veto)
// ==========================================
listenRoutes.delete('/sessions/:id/queue/:itemId', async (c) => {
  const db = c.get('db');
  const userId = c.get('userId');
  const sessionId = c.req.param('id');
  const itemId = c.req.param('itemId');
  try {
    const [queueItem] = await db
      .select()
      .from(listenQueueItems)
      .where(
        and(
          eq(listenQueueItems.id, itemId),
          eq(listenQueueItems.sessionId, sessionId)
        )
      )
      .limit(1);

    if (!queueItem) {
      return c.json({ error: 'Queue item not found' }, 404);
    }

    const [session] = await db
      .select()
      .from(listenSessions)
      .where(eq(listenSessions.id, sessionId))
      .limit(1);

    if (!session || session.endedAt) {
      return c.json({ error: 'Session not found or ended' }, 404);
    }

    // Allow deletion IF user is hostUserId (Host Veto) OR user is addedByUserId (Guest removes own)
    const isHost = session.hostUserId === userId;
    const isAdder = queueItem.addedByUserId === userId;

    if (!isHost && !isAdder) {
      return c.json(
        { error: 'Forbidden: only the host or the user who queued the track can remove it' },
        403
      );
    }

    await db
      .delete(listenQueueItems)
      .where(eq(listenQueueItems.id, itemId));

    const io = getRealtimeServer();
    if (io) {
      await broadcastListenQueue(io, db, sessionId);
    }

    return c.json({ status: 'removed', itemId });
  } catch (err: any) {
    return c.json(safeError(err, c, 500), 500);
  }
});
