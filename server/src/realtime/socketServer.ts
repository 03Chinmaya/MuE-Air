/**
 * Socket.io Realtime Signaling Server
 * Handles room subscriptions, presence, and realtime messaging over a single WebSocket.
 * Strictly adheres to docs/00_STACK_LOCK.md Section 6, docs/05_API.md, and Phase 11B rules.
 */

import { Server as HttpServer } from 'node:http';
import { Server, Socket } from 'socket.io';
import { eq, and, isNull, asc } from 'drizzle-orm';
import { drizzle } from 'drizzle-orm/postgres-js';
import crypto from 'node:crypto';
import { verifyAccessToken } from '../services/authService.js';
import { db, pgClient } from '../db/connection.js';
import * as schema from '../db/schema.js';
import {
  listenParticipants,
  listenSessions,
  listenQueueItems,
  users,
} from '../db/schema.js';

async function withUserContext<T>(userId: string, fn: (reqDb: any) => Promise<T>): Promise<T> {
  return (await pgClient.begin(async (tx) => {
    (tx as any).options = (pgClient as any).options;
    await tx`
      SELECT set_config('request.jwt.claim.sub', ${userId}, true),
             set_config('request.jwt.claims', ${JSON.stringify({ sub: userId })}, true);
    `;
    const reqDb = drizzle(tx as any, { schema });
    return await fn(reqDb);
  })) as T;
}

let activeIo: Server | null = null;

export const MAX_PARTICIPANTS = 20;

export const DEFAULT_HOST_GRACE_MS = 60_000;
export function getHostGraceMs(): number {
  const parsed = Number(process.env.LISTEN_HOST_GRACE_MS);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : DEFAULT_HOST_GRACE_MS;
}
export const HOST_GRACE_MS = getHostGraceMs();

const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export const hostGraceTimers = new Map<string, NodeJS.Timeout>();

export function clearAllHostGraceTimers() {
  for (const timer of hostGraceTimers.values()) {
    clearTimeout(timer);
  }
  hostGraceTimers.clear();
}

export function getRealtimeServer(): Server | null {
  return activeIo;
}

export interface WSMessageEnvelope<T = unknown> {
  type: string;
  id?: string;
  room?: string;
  payload: T;
  timestamp: number;
}

// In-memory rate limiting map per socket: socketId -> { action -> { count, windowStart } }
const rateLimitMap = new Map<string, Map<string, { count: number; windowStart: number }>>();

function checkSocketRateLimit(socketId: string, action: string, limit: number, windowMs = 60_000): boolean {
  let socketLimits = rateLimitMap.get(socketId);
  if (!socketLimits) {
    socketLimits = new Map();
    rateLimitMap.set(socketId, socketLimits);
  }

  const now = Date.now();
  let actionRecord = socketLimits.get(action);
  if (!actionRecord || now - actionRecord.windowStart > windowMs) {
    actionRecord = { count: 1, windowStart: now };
    socketLimits.set(action, actionRecord);
    return true;
  }

  actionRecord.count += 1;
  return actionRecord.count <= limit;
}

function clearSocketRateLimits(socketId: string): void {
  rateLimitMap.delete(socketId);
}


// Broadcast updated collaborative queue to session room
export async function broadcastListenQueue(
  io: Server,
  dbInstance: any,
  sessionId: string
): Promise<void> {
  try {
    const queue = await dbInstance
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

    io.to(`listen:${sessionId}`).emit('listen_queue_updated', {
      type: 'listen_queue_updated',
      room: `listen:${sessionId}`,
      payload: { sessionId, queue },
      queue,
      timestamp: Date.now(),
    });
  } catch (err) {
    console.warn('[Realtime Server] Listen queue broadcast error:', err);
  }
}

