import { describe, it, before, after } from 'node:test';
import assert from 'node:assert';
import http from 'node:http';
import crypto from 'node:crypto';
import { io as ClientSocket, Socket as ClientSocketType } from 'socket.io-client';
import { app } from '../src/app.js';
import { initializeRealtimeServer, clearAllHostGraceTimers } from '../src/realtime/socketServer.js';
import { db } from '../src/db/connection.js';
import { listenSessions, listenParticipants } from '../src/db/schema.js';
import { eq } from 'drizzle-orm';
import { createTestUser, cleanupTestUsers, sqlAuthRole } from './helpers/rls.js';
import { generateAccessToken } from '../src/services/authService.js';

describe('Phase 11D — Listen Together Specification Tests', () => {
  let hostUser: { id: string; username: string; email: string };
  let guestUser: { id: string; username: string; email: string };
  let hostToken: string;
  let guestToken: string;
  let server: http.Server;
  let ioServer: any;
  let serverUrl = '';

  before(async () => {
    const ts = Date.now();
    hostUser = await createTestUser(`listen_host_${ts}`, `host_${ts}@test.com`);
    guestUser = await createTestUser(`listen_guest_${ts}`, `guest_${ts}@test.com`);

    hostToken = generateAccessToken(hostUser.id, hostUser.username);
    guestToken = generateAccessToken(guestUser.id, guestUser.username);

    server = http.createServer();
    ioServer = initializeRealtimeServer(server);
    await new Promise<void>((resolve) => {
      server.listen(0, () => {
        const addr = server.address() as any;
        serverUrl = `http://localhost:${addr.port}`;
        resolve();
      });
    });
  });

  after(async () => {
    try {
      clearAllHostGraceTimers();
      if (ioServer) {
        await new Promise<void>((resolve) => {
          ioServer.close(() => {
            server.close(() => resolve());
          });
        });
      }
      await sqlAuthRole`DELETE FROM listen_sessions WHERE host_user_id IN (${hostUser.id}, ${guestUser.id})`;
      await cleanupTestUsers([hostUser.id, guestUser.id]);
    } catch {}
  });

  let createdSessionId = '';

  it('1. POST /sessions creates row + returns sessionId and host as participant', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/listen/sessions', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${hostToken}`,
        },
        body: JSON.stringify({
          trackId: 'track_alpha_123',
          provider: 'jiosaavn',
          startPositionMs: 5000,
        }),
      })
    );

    assert.strictEqual(res.status, 201);
    const data = (await res.json()) as any;
    assert.ok(data.sessionId, 'Must return sessionId');
    assert.ok(data.hostToken, 'Must return hostToken');
    assert.strictEqual(data.hostUserId, hostUser.id);
    assert.strictEqual(data.currentProviderTrackId, 'track_alpha_123');
    assert.strictEqual(data.positionMs, 5000);

    createdSessionId = data.sessionId;

    // Verify snapshot returns host in participants
    const snapRes = await app.fetch(
      new Request(`http://localhost:4000/api/listen/sessions/${createdSessionId}`, {
        headers: { Authorization: `Bearer ${hostToken}` },
      })
    );
    assert.strictEqual(snapRes.status, 200);
    const snapData = (await snapRes.json()) as any;
    assert.strictEqual(snapData.participants.length, 1);
    assert.strictEqual(snapData.participants[0].userId, hostUser.id);
  });

  it('2. join after full (>20) → 400 Bad Request', async () => {
    // 1. Create a full session with 20 dummy participant rows directly
    const fullSessionRes = await app.fetch(
      new Request('http://localhost:4000/api/listen/sessions', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${hostToken}`,
        },
        body: JSON.stringify({ trackId: 'track_full_test' }),
      })
    );
    const fullSession = (await fullSessionRes.json()) as any;
    const fullSessionId = fullSession.sessionId;

    // Create dummy users to insert 19 more participants (total 20 including host)
    const extraUsers: string[] = [];
    const ts = Date.now();
    for (let i = 0; i < 19; i++) {
      const u = await createTestUser(`extra_listen_${i}_${ts}`, `extra_${i}_${ts}@test.com`);
      extraUsers.push(u.id);
      await sqlAuthRole`
        INSERT INTO listen_participants (session_id, user_id, joined_at)
        VALUES (${fullSessionId}, ${u.id}, NOW())
      `;
    }

    try {
      // Now guestUser attempts to join (21st participant)
      const joinRes = await app.fetch(
        new Request(`http://localhost:4000/api/listen/sessions/${fullSessionId}/join`, {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${guestToken}`,
          },
        })
      );

      assert.strictEqual(joinRes.status, 400);
      const err = (await joinRes.json()) as any;
      assert.match(err.error, /full/i);
    } finally {
      await sqlAuthRole`DELETE FROM listen_sessions WHERE id = ${fullSessionId}`;
      await cleanupTestUsers(extraUsers);
    }
  });

  it('3. join after host left → 404 Not Found', async () => {
    // Host leaves / ends the session
    const leaveRes = await app.fetch(
      new Request(`http://localhost:4000/api/listen/sessions/${createdSessionId}/leave`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${hostToken}` },
      })
    );
    assert.strictEqual(leaveRes.status, 200);
    const leaveData = (await leaveRes.json()) as any;
    assert.strictEqual(leaveData.status, 'ended');
    assert.strictEqual(leaveData.isHost, true);

    // Guest tries to join ended session
    const joinRes = await app.fetch(
      new Request(`http://localhost:4000/api/listen/sessions/${createdSessionId}/join`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${guestToken}` },
      })
    );
    assert.strictEqual(joinRes.status, 404);
    const err = (await joinRes.json()) as any;
    assert.match(err.error, /ended|not found/i);
  });

  it('4. sync updates row + broadcasts state', async () => {
    // Host creates a fresh session
    const createRes = await app.fetch(
      new Request('http://localhost:4000/api/listen/sessions', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${hostToken}`,
        },
        body: JSON.stringify({ trackId: 'sync_test_track', provider: 'jiosaavn' }),
      })
    );
    const session = (await createRes.json()) as any;
    const sessId = session.sessionId;

    // Host pushes sync state
    const syncRes = await app.fetch(
      new Request(`http://localhost:4000/api/listen/sessions/${sessId}/sync`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${hostToken}`,
        },
        body: JSON.stringify({
          trackId: 'track_new_resolved',
          providerId: 'jiosaavn',
          positionMs: 42000,
          playing: true,
          timestamp: Date.now(),
        }),
      })
    );

    assert.strictEqual(syncRes.status, 200);
    const syncData = (await syncRes.json()) as any;
    assert.strictEqual(syncData.status, 'synced');
    assert.strictEqual(syncData.session.positionMs, 42000);
    assert.strictEqual(syncData.session.currentProviderTrackId, 'track_new_resolved');

    // Confirm updated state persisted in DB
    const snapRes = await app.fetch(
      new Request(`http://localhost:4000/api/listen/sessions/${sessId}`, {
        headers: { Authorization: `Bearer ${hostToken}` },
      })
    );
    const snap = (await snapRes.json()) as any;
    assert.strictEqual(snap.positionMs, 42000);
    assert.strictEqual(snap.playing, true);

    await sqlAuthRole`DELETE FROM listen_sessions WHERE id = ${sessId}`;
  });

  it('5. non-host cannot sync (returns 403 Forbidden)', async () => {
    // Host creates session
    const createRes = await app.fetch(
      new Request('http://localhost:4000/api/listen/sessions', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${hostToken}`,
        },
        body: JSON.stringify({ trackId: 'host_track', controlMode: 'host-only' }),
      })
    );
    const session = (await createRes.json()) as any;
    const sessId = session.sessionId;

    // Guest joins
    await app.fetch(
      new Request(`http://localhost:4000/api/listen/sessions/${sessId}/join`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${guestToken}` },
      })
    );

    // Guest attempts to sync
    const syncRes = await app.fetch(
      new Request(`http://localhost:4000/api/listen/sessions/${sessId}/sync`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${guestToken}`,
        },
        body: JSON.stringify({
          positionMs: 99999,
          playing: false,
        }),
      })
    );

    assert.strictEqual(syncRes.status, 403);
    const err = (await syncRes.json()) as any;
    assert.match(err.error, /only.*host.*sync/i);

    await sqlAuthRole`DELETE FROM listen_sessions WHERE id = ${sessId}`;
  });

  it('6. participant count enforced (guest join adds participant)', async () => {
    const createRes = await app.fetch(
      new Request('http://localhost:4000/api/listen/sessions', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${hostToken}`,
        },
        body: JSON.stringify({ trackId: 'count_test' }),
      })
    );
    const session = (await createRes.json()) as any;
    const sessId = session.sessionId;

    // Initially 1 participant (host)
    let snapRes = await app.fetch(
      new Request(`http://localhost:4000/api/listen/sessions/${sessId}`, {
        headers: { Authorization: `Bearer ${hostToken}` },
      })
    );
    let snap = (await snapRes.json()) as any;
    assert.strictEqual(snap.participants.length, 1);

    // Guest joins
    const joinRes = await app.fetch(
      new Request(`http://localhost:4000/api/listen/sessions/${sessId}/join`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${guestToken}` },
      })
    );
    assert.strictEqual(joinRes.status, 200);

    // Now 2 participants
    snapRes = await app.fetch(
      new Request(`http://localhost:4000/api/listen/sessions/${sessId}`, {
        headers: { Authorization: `Bearer ${guestToken}` },
      })
    );
    snap = (await snapRes.json()) as any;
    assert.strictEqual(snap.participants.length, 2);

    await sqlAuthRole`DELETE FROM listen_sessions WHERE id = ${sessId}`;
  });

  it('7. session cleanup on host leave (guest leaving preserves session, host leaving ends session)', async () => {
    const createRes = await app.fetch(
      new Request('http://localhost:4000/api/listen/sessions', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${hostToken}`,
        },
        body: JSON.stringify({ trackId: 'cleanup_test' }),
      })
    );
    const session = (await createRes.json()) as any;
    const sessId = session.sessionId;

    // Guest joins
    await app.fetch(
      new Request(`http://localhost:4000/api/listen/sessions/${sessId}/join`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${guestToken}` },
      })
    );

    // Guest leaves -> session still active, participants = 1
    const guestLeaveRes = await app.fetch(
      new Request(`http://localhost:4000/api/listen/sessions/${sessId}/leave`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${guestToken}` },
      })
    );
    assert.strictEqual(guestLeaveRes.status, 200);
    const guestLeave = (await guestLeaveRes.json()) as any;
    assert.strictEqual(guestLeave.status, 'left');
    assert.strictEqual(guestLeave.isHost, false);

    let snapRes = await app.fetch(
      new Request(`http://localhost:4000/api/listen/sessions/${sessId}`, {
        headers: { Authorization: `Bearer ${hostToken}` },
      })
    );
    assert.strictEqual(snapRes.status, 200);
    let snap = (await snapRes.json()) as any;
    assert.strictEqual(snap.participants.length, 1);

    // Host leaves -> session ended, participants deleted
    const hostLeaveRes = await app.fetch(
      new Request(`http://localhost:4000/api/listen/sessions/${sessId}/leave`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${hostToken}` },
      })
    );
    assert.strictEqual(hostLeaveRes.status, 200);
    const hostLeave = (await hostLeaveRes.json()) as any;
    assert.strictEqual(hostLeave.status, 'ended');
    assert.strictEqual(hostLeave.isHost, true);

    // Verify session marked as ended
    snapRes = await app.fetch(
      new Request(`http://localhost:4000/api/listen/sessions/${sessId}`, {
        headers: { Authorization: `Bearer ${hostToken}` },
      })
    );
    assert.strictEqual(snapRes.status, 404, 'Session should return 404 after being ended');

    await sqlAuthRole`DELETE FROM listen_sessions WHERE id = ${sessId}`;
  });

  it('7. bidirectional control: guest sync succeeds in open mode, 403 in host-only', async () => {
    // 1. Host creates session with default 'open' control mode
    const createRes = await app.fetch(
      new Request('http://localhost:4000/api/listen/sessions', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${hostToken}`,
        },
        body: JSON.stringify({ trackId: 'track_bidi_test', controlMode: 'open' }),
      })
    );
    assert.strictEqual(createRes.status, 201);
    const { sessionId } = (await createRes.json()) as any;

    // 2. Guest joins session
    const joinRes = await app.fetch(
      new Request(`http://localhost:4000/api/listen/sessions/${sessionId}/join`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${guestToken}` },
      })
    );
    assert.strictEqual(joinRes.status, 200);

    // 3. In open mode: guest CAN sync playback state
    const guestSyncRes = await app.fetch(
      new Request(`http://localhost:4000/api/listen/sessions/${sessionId}/sync`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${guestToken}`,
        },
        body: JSON.stringify({ trackId: 'track_guest_played', positionMs: 15000, playing: true }),
      })
    );
    assert.strictEqual(guestSyncRes.status, 200);
    const syncData = (await guestSyncRes.json()) as any;
    assert.strictEqual(syncData.status, 'synced');
    assert.strictEqual(syncData.session.currentProviderTrackId, 'track_guest_played');

    // 4. Guest tries to toggle mode -> 403 (host only)
    const guestToggleRes = await app.fetch(
      new Request(`http://localhost:4000/api/listen/sessions/${sessionId}/mode`, {
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${guestToken}`,
        },
        body: JSON.stringify({ controlMode: 'host-only' }),
      })
    );
    assert.strictEqual(guestToggleRes.status, 403);

    // 5. Host toggles mode to host-only -> 200
    const hostToggleRes = await app.fetch(
      new Request(`http://localhost:4000/api/listen/sessions/${sessionId}/mode`, {
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${hostToken}`,
        },
        body: JSON.stringify({ controlMode: 'host-only' }),
      })
    );
    assert.strictEqual(hostToggleRes.status, 200);
    const modeData = (await hostToggleRes.json()) as any;
    assert.strictEqual(modeData.controlMode, 'host-only');

    // 6. In host-only mode: guest sync fails -> 403
    const guestBlockedSyncRes = await app.fetch(
      new Request(`http://localhost:4000/api/listen/sessions/${sessionId}/sync`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${guestToken}`,
        },
        body: JSON.stringify({ trackId: 'track_guest_blocked', positionMs: 25000, playing: true }),
      })
    );
    assert.strictEqual(guestBlockedSyncRes.status, 403);

    // 7. Host sync succeeds in host-only mode -> 200
    const hostSyncRes = await app.fetch(
      new Request(`http://localhost:4000/api/listen/sessions/${sessionId}/sync`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${hostToken}`,
        },
        body: JSON.stringify({ trackId: 'track_host_override', positionMs: 30000, playing: true }),
      })
    );
    assert.strictEqual(hostSyncRes.status, 200);

    await sqlAuthRole`DELETE FROM listen_sessions WHERE id = ${sessionId}`;
  });

  it('15. listen_chat_message rejects text over 500 chars', async () => {
    const sessionId = crypto.randomUUID();
    const joinCode = 'TS15JC';
    await sqlAuthRole`
      INSERT INTO listen_sessions (id, join_code, host_user_id, control_mode, current_provider, current_provider_track_id, position_ms, playing)
      VALUES (${sessionId}, ${joinCode}, ${hostUser.id}, 'open', 'jiosaavn', 'track_curr', 0, true)
    `;
    await sqlAuthRole`
      INSERT INTO listen_participants (session_id, user_id, joined_at)
      VALUES (${sessionId}, ${guestUser.id}, NOW())
    `;

    const socketHost: ClientSocketType = ClientSocket(serverUrl, { transports: ['websocket'], reconnection: false });
    const socketGuest: ClientSocketType = ClientSocket(serverUrl, { transports: ['websocket'], reconnection: false });

    try {
      await Promise.all([
        new Promise<void>((resolve) => {
          socketHost.on('connect', () => {
            socketHost.emit('auth', { type: 'auth', payload: { token: hostToken, deviceId: 'dev_host_15' }, timestamp: Date.now() });
          });
          socketHost.on('auth_ok', () => resolve());
        }),
        new Promise<void>((resolve) => {
          socketGuest.on('connect', () => {
            socketGuest.emit('auth', { type: 'auth', payload: { token: guestToken, deviceId: 'dev_guest_15' }, timestamp: Date.now() });
          });
          socketGuest.on('auth_ok', () => resolve());
        }),
      ]);

      const room = `listen:${sessionId}`;
      await Promise.all([
        new Promise<void>((resolve) => socketHost.once('room_joined', () => resolve())),
        new Promise<void>((resolve) => socketGuest.once('room_joined', () => resolve())),
        (async () => {
          socketHost.emit('join_room', { room });
          socketGuest.emit('join_room', { room });
        })(),
      ]);

      let receivedChat: any = null;
      socketGuest.on('listen_chat_message', (msg: any) => {
        receivedChat = msg;
      });

      // 1. Text over 500 chars -> should be rejected (never received)
      socketHost.emit('listen_chat_message', {
        sessionId,
        text: 'x'.repeat(501),
      });
      await new Promise((r) => setTimeout(r, 200));
      assert.strictEqual(receivedChat, null, 'Chat text over 500 characters must be rejected');

      // 2. Valid text -> should be received
      const chatRelayedPromise = new Promise<any>((resolve) => {
        socketGuest.once('listen_chat_message', resolve);
      });
      socketHost.emit('listen_chat_message', {
        sessionId,
        text: 'Hello from host!',
      });
      receivedChat = await chatRelayedPromise;
      assert.ok(receivedChat, 'Valid chat message must be relayed');
      assert.strictEqual(receivedChat.payload.text, 'Hello from host!');
    } finally {
      socketHost.close();
      socketGuest.close();
      await sqlAuthRole`DELETE FROM listen_participants WHERE session_id = ${sessionId}`.catch(() => {});
      await sqlAuthRole`DELETE FROM listen_sessions WHERE id = ${sessionId}`.catch(() => {});
    }
  });

  it('16. listen_chat_message rate limits 5 per second per socket', async () => {
    const sessionId = crypto.randomUUID();
    const joinCode = 'TS16JC';
    await sqlAuthRole`
      INSERT INTO listen_sessions (id, join_code, host_user_id, control_mode, current_provider, current_provider_track_id, position_ms, playing)
      VALUES (${sessionId}, ${joinCode}, ${hostUser.id}, 'open', 'jiosaavn', 'track_curr', 0, true)
    `;
    await sqlAuthRole`
      INSERT INTO listen_participants (session_id, user_id, joined_at)
      VALUES (${sessionId}, ${guestUser.id}, NOW())
    `;

    const socketHost: ClientSocketType = ClientSocket(serverUrl, { transports: ['websocket'], reconnection: false });
    const socketGuest: ClientSocketType = ClientSocket(serverUrl, { transports: ['websocket'], reconnection: false });

    try {
      await Promise.all([
        new Promise<void>((resolve) => {
          socketHost.on('connect', () => {
            socketHost.emit('auth', { type: 'auth', payload: { token: hostToken, deviceId: 'dev_host_16' }, timestamp: Date.now() });
          });
          socketHost.on('auth_ok', () => resolve());
        }),
        new Promise<void>((resolve) => {
          socketGuest.on('connect', () => {
            socketGuest.emit('auth', { type: 'auth', payload: { token: guestToken, deviceId: 'dev_guest_16' }, timestamp: Date.now() });
          });
          socketGuest.on('auth_ok', () => resolve());
        }),
      ]);

      const room = `listen:${sessionId}`;
      await Promise.all([
        new Promise<void>((resolve) => socketHost.once('room_joined', () => resolve())),
        new Promise<void>((resolve) => socketGuest.once('room_joined', () => resolve())),
        (async () => {
          socketHost.emit('join_room', { room });
          socketGuest.emit('join_room', { room });
        })(),
      ]);

      let receivedCount = 0;
      socketGuest.on('listen_chat_message', () => {
        receivedCount++;
      });

      // Send 7 messages rapidly within 50ms
      for (let i = 0; i < 7; i++) {
        socketHost.emit('listen_chat_message', {
          sessionId,
          text: `Message ${i}`,
        });
      }

      await new Promise((r) => setTimeout(r, 500));
      assert.strictEqual(receivedCount, 5, `Expected 5 messages relayed within 1s window, got ${receivedCount}`);
    } finally {
      socketHost.close();
      socketGuest.close();
      await sqlAuthRole`DELETE FROM listen_participants WHERE session_id = ${sessionId}`.catch(() => {});
      await sqlAuthRole`DELETE FROM listen_sessions WHERE id = ${sessionId}`.catch(() => {});
    }
  });

  it('17. listen_reaction rejects emoji outside whitelist', async () => {
    const sessionId = crypto.randomUUID();
    const joinCode = 'TS17JC';
    await sqlAuthRole`
      INSERT INTO listen_sessions (id, join_code, host_user_id, control_mode, current_provider, current_provider_track_id, position_ms, playing)
      VALUES (${sessionId}, ${joinCode}, ${hostUser.id}, 'open', 'jiosaavn', 'track_curr', 0, true)
    `;
    await sqlAuthRole`
      INSERT INTO listen_participants (session_id, user_id, joined_at)
      VALUES (${sessionId}, ${guestUser.id}, NOW())
    `;

    const socketHost: ClientSocketType = ClientSocket(serverUrl, { transports: ['websocket'], reconnection: false });
    const socketGuest: ClientSocketType = ClientSocket(serverUrl, { transports: ['websocket'], reconnection: false });

    try {
      await Promise.all([
        new Promise<void>((resolve) => {
          socketHost.on('connect', () => {
            socketHost.emit('auth', { type: 'auth', payload: { token: hostToken, deviceId: 'dev_host_17' }, timestamp: Date.now() });
          });
          socketHost.on('auth_ok', () => resolve());
        }),
        new Promise<void>((resolve) => {
          socketGuest.on('connect', () => {
            socketGuest.emit('auth', { type: 'auth', payload: { token: guestToken, deviceId: 'dev_guest_17' }, timestamp: Date.now() });
          });
          socketGuest.on('auth_ok', () => resolve());
        }),
      ]);

      const room = `listen:${sessionId}`;
      await Promise.all([
        new Promise<void>((resolve) => socketHost.once('room_joined', () => resolve())),
        new Promise<void>((resolve) => socketGuest.once('room_joined', () => resolve())),
        (async () => {
          socketHost.emit('join_room', { room });
          socketGuest.emit('join_room', { room });
        })(),
      ]);

      let receivedReaction: any = null;
      socketGuest.on('listen_reaction', (msg: any) => {
        receivedReaction = msg;
      });

      // 1. Emoji outside whitelist -> rejected
      socketHost.emit('listen_reaction', { sessionId, emoji: '💩' });
      await new Promise((r) => setTimeout(r, 200));
      assert.strictEqual(receivedReaction, null, 'Non-whitelisted emoji must be rejected');

      // 2. Whitelisted emoji -> accepted
      const reactionPromise = new Promise<any>((resolve) => {
        socketGuest.once('listen_reaction', resolve);
      });
      socketHost.emit('listen_reaction', { sessionId, emoji: '🔥' });
      receivedReaction = await reactionPromise;
      assert.ok(receivedReaction, 'Whitelisted emoji must be relayed');
      assert.strictEqual(receivedReaction.payload.emoji, '🔥');
    } finally {
      socketHost.close();
      socketGuest.close();
      await sqlAuthRole`DELETE FROM listen_participants WHERE session_id = ${sessionId}`.catch(() => {});
      await sqlAuthRole`DELETE FROM listen_sessions WHERE id = ${sessionId}`.catch(() => {});
    }
  });

  it('18. Host disconnect starts 60s grace period and notifies participants', async () => {
    const sessionId = crypto.randomUUID();
    const joinCode = 'TS18JC';
    const olderJoinedAt = new Date(Date.now() - 5000);
    const newerJoinedAt = new Date(Date.now() - 2000);

    const ts = Date.now();
    const participant2 = await createTestUser(`p2_${ts}`, `p2_${ts}@test.com`);
    const p2Token = generateAccessToken(participant2.id, participant2.username);

    await sqlAuthRole`
      INSERT INTO listen_sessions (id, join_code, host_user_id, control_mode, current_provider, current_provider_track_id, position_ms, playing)
      VALUES (${sessionId}, ${joinCode}, ${hostUser.id}, 'open', 'jiosaavn', 'track_curr', 0, true)
    `;
    await sqlAuthRole`
      INSERT INTO listen_participants (session_id, user_id, joined_at)
      VALUES (${sessionId}, ${guestUser.id}, ${olderJoinedAt}),
             (${sessionId}, ${participant2.id}, ${newerJoinedAt})
    `;

    const socketHost: ClientSocketType = ClientSocket(serverUrl, { transports: ['websocket'], reconnection: false });
    const socketGuest: ClientSocketType = ClientSocket(serverUrl, { transports: ['websocket'], reconnection: false });

    try {
      await Promise.all([
        new Promise<void>((resolve) => {
          socketHost.on('connect', () => {
            socketHost.emit('auth', { type: 'auth', payload: { token: hostToken, deviceId: 'dev_host_18' }, timestamp: Date.now() });
          });
          socketHost.on('auth_ok', () => resolve());
        }),
        new Promise<void>((resolve) => {
          socketGuest.on('connect', () => {
            socketGuest.emit('auth', { type: 'auth', payload: { token: guestToken, deviceId: 'dev_guest_18' }, timestamp: Date.now() });
          });
          socketGuest.on('auth_ok', () => resolve());
        }),
      ]);

      const room = `listen:${sessionId}`;
      await Promise.all([
        new Promise<void>((resolve) => {
          socketHost.once('room_joined', () => resolve());
          socketHost.emit('join_room', { room });
        }),
        new Promise<void>((resolve) => {
          socketGuest.once('room_joined', () => resolve());
          socketGuest.emit('join_room', { room });
        }),
      ]);

      const dcPromise = new Promise<any>((resolve, reject) => {
        socketGuest.on('listen_host_disconnected', (msg: any) => resolve(msg));
        setTimeout(() => reject(new Error('Timeout waiting for listen_host_disconnected')), 5000);
      });

      // Host disconnects
      socketHost.disconnect();

      const dcMsg = await dcPromise;
      assert.strictEqual(dcMsg.payload.sessionId, sessionId);
      assert.strictEqual(dcMsg.payload.graceSeconds, 60);
    } finally {
      socketHost.close();
      socketGuest.close();
      clearAllHostGraceTimers();
      await sqlAuthRole`DELETE FROM listen_participants WHERE session_id = ${sessionId}`.catch(() => {});
      await sqlAuthRole`DELETE FROM listen_sessions WHERE id = ${sessionId}`.catch(() => {});
      await cleanupTestUsers([participant2.id]).catch(() => {});
    }
  });

  it('19. Host disconnect with no remaining participants broadcasts grace period and starts timer', async () => {
    const sessionId = crypto.randomUUID();
    const joinCode = 'TS19JC';
    await sqlAuthRole`
      INSERT INTO listen_sessions (id, join_code, host_user_id, control_mode, current_provider, current_provider_track_id, position_ms, playing)
      VALUES (${sessionId}, ${joinCode}, ${hostUser.id}, 'open', 'jiosaavn', 'track_curr', 0, true)
    `;

    const socketHost: ClientSocketType = ClientSocket(serverUrl, { transports: ['websocket'], reconnection: false });

    try {
      await new Promise<void>((resolve) => {
        socketHost.on('connect', () => {
          socketHost.emit('auth', { type: 'auth', payload: { token: hostToken, deviceId: 'dev_host_19' }, timestamp: Date.now() });
        });
        socketHost.on('auth_ok', () => resolve());
      });

      const room = `listen:${sessionId}`;
      await new Promise<void>((resolve) => {
        socketHost.once('room_joined', () => resolve());
        socketHost.emit('join_room', { room });
      });

      // Disconnect host
      socketHost.disconnect();

      await new Promise((r) => setTimeout(r, 300));
      // Session should still exist (under grace period)
      const [session] = await sqlAuthRole`SELECT ended_at AS "endedAt" FROM listen_sessions WHERE id = ${sessionId}`;
      assert.ok(session, 'Session must exist during grace period');
      assert.strictEqual(session.endedAt, null, 'Session must not be terminated immediately');
    } finally {
      socketHost.close();
      clearAllHostGraceTimers();
      await sqlAuthRole`DELETE FROM listen_sessions WHERE id = ${sessionId}`.catch(() => {});
    }
  });

  it('20. POST /api/listen/sessions/:id/queue adds track and DELETE removes track (Host Veto & User remove own)', async () => {
    // 1. Host creates a fresh session
    const createRes = await app.fetch(
      new Request('http://localhost:4000/api/listen/sessions', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${hostToken}`,
        },
        body: JSON.stringify({
          trackId: 'track_initial',
          provider: 'jiosaavn',
          startPositionMs: 0,
        }),
      })
    );
    assert.strictEqual(createRes.status, 201);
    const { sessionId } = (await createRes.json()) as any;

    try {
      // 2. Guest joins session
      const joinRes = await app.fetch(
        new Request(`http://localhost:4000/api/listen/sessions/${sessionId}/join`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${guestToken}` },
        })
      );
      assert.strictEqual(joinRes.status, 200);

      // 3. Guest queues a song
      const addQueueRes = await app.fetch(
        new Request(`http://localhost:4000/api/listen/sessions/${sessionId}/queue`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${guestToken}`,
          },
          body: JSON.stringify({
            trackId: 'track_guest_1',
            provider: 'jiosaavn',
            title: 'Guest Song 1',
            artist: 'Guest Artist',
            artworkUrl: 'https://example.com/art.jpg',
          }),
        })
      );
      assert.strictEqual(addQueueRes.status, 201);
      const queueItem = (await addQueueRes.json()) as any;
      assert.ok(queueItem.id, 'Queue item must have an id');
      assert.strictEqual(queueItem.trackId, 'track_guest_1');
      assert.strictEqual(queueItem.addedByUserId, guestUser.id);
      assert.strictEqual(queueItem.position, 1);

      // 4. Host also queues a song
      const hostAddRes = await app.fetch(
        new Request(`http://localhost:4000/api/listen/sessions/${sessionId}/queue`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${hostToken}`,
          },
          body: JSON.stringify({
            trackId: 'track_host_2',
            provider: 'jiosaavn',
            title: 'Host Song 2',
            artist: 'Host Artist',
          }),
        })
      );
      assert.strictEqual(hostAddRes.status, 201);
      const hostQueueItem = (await hostAddRes.json()) as any;
      assert.strictEqual(hostQueueItem.position, 2);

      // 5. Fetch queue via GET /queue
      const fetchQueueRes = await app.fetch(
        new Request(`http://localhost:4000/api/listen/sessions/${sessionId}/queue`, {
          headers: { Authorization: `Bearer ${guestToken}` },
        })
      );
      assert.strictEqual(fetchQueueRes.status, 200);
      const { queue } = (await fetchQueueRes.json()) as any;
      assert.strictEqual(queue.length, 2);
      assert.strictEqual(queue[0].trackId, 'track_guest_1');
      assert.strictEqual(queue[1].trackId, 'track_host_2');

      // 6. Non-participant attempts to queue -> 403 Forbidden
      const outsider = await createTestUser(`listen_outsider_${Date.now()}`, `outsider_${Date.now()}@test.com`);
      const outsiderToken = generateAccessToken(outsider.id, outsider.username);
      try {
        const outsiderAddRes = await app.fetch(
          new Request(`http://localhost:4000/api/listen/sessions/${sessionId}/queue`, {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              Authorization: `Bearer ${outsiderToken}`,
            },
            body: JSON.stringify({
              trackId: 'track_hacker',
              title: 'Hacker Song',
            }),
          })
        );
        assert.strictEqual(outsiderAddRes.status, 403);

        // 7. Outsider attempts to delete guest's queued track -> 403 Forbidden
        const outsiderDelRes = await app.fetch(
          new Request(`http://localhost:4000/api/listen/sessions/${sessionId}/queue/${queueItem.id}`, {
            method: 'DELETE',
            headers: { Authorization: `Bearer ${outsiderToken}` },
          })
        );
        assert.strictEqual(outsiderDelRes.status, 403);
      } finally {
        await cleanupTestUsers([outsider.id]);
      }

      // 8. Host Veto: Host deletes guest's queued track -> 200 OK
      const hostVetoRes = await app.fetch(
        new Request(`http://localhost:4000/api/listen/sessions/${sessionId}/queue/${queueItem.id}`, {
          method: 'DELETE',
          headers: { Authorization: `Bearer ${hostToken}` },
        })
      );
      assert.strictEqual(hostVetoRes.status, 200);

      // 9. Host removes their own queued track -> 200 OK
      const hostDeleteOwnRes = await app.fetch(
        new Request(`http://localhost:4000/api/listen/sessions/${sessionId}/queue/${hostQueueItem.id}`, {
          method: 'DELETE',
          headers: { Authorization: `Bearer ${hostToken}` },
        })
      );
      assert.strictEqual(hostDeleteOwnRes.status, 200);

      // 10. Deleting already-deleted track returns 404
      const notFoundRes = await app.fetch(
        new Request(`http://localhost:4000/api/listen/sessions/${sessionId}/queue/${queueItem.id}`, {
          method: 'DELETE',
          headers: { Authorization: `Bearer ${hostToken}` },
        })
      );
      assert.strictEqual(notFoundRes.status, 404);
    } finally {
      await sqlAuthRole`DELETE FROM listen_sessions WHERE id = ${sessionId}`.catch(() => {});
    }
  });

  it('POST /api/listen/sessions returns 401 without auth token', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/listen/sessions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ isPublic: true }),
      })
    );
    assert.strictEqual(res.status, 401);
  });

  it('POST /api/listen/sessions/:id/join with invalid session ID returns 400', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/listen/sessions/invalid-session-id/join', {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${hostToken}`,
        },
      })
    );
    assert.strictEqual(res.status, 400);
  });

  // ==========================================
  // Phase A.4.1 — Server-Side Listen Together Integration Tests
  // ==========================================

  it('Phase A.4.1 - 1. POST /sessions creates valid join_code matching 32-char alphabet', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/listen/sessions', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${hostToken}`,
        },
        body: JSON.stringify({
          trackId: 'track_a41_test',
          provider: 'jiosaavn',
          startPositionMs: 1200,
        }),
      })
    );

    assert.strictEqual(res.status, 201);
    const data = (await res.json()) as any;
    assert.ok(data.sessionId, 'Must return sessionId UUID');
    assert.ok(data.joinCode, 'Must return joinCode');
    assert.match(data.joinCode, /^[2-9A-HJ-NP-Z]{6}$/, 'joinCode must match 6-char non-ambiguous alphabet');

    // Verify GET /api/listen/join/:joinCode resolves session
    const joinRes = await app.fetch(
      new Request(`http://localhost:4000/api/listen/join/${data.joinCode}`, {
        headers: { Authorization: `Bearer ${guestToken}` },
      })
    );
    assert.strictEqual(joinRes.status, 200);
    const joinData = (await joinRes.json()) as any;
    assert.strictEqual(joinData.sessionId, data.sessionId);
    assert.strictEqual(joinData.joinCode, data.joinCode);
    assert.strictEqual(joinData.participants.length, 1);
  });

  it('Phase A.4.1 - 2. Guest joins by join_code over socket and receives participant_joined', async () => {
    const createRes = await app.fetch(
      new Request('http://localhost:4000/api/listen/sessions', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${hostToken}`,
        },
        body: JSON.stringify({ trackId: 'track_a41_socket' }),
      })
    );
    const session = (await createRes.json()) as any;
    const joinCode = session.joinCode;

    const socketHost: ClientSocketType = ClientSocket(serverUrl, { transports: ['websocket'], reconnection: false });
    const socketGuest: ClientSocketType = ClientSocket(serverUrl, { transports: ['websocket'], reconnection: false });

    try {
      await Promise.all([
        new Promise<void>((resolve) => {
          socketHost.on('connect', () => {
            socketHost.emit('auth', { type: 'auth', payload: { token: hostToken, deviceId: 'dev_host_a41_2' }, timestamp: Date.now() });
          });
          socketHost.on('auth_ok', () => resolve());
        }),
        new Promise<void>((resolve) => {
          socketGuest.on('connect', () => {
            socketGuest.emit('auth', { type: 'auth', payload: { token: guestToken, deviceId: 'dev_guest_a41_2' }, timestamp: Date.now() });
          });
          socketGuest.on('auth_ok', () => resolve());
        }),
      ]);

      const room = `listen:${joinCode}`;

      // Host joins
      await new Promise<void>((resolve) => {
        socketHost.once('room_joined', () => resolve());
        socketHost.emit('join_room', { room });
      });

      // Guest joins and verifies participant_joined is received on both sides
      const hostReceivedJoinedPromise = new Promise<any>((resolve, reject) => {
        socketHost.once('listen_together_participant_joined', resolve);
        setTimeout(() => reject(new Error('Timeout waiting for host listen_together_participant_joined')), 5000);
      });
      const guestReceivedJoinedPromise = new Promise<any>((resolve, reject) => {
        socketGuest.once('listen_together_participant_joined', resolve);
        setTimeout(() => reject(new Error('Timeout waiting for guest listen_together_participant_joined')), 5000);
      });

      await new Promise<void>((resolve, reject) => {
        socketGuest.once('room_joined', () => resolve());
        setTimeout(() => reject(new Error('Timeout waiting for guest room_joined')), 5000);
        socketGuest.emit('join_room', { room });
      });

      const hostEvent = await hostReceivedJoinedPromise;
      const guestEvent = await guestReceivedJoinedPromise;

      assert.strictEqual(hostEvent.payload.joinCode, joinCode);
      assert.strictEqual(hostEvent.payload.userId, guestUser.id);
      assert.strictEqual(guestEvent.payload.joinCode, joinCode);
      assert.strictEqual(guestEvent.payload.userId, guestUser.id);
    } finally {
      socketHost.close();
      socketGuest.close();
    }
  });

  it('Phase A.4.1 - 3. Sync relay from host to guest via join_code room', async () => {
    const createRes = await app.fetch(
      new Request('http://localhost:4000/api/listen/sessions', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${hostToken}`,
        },
        body: JSON.stringify({ trackId: 'track_sync_relay' }),
      })
    );
    const session = (await createRes.json()) as any;
    const joinCode = session.joinCode;

    const socketHost: ClientSocketType = ClientSocket(serverUrl, { transports: ['websocket'], reconnection: false });
    const socketGuest: ClientSocketType = ClientSocket(serverUrl, { transports: ['websocket'], reconnection: false });

    try {
      await Promise.all([
        new Promise<void>((resolve) => {
          socketHost.on('connect', () => {
            socketHost.emit('auth', { type: 'auth', payload: { token: hostToken, deviceId: 'dev_host_a41_3' }, timestamp: Date.now() });
          });
          socketHost.on('auth_ok', () => resolve());
        }),
        new Promise<void>((resolve) => {
          socketGuest.on('connect', () => {
            socketGuest.emit('auth', { type: 'auth', payload: { token: guestToken, deviceId: 'dev_guest_a41_3' }, timestamp: Date.now() });
          });
          socketGuest.on('auth_ok', () => resolve());
        }),
      ]);

      const room = `listen:${joinCode}`;
      await Promise.all([
        new Promise<void>((resolve) => {
          socketHost.once('room_joined', () => resolve());
          socketHost.emit('join_room', { room });
        }),
        new Promise<void>((resolve) => {
          socketGuest.once('room_joined', () => resolve());
          socketGuest.emit('join_room', { room });
        }),
      ]);

      const guestSyncPromise = new Promise<any>((resolve, reject) => {
        socketGuest.once('listen_together_sync', resolve);
        setTimeout(() => reject(new Error('Timeout waiting for listen_together_sync')), 5000);
      });

      // Allow room subscription and rate-limit window to settle before sync broadcast
      await new Promise((r) => setTimeout(r, 200));

      socketHost.emit('listen_together_sync', {
        sessionId: joinCode,
        trackId: 'track_synced_perfectly',
        providerId: 'jiosaavn',
        positionMs: 45000,
        playing: true,
      });

      const syncReceived = await guestSyncPromise;
      assert.strictEqual(syncReceived.payload.trackId, 'track_synced_perfectly');
      assert.strictEqual(syncReceived.payload.positionMs, 45000);
      assert.strictEqual(syncReceived.payload.playing, true);
      assert.strictEqual(syncReceived.payload.joinCode, joinCode);
    } finally {
      socketHost.close();
      socketGuest.close();
    }
  });

  it('Phase A.4.1 - 4. Non-host socket attempting to end session is rejected with FORBIDDEN', async () => {
    const createRes = await app.fetch(
      new Request('http://localhost:4000/api/listen/sessions', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${hostToken}`,
        },
        body: JSON.stringify({ trackId: 'track_ownership_test' }),
      })
    );
    const session = (await createRes.json()) as any;
    const joinCode = session.joinCode;

    const socketGuest: ClientSocketType = ClientSocket(serverUrl, { transports: ['websocket'], reconnection: false });

    try {
      await new Promise<void>((resolve) => {
        socketGuest.on('connect', () => {
          socketGuest.emit('auth', { type: 'auth', payload: { token: guestToken, deviceId: 'dev_guest_a41_4' }, timestamp: Date.now() });
        });
        socketGuest.on('auth_ok', () => resolve());
      });

      const room = `listen:${joinCode}`;
      await new Promise<void>((resolve) => {
        socketGuest.once('room_joined', () => resolve());
        socketGuest.emit('join_room', { room });
      });

      const errorPromise = new Promise<any>((resolve, reject) => {
        socketGuest.once('error', resolve);
        setTimeout(() => reject(new Error('Timeout waiting for FORBIDDEN error')), 5000);
      });

      // Guest attempts to end session
      socketGuest.emit('listen_together_end', {
        sessionId: joinCode,
      });

      const err = await errorPromise;
      assert.strictEqual(err.payload.code, 'FORBIDDEN');

      // Verify session is still active via REST
      const snapRes = await app.fetch(
        new Request(`http://localhost:4000/api/listen/join/${joinCode}`, {
          headers: { Authorization: `Bearer ${hostToken}` },
        })
      );
      assert.strictEqual(snapRes.status, 200);
      const snap = (await snapRes.json()) as any;
      assert.strictEqual(snap.endedAt, null, 'Session must remain active');
    } finally {
      socketGuest.close();
    }
  });

  it('Phase A.4.1 - 5. Host disconnect emits listen_host_disconnected; reconnect restores room', async () => {
    const createRes = await app.fetch(
      new Request('http://localhost:4000/api/listen/sessions', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${hostToken}`,
        },
        body: JSON.stringify({ trackId: 'track_grace_test' }),
      })
    );
    const session = (await createRes.json()) as any;
    const joinCode = session.joinCode;

    let socketHost: ClientSocketType = ClientSocket(serverUrl, { transports: ['websocket'], reconnection: false });
    const socketGuest: ClientSocketType = ClientSocket(serverUrl, { transports: ['websocket'], reconnection: false });

    try {
      await Promise.all([
        new Promise<void>((resolve) => {
          socketHost.on('connect', () => {
            socketHost.emit('auth', { type: 'auth', payload: { token: hostToken, deviceId: 'dev_host_a41_5' }, timestamp: Date.now() });
          });
          socketHost.on('auth_ok', () => resolve());
        }),
        new Promise<void>((resolve) => {
          socketGuest.on('connect', () => {
            socketGuest.emit('auth', { type: 'auth', payload: { token: guestToken, deviceId: 'dev_guest_a41_5' }, timestamp: Date.now() });
          });
          socketGuest.on('auth_ok', () => resolve());
        }),
      ]);

      const room = `listen:${joinCode}`;
      await Promise.all([
        new Promise<void>((resolve) => {
          socketHost.once('room_joined', () => resolve());
          socketHost.emit('join_room', { room });
        }),
        new Promise<void>((resolve) => {
          socketGuest.once('room_joined', () => resolve());
          socketGuest.emit('join_room', { room });
        }),
      ]);

      // 1. Host disconnects -> Guest receives listen_host_disconnected with graceSeconds: 60
      const disconnectPromise = new Promise<any>((resolve, reject) => {
        socketGuest.once('listen_host_disconnected', resolve);
        setTimeout(() => reject(new Error('Timeout waiting for listen_host_disconnected')), 5000);
      });

      socketHost.disconnect();
      const dcEvent = await disconnectPromise;
      assert.strictEqual(dcEvent.payload.joinCode, joinCode);
      assert.strictEqual(dcEvent.payload.graceSeconds, 60);

      // 2. Host reconnects within window -> Guest receives listen_host_reconnected
      const reconnectedPromise = new Promise<any>((resolve, reject) => {
        socketGuest.once('listen_host_reconnected', resolve);
        setTimeout(() => reject(new Error('Timeout waiting for listen_host_reconnected')), 5000);
      });

      socketHost = ClientSocket(serverUrl, { transports: ['websocket'], reconnection: false });
      await new Promise<void>((resolve) => {
        socketHost.on('connect', () => {
          socketHost.emit('auth', { type: 'auth', payload: { token: hostToken, deviceId: 'dev_host_a41_5' }, timestamp: Date.now() });
        });
        socketHost.on('auth_ok', () => resolve());
      });

      socketHost.emit('join_room', { room });

      const reconnEvent = await reconnectedPromise;
      assert.strictEqual(reconnEvent.payload.joinCode, joinCode);

      // Verify room still alive
      const snapRes = await app.fetch(
        new Request(`http://localhost:4000/api/listen/join/${joinCode}`, {
          headers: { Authorization: `Bearer ${hostToken}` },
        })
      );
      assert.strictEqual(snapRes.status, 200);
    } finally {
      socketHost.close();
      socketGuest.close();
    }
  });

  it('Phase A.4.1 - 6. Nonexistent join code returns clean 404 on REST and error on socket', async () => {
    // 1. REST check
    const restRes = await app.fetch(
      new Request('http://localhost:4000/api/listen/join/ZZZZZZ', {
        headers: { Authorization: `Bearer ${guestToken}` },
      })
    );
    assert.strictEqual(restRes.status, 404);
    const restBody = (await restRes.json()) as any;
    assert.match(restBody.error, /not found/i);

    // 2. Socket check
    const socketGuest: ClientSocketType = ClientSocket(serverUrl, { transports: ['websocket'], reconnection: false });
    try {
      await new Promise<void>((resolve) => {
        socketGuest.on('connect', () => {
          socketGuest.emit('auth', { type: 'auth', payload: { token: guestToken, deviceId: 'dev_guest_a41_6' }, timestamp: Date.now() });
        });
        socketGuest.on('auth_ok', () => resolve());
      });

      const errorPromise = new Promise<any>((resolve, reject) => {
        socketGuest.once('error', resolve);
        setTimeout(() => reject(new Error('Timeout waiting for FORBIDDEN error')), 5000);
      });

      socketGuest.emit('join_room', { room: 'listen:ZZZZZZ' });
      const err = await errorPromise;
      assert.match(err.payload.message, /Unauthorized or invalid room: listen:ZZZZZZ/i);
    } finally {
      socketGuest.close();
    }
  });

  // ==========================================
  // Phase A.4.1b — Additional Parity & Grace Tests
  // ==========================================

  it('Phase A.4.1b - Test A. Capacity parity on join code path (21st join rejected, self-rejoin allowed)', async () => {
    // 1. Host creates room
    const createRes = await app.fetch(
      new Request('http://localhost:4000/api/listen/sessions', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${hostToken}`,
        },
        body: JSON.stringify({ trackId: 'track_capacity_code_test' }),
      })
    );
    assert.strictEqual(createRes.status, 201);
    const session = (await createRes.json()) as any;
    const { sessionId, joinCode } = session;

    // 2. Add 19 guests via REST code join route (total 20 participants including host)
    // Seeding via REST join route is chosen over WebSocket to avoid 19 simultaneous socket handshakes and timeouts.
    const guestUsers: any[] = [];
    const guestTokens: string[] = [];
    const ts = Date.now();

    for (let i = 0; i < 19; i++) {
      const u = await createTestUser(`cap_code_${i}_${ts}`, `cap_code_${i}_${ts}@test.com`);
      guestUsers.push(u);
      const token = generateAccessToken(u.id, u.username);
      guestTokens.push(token);

      const joinRes = await app.fetch(
        new Request(`http://localhost:4000/api/listen/sessions/${joinCode}/join`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${token}` },
        })
      );
      assert.strictEqual(joinRes.status, 200, `Guest ${i} must join successfully via joinCode`);
    }

    try {
      // 3. 21st participant attempts to join via join code path -> must be rejected with 400
      const u21 = await createTestUser(`cap_code_21_${ts}`, `cap_code_21_${ts}@test.com`);
      const u21Token = generateAccessToken(u21.id, u21.username);
      guestUsers.push(u21);

      const rejectedJoinRes = await app.fetch(
        new Request(`http://localhost:4000/api/listen/sessions/${joinCode}/join`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${u21Token}` },
        })
      );
      assert.strictEqual(rejectedJoinRes.status, 400);
      const rejectedBody = (await rejectedJoinRes.json()) as any;
      assert.strictEqual(rejectedBody.error, 'Session is full (max 20 participants)');

      // Also verify via POST /api/listen/join/:joinCode route
      const aliasRejectedRes = await app.fetch(
        new Request(`http://localhost:4000/api/listen/join/${joinCode}`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${u21Token}` },
        })
      );
      assert.strictEqual(aliasRejectedRes.status, 400);

      // 4. Assert a participant re-joining their own room is NOT rejected (idempotency)
      const rejoinRes = await app.fetch(
        new Request(`http://localhost:4000/api/listen/sessions/${joinCode}/join`, {
          method: 'POST',
          headers: { Authorization: `Bearer ${guestTokens[0]}` },
        })
      );
      assert.strictEqual(rejoinRes.status, 200, 'Existing participant re-joining must not be rejected by capacity');
      const rejoinBody = (await rejoinRes.json()) as any;
      assert.strictEqual(rejoinBody.status, 'joined');
    } finally {
      await cleanupTestUsers(guestUsers.map((u) => u.id)).catch(() => {});
      await sqlAuthRole`DELETE FROM listen_participants WHERE session_id = ${sessionId}`.catch(() => {});
      await sqlAuthRole`DELETE FROM listen_sessions WHERE id = ${sessionId}`.catch(() => {});
    }
  });

  it('Phase A.4.1b - Test B. Grace expiry actually ends room after configured LISTEN_HOST_GRACE_MS', async () => {
    // 1. Configure shortened grace window for this test
    process.env.LISTEN_HOST_GRACE_MS = '150';

    const createRes = await app.fetch(
      new Request('http://localhost:4000/api/listen/sessions', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${hostToken}`,
        },
        body: JSON.stringify({ trackId: 'track_grace_expiry_test' }),
      })
    );
    assert.strictEqual(createRes.status, 201);
    const session = (await createRes.json()) as any;
    const { sessionId, joinCode } = session;

    let socketHost: ClientSocketType = ClientSocket(serverUrl, { transports: ['websocket'], reconnection: false });
    const socketGuest: ClientSocketType = ClientSocket(serverUrl, { transports: ['websocket'], reconnection: false });

    try {
      await Promise.all([
        new Promise<void>((resolve) => {
          socketHost.on('connect', () => {
            socketHost.emit('auth', { type: 'auth', payload: { token: hostToken, deviceId: 'dev_host_grace_b' }, timestamp: Date.now() });
          });
          socketHost.on('auth_ok', () => resolve());
        }),
        new Promise<void>((resolve) => {
          socketGuest.on('connect', () => {
            socketGuest.emit('auth', { type: 'auth', payload: { token: guestToken, deviceId: 'dev_guest_grace_b' }, timestamp: Date.now() });
          });
          socketGuest.on('auth_ok', () => resolve());
        }),
      ]);

      const room = `listen:${joinCode}`;
      await Promise.all([
        new Promise<void>((resolve) => {
          socketHost.once('room_joined', () => resolve());
          socketHost.emit('join_room', { room });
        }),
        new Promise<void>((resolve) => {
          socketGuest.once('room_joined', () => resolve());
          socketGuest.emit('join_room', { room });
        }),
      ]);

      // Set up promises for host disconnect and session end
      const disconnectPromise = new Promise<any>((resolve, reject) => {
        socketGuest.once('listen_host_disconnected', resolve);
        setTimeout(() => reject(new Error('Timeout waiting for listen_host_disconnected')), 5000);
      });

      const endedPromise = new Promise<any>((resolve, reject) => {
        socketGuest.once('listen_together_ended', resolve);
        setTimeout(() => reject(new Error('Timeout waiting for listen_together_ended')), 5000);
      });

      // Host disconnects
      socketHost.disconnect();

      // 1. Assert listen_host_disconnected received with graceSeconds consistent with configured 150ms
      const dcEvent = await disconnectPromise;
      assert.strictEqual(dcEvent.payload.sessionId, sessionId);
      assert.strictEqual(dcEvent.payload.joinCode, joinCode);
      assert.strictEqual(dcEvent.payload.graceSeconds, 0.15);

      // 2. Assert after grace elapses, listen_together_ended is broadcast to remaining participants
      const endedEvent = await endedPromise;
      assert.strictEqual(endedEvent.payload.sessionId, sessionId);
      assert.strictEqual(endedEvent.payload.joinCode, joinCode);
      assert.strictEqual(endedEvent.payload.reason, 'host_disconnected_timeout');

      // 3. Assert the listen_sessions row has ended_at set in DB (proves timer executed cleanup)
      const [dbSession] = await sqlAuthRole`
        SELECT ended_at AS "endedAt" FROM listen_sessions WHERE id = ${sessionId}
      `;
      assert.ok(dbSession?.endedAt, 'listen_sessions.ended_at must be populated in database');
    } finally {
      // Restore isolation: delete env override and clear lingering timers
      delete process.env.LISTEN_HOST_GRACE_MS;
      clearAllHostGraceTimers();
      socketHost.close();
      socketGuest.close();
      await sqlAuthRole`DELETE FROM listen_participants WHERE session_id = ${sessionId}`.catch(() => {});
      await sqlAuthRole`DELETE FROM listen_sessions WHERE id = ${sessionId}`.catch(() => {});
    }
  });
});