export function initializeRealtimeServer(httpServer: HttpServer): Server {
  const allowedOrigins = process.env.ALLOWED_ORIGINS
    ? process.env.ALLOWED_ORIGINS.split(',').map((s) => s.trim())
    : null;

  const io = new Server(httpServer, {
    cors: {
      origin: (origin, callback) => {
        if (!origin) return callback(null, true);
        if (allowedOrigins) {
          if (allowedOrigins.includes(origin)) return callback(null, true);
          return callback(new Error('Origin not allowed by CORS'));
        }
        // Dev fallback matching HTTP middleware in Phase 8
        if (
          origin.startsWith('http://localhost') ||
          origin.startsWith('mue://')
        ) {
          return callback(null, true);
        }
        return callback(new Error('Origin not allowed by CORS'));
      },
      methods: ['GET', 'POST'],
    },
    pingTimeout: 30000,
    pingInterval: 10000,
  });

  activeIo = io;

  // Handshake authentication middleware
  io.use((socket, next) => {
    const token = socket.handshake.auth?.token;
    if (token) {
      const payload = verifyAccessToken(token);
      if (payload?.sub) {
        socket.data.userId = payload.sub;
        socket.data.deviceId = socket.handshake.auth?.deviceId || 'test-device';
      }
    }
    next();
  });

  io.on('connection', (socket: Socket) => {
    let unauthTimeout: NodeJS.Timeout | null = null;

    if (socket.data.userId) {
      const userId = socket.data.userId;
      const deviceId = socket.data.deviceId || 'test-device';
      socket.join(`user:${userId}`);
      socket.emit('auth_ok', {
        type: 'auth_ok',
        payload: { userId, deviceId },
        timestamp: Date.now(),
      });
    } else {
      // Rule: Unauthenticated sockets closed after 5s timeout
      unauthTimeout = setTimeout(() => {
        if (!socket.data.userId) {
          socket.emit('auth_fail', {
            type: 'auth_fail',
            payload: { code: 'auth_expired', message: 'Authentication timeout (5s)' },
            timestamp: Date.now(),
          });
          socket.disconnect(true);
        }
      }, 5000);
    }

    // 1. Auth Handler
    const authenticateSocket = async (msg: any) => {
      // Rate limit auth attempts: 3/min/socket
      if (!checkSocketRateLimit(socket.id, 'auth', 3)) {
        socket.emit('auth_fail', {
          type: 'auth_fail',
          payload: { code: 'auth_expired', message: 'Too many auth attempts' },
          timestamp: Date.now(),
        });
        socket.disconnect(true);
        return;
      }

      const token = msg?.payload?.token || msg?.token;
      const deviceId = msg?.payload?.deviceId || msg?.deviceId;

      if (!token) {
        socket.emit('auth_fail', {
          type: 'auth_fail',
          payload: { code: 'auth_expired', message: 'Token required' },
          timestamp: Date.now(),
        });
        return;
      }

      const payload = verifyAccessToken(token);
      if (!payload?.sub) {
        socket.emit('auth_fail', {
          type: 'auth_fail',
          payload: { code: 'auth_expired', message: 'Invalid or expired token' },
          timestamp: Date.now(),
        });
        socket.disconnect(true);
        return;
      }

      if (!deviceId) {
        socket.emit('auth_fail', {
          type: 'auth_fail',
          payload: { code: 'auth_expired', message: 'deviceId required' },
          timestamp: Date.now(),
        });
        return;
      }

      // Authenticated successfully
      if (unauthTimeout) clearTimeout(unauthTimeout);
      const userId = payload.sub;
      socket.data.userId = userId;
      socket.data.deviceId = deviceId;

      socket.join(`user:${userId}`);

      socket.emit('auth_ok', {
        type: 'auth_ok',
        payload: { userId, deviceId },
        timestamp: Date.now(),
      });
    };

    socket.on('auth', authenticateSocket);

    // Additive event: listen_join_room
    socket.on('listen_join_room', async (msg: any) => {
      const raw = (msg?.sessionId || msg?.joinCode || msg?.payload?.sessionId || msg?.payload?.joinCode || msg?.room?.replace('listen:', ''))?.trim();
      if (!raw) return;
      const userId = socket.data.userId;
      if (!userId) {
        socket.emit('error', {
          type: 'error',
          payload: { message: 'Unauthenticated socket' },
          timestamp: Date.now(),
        });
        return;
      }

      const isUuid = UUID_REGEX.test(raw);
      try {
        await withUserContext(userId, async (userDb) => {
          const [session] = await userDb
            .select()
            .from(listenSessions)
            .where(
              and(
                isUuid ? eq(listenSessions.id, raw) : eq(listenSessions.joinCode, raw.toUpperCase()),
                isNull(listenSessions.endedAt)
              )
            )
            .limit(1);

          if (!session) {
            socket.emit('error', {
              type: 'error',
              payload: { message: `Unauthorized or invalid room: listen:${raw}` },
              timestamp: Date.now(),
            });
            return;
          }

          socket.data.sessionId = session.id;
          socket.data.joinCode = session.joinCode;

          const isHost = session.hostUserId === userId;
          if (isHost && hostGraceTimers.has(session.id)) {
            clearTimeout(hostGraceTimers.get(session.id)!);
            hostGraceTimers.delete(session.id);
            const reconnPayload = { sessionId: session.id, joinCode: session.joinCode, userId };
            io.to(`listen:${session.id}`).emit('listen_host_reconnected', {
              type: 'listen_host_reconnected',
              room: `listen:${session.id}`,
              payload: reconnPayload,
              timestamp: Date.now(),
            });
            io.to(`listen:${session.joinCode}`).emit('listen_host_reconnected', {
              type: 'listen_host_reconnected',
              room: `listen:${session.joinCode}`,
              payload: reconnPayload,
              timestamp: Date.now(),
            });
          }

          const [participant] = await userDb
            .select({ sid: listenParticipants.sessionId })
            .from(listenParticipants)
            .where(
              and(
                eq(listenParticipants.sessionId, session.id),
                eq(listenParticipants.userId, userId),
                isNull(listenParticipants.leftAt)
              )
            )
            .limit(1);

          if (!isHost && !participant) {
            const activeCount = await userDb
              .select({ sid: listenParticipants.sessionId })
              .from(listenParticipants)
              .where(and(eq(listenParticipants.sessionId, session.id), isNull(listenParticipants.leftAt)));

            if (activeCount.length >= MAX_PARTICIPANTS) {
              socket.emit('error', {
                type: 'error',
                payload: { message: `Unauthorized or invalid room: listen:${raw}` },
                timestamp: Date.now(),
              });
              return;
            }

            await userDb
              .insert(listenParticipants)
              .values({
                sessionId: session.id,
                userId,
                joinedAt: new Date(),
                leftAt: null,
              });
          }

          socket.join(`listen:${session.id}`);
          socket.join(`listen:${session.joinCode}`);
          socket.emit('listen_participant_joined', { sessionId: session.id, joinCode: session.joinCode, userId });
        });
      } catch (err) {
        socket.emit('error', {
          type: 'error',
          payload: { message: `Unauthorized or invalid room: listen:${raw}` },
          timestamp: Date.now(),
        });
      }
    });

    // 2. Room Join Handler
    socket.on('join_room', async (msg: any) => {
      // Rate limit: 10/min/socket. Exceeding limit closes socket.
      if (!checkSocketRateLimit(socket.id, 'join_room', 10)) {
        socket.emit('error', {
          type: 'error',
          payload: { message: 'Rate limit exceeded for join_room' },
          timestamp: Date.now(),
        });
        socket.disconnect(true);
        return;
      }

      if (!socket.data.userId) {
        socket.emit('error', {
          type: 'error',
          payload: { message: 'Unauthenticated socket' },
          timestamp: Date.now(),
        });
        return;
      }

      const room = msg?.payload?.room || msg?.room;
      if (typeof room !== 'string') {
        socket.emit('error', {
          type: 'error',
          payload: { message: 'Invalid room format' },
          timestamp: Date.now(),
        });
        return;
      }

      const userId = socket.data.userId;
      let authorized = false;

      // Room validation rules:
      // user:<uuid> -> must match own userId
      // handoff:<uuid> -> must match own userId
      // chat:<uuid> -> check chat_participants
      // listen:<uuid> -> check listen_participants or listen_sessions
      if (room.startsWith('user:')) {
        const targetUserId = room.slice(5);
        authorized = targetUserId === userId;
      } else if (room.startsWith('listen:')) {
        const rawCodeOrId = room.slice(7).trim();
        const isUuid = UUID_REGEX.test(rawCodeOrId);
        try {
          await withUserContext(userId, async (userDb) => {
            const [session] = await userDb
              .select()
              .from(listenSessions)
              .where(
                and(
                  isUuid
                    ? eq(listenSessions.id, rawCodeOrId)
                    : eq(listenSessions.joinCode, rawCodeOrId.toUpperCase()),
                  isNull(listenSessions.endedAt)
                )
              )
              .limit(1);

            if (!session) {
              authorized = false;
              return;
            }

            socket.data.sessionId = session.id;
            socket.data.joinCode = session.joinCode;

            // Host reconnect logic
            if (session.hostUserId === userId) {
              authorized = true;
              if (hostGraceTimers.has(session.id)) {
                clearTimeout(hostGraceTimers.get(session.id)!);
                hostGraceTimers.delete(session.id);
                const reconnPayload = { sessionId: session.id, joinCode: session.joinCode, userId };
                io.to(`listen:${session.id}`).emit('listen_host_reconnected', {
                  type: 'listen_host_reconnected',
                  room: `listen:${session.id}`,
                  payload: reconnPayload,
                  timestamp: Date.now(),
                });
                io.to(`listen:${session.joinCode}`).emit('listen_host_reconnected', {
                  type: 'listen_host_reconnected',
                  room: `listen:${session.joinCode}`,
                  payload: reconnPayload,
                  timestamp: Date.now(),
                });
              }
              return;
            }

            // Guest verification / join
            const [participant] = await userDb
              .select({ sid: listenParticipants.sessionId })
              .from(listenParticipants)
              .where(
                and(
                  eq(listenParticipants.sessionId, session.id),
                  eq(listenParticipants.userId, userId),
                  isNull(listenParticipants.leftAt)
                )
              )
              .limit(1);

            if (participant) {
              authorized = true;
            } else {
              // Auto-join guest if under limit
              const activeCount = await userDb
                .select({ sid: listenParticipants.sessionId })
                .from(listenParticipants)
                .where(and(eq(listenParticipants.sessionId, session.id), isNull(listenParticipants.leftAt)));

              if (activeCount.length < MAX_PARTICIPANTS) {
                await userDb
                  .insert(listenParticipants)
                  .values({
                    sessionId: session.id,
                    userId,
                    joinedAt: new Date(),
                    leftAt: null,
                  });
                authorized = true;
                socket.join(room);
                socket.join(`listen:${session.id}`);
                socket.join(`listen:${session.joinCode}`);

                const joinPayload = {
                  sessionId: session.id,
                  joinCode: session.joinCode,
                  userId,
                  role: 'guest',
                  joinedAt: Date.now(),
                };
                io.to(`listen:${session.id}`).emit('listen_together_participant_joined', {
                  type: 'listen_together_participant_joined',
                  room: `listen:${session.id}`,
                  payload: joinPayload,
                  timestamp: Date.now(),
                });
                io.to(`listen:${session.joinCode}`).emit('listen_together_participant_joined', {
                  type: 'listen_together_participant_joined',
                  room: `listen:${session.joinCode}`,
                  payload: joinPayload,
                  timestamp: Date.now(),
                });
                io.to(`listen:${session.id}`).emit('listen_participant_joined', {
                  type: 'listen_participant_joined',
                  room: `listen:${session.id}`,
                  payload: joinPayload,
                  timestamp: Date.now(),
                });
                io.to(`listen:${session.joinCode}`).emit('listen_participant_joined', {
                  type: 'listen_participant_joined',
                  room: `listen:${session.joinCode}`,
                  payload: joinPayload,
                  timestamp: Date.now(),
                });
              } else {
                authorized = false;
              }
            }
          });
        } catch (err) {
          console.warn('[SocketServer] Error in listen join_room auth:', err);
          authorized = false;
        }
      }

      if (!authorized) {
        socket.emit('error', {
          type: 'error',
          payload: { message: `Unauthorized or invalid room: ${room}` },
          timestamp: Date.now(),
        });
        return;
      }

      socket.join(room);
      if (socket.data.sessionId) socket.join(`listen:${socket.data.sessionId}`);
      if (socket.data.joinCode) socket.join(`listen:${socket.data.joinCode}`);
      socket.emit('room_joined', {
        type: 'room_joined',
        room,
        payload: { room, sessionId: socket.data.sessionId, joinCode: socket.data.joinCode },
        timestamp: Date.now(),
      });
    });

    // 3. Room Leave Handler
    socket.on('leave_room', (msg: any) => {
      // Rate limit: 30/min/socket
      if (!checkSocketRateLimit(socket.id, 'leave_room', 30)) {
        return;
      }

      const room = msg?.payload?.room || msg?.room;
      if (typeof room === 'string') {
        socket.leave(room);
        socket.emit('room_left', {
          type: 'room_left',
          room,
          payload: { room },
          timestamp: Date.now(),
        });
      }
    });






    // 6. Listen Together Socket Handlers (Phase 11D + A.4.1)
    const handleListenSync = async (msg: any) => {
      const userId = socket.data.userId;
      if (!userId) return;
      // 1 sync event per second per host rate limit
      if (!checkSocketRateLimit(socket.id, 'listen_sync', 1, 1000)) {
        return;
      }

      const rawId = msg?.sessionId || msg?.joinCode || socket.data.sessionId || socket.data.joinCode || msg?.payload?.sessionId || msg?.payload?.joinCode || msg?.room?.replace('listen:', '');
      if (!rawId) return;

      const trackId = msg?.trackId || msg?.payload?.trackId;
      const providerId = msg?.providerId || msg?.payload?.providerId || msg?.provider || msg?.payload?.provider;
      const positionMs = Number(msg?.positionMs ?? msg?.payload?.positionMs ?? 0);
      const playing = Boolean(msg?.playing ?? msg?.payload?.playing);

      const isUuid = UUID_REGEX.test(rawId);

      try {
        await withUserContext(userId, async (userDb) => {
          const [session] = await userDb
            .select()
            .from(listenSessions)
            .where(
              and(
                isUuid ? eq(listenSessions.id, rawId) : eq(listenSessions.joinCode, rawId.toUpperCase()),
                isNull(listenSessions.endedAt)
              )
            )
            .limit(1);

          if (!session) return;

          // Verify permission: host or active participant if open
          if (session.controlMode === 'host-only') {
            if (session.hostUserId !== userId) {
              socket.emit('error', {
                type: 'error',
                payload: { code: 'FORBIDDEN', message: 'Only host can sync in host-only mode' },
                timestamp: Date.now(),
              });
              return;
            }
          } else {
            if (session.hostUserId !== userId) {
              const [isPart] = await userDb
                .select()
                .from(listenParticipants)
                .where(
                  and(
                    eq(listenParticipants.sessionId, session.id),
                    eq(listenParticipants.userId, userId),
                    isNull(listenParticipants.leftAt)
                  )
                )
                .limit(1);
              if (!isPart) {
                socket.emit('error', {
                  type: 'error',
                  payload: { code: 'FORBIDDEN', message: 'Not an active participant' },
                  timestamp: Date.now(),
                });
                return;
              }
            }
          }

          await userDb
            .update(listenSessions)
            .set({
              currentProviderTrackId: trackId || session.currentProviderTrackId,
              currentProvider: providerId || session.currentProvider,
              positionMs,
              playing,
              updatedAt: new Date(),
            })
            .where(eq(listenSessions.id, session.id));

          const rawQueue = Array.isArray(msg?.queueSnapshot)
            ? msg.queueSnapshot
            : Array.isArray(msg?.payload?.queueSnapshot)
              ? msg.payload.queueSnapshot
              : [];
          const queueSnapshot = rawQueue
            .slice(0, 30)
            .filter((t: any) => t?.trackId)
            .map((t: any) => ({
              trackId: String(t.trackId),
              provider: String(t.provider || 'jiosaavn'),
              title: String(t.title || ''),
              artist: String(t.artist || ''),
              artworkUrl: t.artworkUrl ? String(t.artworkUrl) : '',
            }));

          const syncPayload = {
            sessionId: session.id,
            joinCode: session.joinCode,
            trackId: trackId || session.currentProviderTrackId,
            providerId: providerId || session.currentProvider,
            positionMs,
            playing,
            timestamp: Date.now(),
            queueSnapshot,
          };

          io.to(`listen:${session.id}`).emit('listen_together_sync', {
            type: 'listen_together_sync',
            room: `listen:${session.id}`,
            payload: syncPayload,
            timestamp: Date.now(),
          });
          io.to(`listen:${session.joinCode}`).emit('listen_together_sync', {
            type: 'listen_together_sync',
            room: `listen:${session.joinCode}`,
            payload: syncPayload,
            timestamp: Date.now(),
          });
          io.to(`listen:${session.id}`).emit('listen_sync', {
            type: 'listen_sync',
            room: `listen:${session.id}`,
            payload: syncPayload,
            timestamp: Date.now(),
          });
          io.to(`listen:${session.joinCode}`).emit('listen_sync', {
            type: 'listen_sync',
            room: `listen:${session.joinCode}`,
            payload: syncPayload,
            timestamp: Date.now(),
          });
        });
      } catch (err) {
        console.warn('[SocketServer] Error in listen sync:', err);
      }
    };

    socket.on('listen_together_sync', handleListenSync);
    socket.on('listen_sync', handleListenSync);

    const handleListenEnd = async (msg: any) => {
      const userId = socket.data.userId;
      if (!userId) return;
      const rawId = msg?.sessionId || msg?.joinCode || socket.data.sessionId || socket.data.joinCode || msg?.payload?.sessionId || msg?.payload?.joinCode || msg?.room?.replace('listen:', '');
      if (!rawId) return;

      const isUuid = UUID_REGEX.test(rawId);

      try {
        await withUserContext(userId, async (userDb) => {
          const [session] = await userDb
            .select()
            .from(listenSessions)
            .where(
              and(
                isUuid ? eq(listenSessions.id, rawId) : eq(listenSessions.joinCode, rawId.toUpperCase()),
                isNull(listenSessions.endedAt)
              )
            )
            .limit(1);

          if (!session) return;

          // Enforce ownership: only host can end
          if (session.hostUserId !== userId) {
            socket.emit('error', {
              type: 'error',
              payload: { code: 'FORBIDDEN', message: 'Only host can end the session' },
              timestamp: Date.now(),
            });
            return;
          }

          if (hostGraceTimers.has(session.id)) {
            clearTimeout(hostGraceTimers.get(session.id)!);
            hostGraceTimers.delete(session.id);
          }

          await userDb
            .update(listenSessions)
            .set({ endedAt: new Date() })
            .where(eq(listenSessions.id, session.id));

          const endPayload = { sessionId: session.id, joinCode: session.joinCode };
          io.to(`listen:${session.id}`).emit('listen_together_ended', {
            type: 'listen_together_ended',
            room: `listen:${session.id}`,
            payload: endPayload,
            timestamp: Date.now(),
          });
          io.to(`listen:${session.joinCode}`).emit('listen_together_ended', {
            type: 'listen_together_ended',
            room: `listen:${session.joinCode}`,
            payload: endPayload,
            timestamp: Date.now(),
          });
          io.to(`listen:${session.id}`).emit('listen_ended', {
            type: 'listen_ended',
            room: `listen:${session.id}`,
            payload: endPayload,
            timestamp: Date.now(),
          });
          io.to(`listen:${session.joinCode}`).emit('listen_ended', {
            type: 'listen_ended',
            room: `listen:${session.joinCode}`,
            payload: endPayload,
            timestamp: Date.now(),
          });
        });
      } catch (err) {
        console.warn('[SocketServer] Error in listen end:', err);
      }
    };

    socket.on('listen_together_end', handleListenEnd);
    socket.on('listen_ended', handleListenEnd);

    const handleListenLeave = async (msg: any) => {
      const userId = socket.data.userId;
      if (!userId) return;
      const sessionId = msg?.sessionId || msg?.payload?.sessionId || msg?.room?.replace('listen:', '');
      if (!sessionId) return;

      try {
        await withUserContext(userId, async (userDb) => {
          await userDb
            .delete(listenParticipants)
            .where(and(eq(listenParticipants.sessionId, sessionId), eq(listenParticipants.userId, userId)));

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
        });
      } catch (err) {
        console.warn('[SocketServer] Error in listen leave:', err);
      }
    };

    socket.on('listen_together_leave', handleListenLeave);
    socket.on('listen_participant_left', handleListenLeave);


    socket.on('sync_ping', (msg: any) => {
      const t0 = Number(msg?.t0 ?? msg?.payload?.t0 ?? Date.now());
      const roomId = msg?.roomId ?? msg?.sessionId ?? msg?.payload?.roomId ?? '';
      socket.emit('sync_pong', {
        t0,
        t1: Date.now(),
        roomId,
      });
      if (roomId) {
        socket.to(`listen:${roomId}`).emit('sync_ping', {
          t0,
          senderId: socket.data.userId,
          roomId,
        });
      }
    });

    socket.on('sync_pong', (msg: any) => {
      const roomId = msg?.roomId ?? msg?.sessionId ?? '';
      if (roomId) {
        socket.to(`listen:${roomId}`).emit('sync_pong', msg);
      }
    });

    socket.on('queue_update', (msg: any) => {
      const roomId = msg?.roomId ?? msg?.sessionId ?? '';
      if (roomId) {
        io.to(`listen:${roomId}`).emit('queue_update', msg);
      }
    });

    socket.on('listen_queue_add', async (msg: any) => {
      const userId = socket.data.userId;
      if (!userId) return;
      const rawId = msg?.sessionId || msg?.joinCode || socket.data.sessionId || socket.data.joinCode || msg?.payload?.sessionId || msg?.payload?.joinCode || msg?.room?.replace('listen:', '');
      const track = msg?.track || msg?.payload?.track || msg;
      if (!rawId || !track?.trackId) return;

      const isUuid = UUID_REGEX.test(rawId);

      try {
        await withUserContext(userId, async (userDb) => {
          const [session] = await userDb
            .select()
            .from(listenSessions)
            .where(
              and(
                isUuid ? eq(listenSessions.id, rawId) : eq(listenSessions.joinCode, rawId.toUpperCase()),
                isNull(listenSessions.endedAt)
              )
            )
            .limit(1);

          if (!session) return;

          if (session.controlMode === 'host-only' && session.hostUserId !== userId) {
            socket.emit('error', {
              type: 'error',
              payload: { code: 'FORBIDDEN', message: 'Only host can add to queue in host-only mode' },
              timestamp: Date.now(),
            });
            return;
          }

          if (session.hostUserId !== userId) {
            const [participant] = await userDb
              .select()
              .from(listenParticipants)
              .where(
                and(
                  eq(listenParticipants.sessionId, session.id),
                  eq(listenParticipants.userId, userId),
                  isNull(listenParticipants.leftAt)
                )
              )
              .limit(1);
            if (!participant) return;
          }

          const existingItems = await userDb
            .select({ position: listenQueueItems.position })
            .from(listenQueueItems)
            .where(eq(listenQueueItems.sessionId, session.id))
            .orderBy(asc(listenQueueItems.position));

          const nextPosition = existingItems.length > 0 ? existingItems[existingItems.length - 1].position + 1 : 1;

          await userDb.insert(listenQueueItems).values({
            sessionId: session.id,
            trackId: String(track.trackId),
            provider: String(track.provider || 'jiosaavn'),
            title: String(track.title || 'Unknown Title'),
            artist: track.artist ? String(track.artist) : null,
            artworkUrl: track.artworkUrl ? String(track.artworkUrl) : null,
            addedByUserId: userId,
            position: nextPosition,
            createdAt: new Date(),
          });

          await broadcastListenQueue(io, userDb, session.id);
          if (session.joinCode) {
            await broadcastListenQueue(io, userDb, session.joinCode);
          }
        });
      } catch (err) {
        console.warn('[SocketServer] Error in listen_queue_add:', err);
      }
    });

    socket.on('listen_queue_remove', async (msg: any) => {
      const userId = socket.data.userId;
      if (!userId) return;
      const rawId = msg?.sessionId || msg?.joinCode || socket.data.sessionId || socket.data.joinCode || msg?.payload?.sessionId || msg?.payload?.joinCode || msg?.room?.replace('listen:', '');
      const itemId = msg?.itemId || msg?.payload?.itemId;
      if (!rawId || !itemId) return;

      const isUuid = UUID_REGEX.test(rawId);

      try {
        await withUserContext(userId, async (userDb) => {
          const [session] = await userDb
            .select()
            .from(listenSessions)
            .where(
              and(
                isUuid ? eq(listenSessions.id, rawId) : eq(listenSessions.joinCode, rawId.toUpperCase()),
                isNull(listenSessions.endedAt)
              )
            )
            .limit(1);

          if (!session) return;

          const [item] = await userDb
            .select()
            .from(listenQueueItems)
            .where(and(eq(listenQueueItems.id, itemId), eq(listenQueueItems.sessionId, session.id)))
            .limit(1);

          if (!item) return;

          // Host veto OR guest removes own
          if (session.hostUserId !== userId && item.addedByUserId !== userId) {
            socket.emit('error', {
              type: 'error',
              payload: { code: 'FORBIDDEN', message: 'Cannot remove another user item' },
              timestamp: Date.now(),
            });
            return;
          }

          await userDb.delete(listenQueueItems).where(eq(listenQueueItems.id, itemId));
          await broadcastListenQueue(io, userDb, session.id);
          if (session.joinCode) {
            await broadcastListenQueue(io, userDb, session.joinCode);
          }
        });
      } catch (err) {
        console.warn('[SocketServer] Error in listen_queue_remove:', err);
      }
    });

    // Disconnect cleanup & host grace window
    socket.on('disconnect', async () => {
      if (unauthTimeout) clearTimeout(unauthTimeout);
      clearSocketRateLimits(socket.id);

      const userId = socket.data.userId;
      if (!userId) return;

      // Host disconnect: 60s grace period for active listen sessions
      try {
          await withUserContext(userId, async (userDb) => {
            const hostedSessions = await userDb
              .select()
              .from(listenSessions)
              .where(
                and(
                  eq(listenSessions.hostUserId, userId),
                  isNull(listenSessions.endedAt)
                )
              );

            const graceMs = getHostGraceMs();
            const graceSeconds = graceMs >= 1000 ? Math.round(graceMs / 1000) : Number((graceMs / 1000).toFixed(2));

            for (const session of hostedSessions) {
              const dcPayload = {
                sessionId: session.id,
                joinCode: session.joinCode,
                graceSeconds,
              };
              io.to(`listen:${session.id}`).emit('listen_host_disconnected', {
                type: 'listen_host_disconnected',
                room: `listen:${session.id}`,
                payload: dcPayload,
                timestamp: Date.now(),
              });
              io.to(`listen:${session.joinCode}`).emit('listen_host_disconnected', {
                type: 'listen_host_disconnected',
                room: `listen:${session.joinCode}`,
                payload: dcPayload,
                timestamp: Date.now(),
              });

              if (hostGraceTimers.has(session.id)) {
                clearTimeout(hostGraceTimers.get(session.id)!);
              }

              const timer = setTimeout(async () => {
                hostGraceTimers.delete(session.id);
                try {
                  await withUserContext(userId, async (cleanupDb) => {
                    await cleanupDb
                      .update(listenSessions)
                      .set({ endedAt: new Date() })
                      .where(and(eq(listenSessions.id, session.id), isNull(listenSessions.endedAt)));

                    const endPayload = { sessionId: session.id, joinCode: session.joinCode, reason: 'host_disconnected_timeout' };
                    io.to(`listen:${session.id}`).emit('listen_together_ended', {
                      type: 'listen_together_ended',
                      room: `listen:${session.id}`,
                      payload: endPayload,
                      timestamp: Date.now(),
                    });
                    io.to(`listen:${session.joinCode}`).emit('listen_together_ended', {
                      type: 'listen_together_ended',
                      room: `listen:${session.joinCode}`,
                      payload: endPayload,
                      timestamp: Date.now(),
                    });
                    io.to(`listen:${session.id}`).emit('listen_ended', {
                      type: 'listen_ended',
                      room: `listen:${session.id}`,
                      payload: endPayload,
                      timestamp: Date.now(),
                    });
                    io.to(`listen:${session.joinCode}`).emit('listen_ended', {
                      type: 'listen_ended',
                      room: `listen:${session.joinCode}`,
                      payload: endPayload,
                      timestamp: Date.now(),
                    });
                  });
                } catch (err) {
                  console.warn('[SocketServer] Grace timeout end error:', err);
                }
              }, graceMs);
              if (timer.unref) timer.unref();
              hostGraceTimers.set(session.id, timer);
            }
          });
        } catch (err) {
          console.warn('[SocketServer] Host disconnect grace error:', err);
        }
    });
  });

  return io;
}
