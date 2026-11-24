/**
 * Phase 11B — Realtime Signaling, Rooms & Presence Server Tests
 * Conforms to docs/00_STACK_LOCK.md Section 6, docs/05_API.md, and Phase 11B specification.
 */

import { describe, it, before, after } from 'node:test';
import assert from 'node:assert';
import http from 'node:http';
import crypto from 'node:crypto';
import { io as ClientSocket, Socket as ClientSocketType } from 'socket.io-client';
import { app } from '../src/app.js';
import { initializeRealtimeServer } from '../src/realtime/socketServer.js';
import { presence } from '../src/realtime/presence.js';
import { typing } from '../src/realtime/typing.js';
import { sqlAuthRole, createTestUser, cleanupTestUsers } from './helpers/rls.js';
import { generateAccessToken } from '../src/services/authService.js';

function makeJoinCode(): string {
  const A = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
  const bytes = crypto.randomBytes(6);
  return Array.from(bytes, (b) => A[b % A.length]).join('');
}

describe('Phase 11B — Realtime Signaling, Rooms & Presence Tests', () => {
  let server: http.Server;
  let ioServer: any;
  let serverPort = 0;
  let serverUrl = '';

  const timestamp = Math.floor(Math.random() * 9000 + 1000);
  let userA: { id: string; username: string; token: string };
  let userB: { id: string; username: string; token: string };

  before(async () => {
    // 1. Create HTTP server with Socket.io mounted
    server = http.createServer();
    ioServer = initializeRealtimeServer(server);

    await new Promise<void>((resolve) => {
      server.listen(0, () => {
        const addr = server.address() as any;
        serverPort = addr.port;
        serverUrl = `http://localhost:${serverPort}`;
        resolve();
      });
    });

    // 2. Setup User A via API
    const resA = await app.fetch(
      new Request('http://localhost:4000/api/auth/signup', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: `rt_u_a_${timestamp}`,
          password: 'Password123!',
          displayName: 'Realtime User A',
        }),
      })
    );
    assert.strictEqual(resA.status, 201);
    const dataA = (await resA.json()) as any;
    userA = {
      id: dataA.user.id,
      username: dataA.user.username,
      token: dataA.accessToken,
    };

    // 3. Setup User B via API
    const resB = await app.fetch(
      new Request('http://localhost:4000/api/auth/signup', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: `rt_u_b_${timestamp}`,
          password: 'Password123!',
          displayName: 'Realtime User B',
        }),
      })
    );
    assert.strictEqual(resB.status, 201);
    const dataB = (await resB.json()) as any;
    userB = {
      id: dataB.user.id,
      username: dataB.user.username,
      token: dataB.accessToken,
    };

    // User B follows User A so User B receives User A's presence updates
    await sqlAuthRole`
      INSERT INTO follows (follower_id, followee_id)
      VALUES (${userB.id}, ${userA.id})
      ON CONFLICT DO NOTHING
    `;
  });

  after(async () => {
    if (server) {
      await new Promise<void>((resolve) => server.close(() => resolve()));
    }
    try {
      if (userA?.id) await sqlAuthRole`DELETE FROM users WHERE id = ${userA.id}`;
      if (userB?.id) await sqlAuthRole`DELETE FROM users WHERE id = ${userB.id}`;
    } catch {}
    presence._clear();
    typing._clear();
  });

  // 1. Socket connects with no auth -> closed after 5s
  it('1. Socket connects with no auth -> closed after 5s timeout', async () => {
    const socket: ClientSocketType = ClientSocket(serverUrl, {
      transports: ['websocket'],
      reconnection: false,
    });

    const disconnected = await new Promise<boolean>((resolve) => {
      socket.on('disconnect', () => {
        resolve(true);
      });
      // Safety timeout if test hangs
      setTimeout(() => resolve(false), 7000);
    });

    assert.strictEqual(disconnected, true, 'Unauthenticated socket must be disconnected by server');
    socket.close();
  });

  // 2. auth with invalid JWT -> auth_fail + close
  it('2. auth with invalid JWT -> auth_fail + close', async () => {
    const socket: ClientSocketType = ClientSocket(serverUrl, {
      transports: ['websocket'],
      reconnection: false,
    });

    const result = await new Promise<{ gotFail: boolean; disconnected: boolean }>((resolve) => {
      let gotFail = false;
      socket.on('connect', () => {
        socket.emit('auth', {
          type: 'auth',
          payload: { token: 'invalid.jwt.token' },
          timestamp: Date.now(),
        });
      });

      socket.on('auth_fail', () => {
        gotFail = true;
      });

      socket.on('disconnect', () => {
        resolve({ gotFail, disconnected: true });
      });

      setTimeout(() => resolve({ gotFail, disconnected: false }), 4000);
    });

    assert.strictEqual(result.gotFail, true, 'Must receive auth_fail');
    assert.strictEqual(result.disconnected, true, 'Socket must be closed on invalid JWT');
    socket.close();
  });

  // 3. auth with valid JWT -> auth_ok
  it('3. auth with valid JWT -> auth_ok', async () => {
    const socket: ClientSocketType = ClientSocket(serverUrl, {
      transports: ['websocket'],
      reconnection: false,
    });

    const authRes = await new Promise<any>((resolve, reject) => {
      socket.on('connect', () => {
        socket.emit('auth', {
          type: 'auth',
          payload: { token: userA.token, deviceId: 'dev_test_a' },
          timestamp: Date.now(),
        });
      });

      socket.on('auth_ok', (msg) => {
        resolve(msg);
      });

      socket.on('auth_fail', (err) => {
        reject(new Error(err?.payload?.message || 'auth_fail'));
      });

      setTimeout(() => reject(new Error('Timeout waiting for auth_ok')), 4000);
    });

    assert.strictEqual(authRes.type, 'auth_ok');
    assert.strictEqual(authRes.payload.userId, userA.id);
    assert.strictEqual(presence.isOnline(userA.id), true);

    socket.close();
  });

  // 4. join_room with valid user:<self> -> room_joined
  it('4. join_room with valid user:<self> -> room_joined', async () => {
    const socket: ClientSocketType = ClientSocket(serverUrl, {
      transports: ['websocket'],
      reconnection: false,
    });

    const joined = await new Promise<any>((resolve, reject) => {
      socket.on('connect', () => {
        socket.emit('auth', {
          type: 'auth',
          payload: { token: userA.token, deviceId: 'dev_test_a' },
          timestamp: Date.now(),
        });
      });

      socket.on('auth_ok', () => {
        socket.emit('join_room', {
          type: 'join_room',
          payload: { room: `user:${userA.id}` },
          timestamp: Date.now(),
        });
      });

      socket.on('room_joined', (msg) => {
        resolve(msg);
      });

      socket.on('error', (err) => {
        reject(new Error(err?.payload?.message || 'join error'));
      });

      setTimeout(() => reject(new Error('Timeout waiting for room_joined')), 4000);
    });

    assert.strictEqual(joined.type, 'room_joined');
    assert.strictEqual(joined.payload.room, `user:${userA.id}`);

    socket.close();
  });

  // 5. join_room with user:<other> -> error (not authorized)
  it('5. join_room with user:<other> -> error (not authorized)', async () => {
    const socket: ClientSocketType = ClientSocket(serverUrl, {
      transports: ['websocket'],
      reconnection: false,
    });

    const errorMsg = await new Promise<any>((resolve, reject) => {
      socket.on('connect', () => {
        socket.emit('auth', {
          type: 'auth',
          payload: { token: userA.token, deviceId: 'dev_test_a' },
          timestamp: Date.now(),
        });
      });

      socket.on('auth_ok', () => {
        // User A attempts to join user B's personal room
        socket.emit('join_room', {
          type: 'join_room',
          payload: { room: `user:${userB.id}` },
          timestamp: Date.now(),
        });
      });

      socket.on('error', (msg) => {
        resolve(msg);
      });

      socket.on('room_joined', () => {
        reject(new Error('Should not have joined unauthorized room'));
      });

      setTimeout(() => reject(new Error('Timeout waiting for error event')), 4000);
    });

    assert.strictEqual(errorMsg.type, 'error');
    assert.match(errorMsg.payload.message, /Unauthorized/);

    socket.close();
  });

  // 6. join_room with chat:<id> where user is not a participant -> error
  it('6. join_room with chat:<id> where user is not a participant -> error', async () => {
    const socket: ClientSocketType = ClientSocket(serverUrl, {
      transports: ['websocket'],
      reconnection: false,
    });

    const errorMsg = await new Promise<any>((resolve, reject) => {
      socket.on('connect', () => {
        socket.emit('auth', {
          type: 'auth',
          payload: { token: userA.token, deviceId: 'dev_test_a' },
          timestamp: Date.now(),
        });
      });

      socket.on('auth_ok', () => {
        socket.emit('join_room', {
          type: 'join_room',
          payload: { room: 'chat:00000000-0000-0000-0000-000000000999' },
          timestamp: Date.now(),
        });
      });

      socket.on('error', (msg) => {
        resolve(msg);
      });

      setTimeout(() => reject(new Error('Timeout waiting for room error')), 4000);
    });

    assert.strictEqual(errorMsg.type, 'error');
    assert.match(errorMsg.payload.message, /Unauthorized/);

    socket.close();
  });

  // 7. join_room with malformed room string -> error
  it('7. join_room with malformed room string -> error', async () => {
    const socket: ClientSocketType = ClientSocket(serverUrl, {
      transports: ['websocket'],
      reconnection: false,
    });

    const errorMsg = await new Promise<any>((resolve, reject) => {
      socket.on('connect', () => {
        socket.emit('auth', {
          type: 'auth',
          payload: { token: userA.token, deviceId: 'dev_test_a' },
          timestamp: Date.now(),
        });
      });

      socket.on('auth_ok', () => {
        socket.emit('join_room', {
          type: 'join_room',
          payload: { room: 'malformed_room_syntax_no_prefix' },
          timestamp: Date.now(),
        });
      });

      socket.on('error', (msg) => {
        resolve(msg);
      });

      setTimeout(() => reject(new Error('Timeout waiting for malformed room error')), 4000);
    });

    assert.strictEqual(errorMsg.type, 'error');
    assert.match(errorMsg.payload.message, /Unauthorized or invalid room/);

    socket.close();
  });

  // 8. presence_ping -> presence_pong
  it('8. presence_ping -> presence_pong', async () => {
    const socket: ClientSocketType = ClientSocket(serverUrl, {
      transports: ['websocket'],
      reconnection: false,
    });

    const pong = await new Promise<any>((resolve, reject) => {
      socket.on('connect', () => {
        socket.emit('auth', {
          type: 'auth',
          payload: { token: userA.token, deviceId: 'dev_test_a' },
          timestamp: Date.now(),
        });
      });

      socket.on('auth_ok', () => {
        socket.emit('presence_ping', {
          type: 'presence_ping',
          payload: {},
          timestamp: Date.now(),
        });
      });

      socket.on('presence_pong', (msg) => {
        resolve(msg);
      });

      setTimeout(() => reject(new Error('Timeout waiting for presence_pong')), 4000);
    });

    assert.strictEqual(pong.type, 'presence_pong');
    assert.ok(pong.payload.timestamp > 0);

    socket.close();
  });

  // 9. Last socket disconnect -> presence_update with online:false broadcast to followers
  it('9. Last socket disconnect -> presence_update with online:false broadcast to followers', async () => {
    // Connect User B first to listen for User A's presence update
    const socketB: ClientSocketType = ClientSocket(serverUrl, {
      transports: ['websocket'],
      reconnection: false,
    });

    await new Promise<void>((resolve) => {
      socketB.on('connect', () => {
        socketB.emit('auth', {
          type: 'auth',
          payload: { token: userB.token, deviceId: 'dev_test_b' },
          timestamp: Date.now(),
        });
      });
      socketB.on('auth_ok', () => resolve());
    });

    // Now connect User A
    const socketA: ClientSocketType = ClientSocket(serverUrl, {
      transports: ['websocket'],
      reconnection: false,
    });

    await new Promise<void>((resolve) => {
      socketA.on('connect', () => {
        socketA.emit('auth', {
          type: 'auth',
          payload: { token: userA.token, deviceId: 'dev_test_a' },
          timestamp: Date.now(),
        });
      });
      socketA.on('auth_ok', () => resolve());
    });

    // Prepare User B listener for User A going offline
    let receivedEventName = '';
    const offlineUpdatePromise = new Promise<any>((resolve, reject) => {
      socketB.on('presence_update', (msg: any) => {
        receivedEventName = 'presence_update';
        if (msg?.payload?.userId === userA.id && msg?.payload?.online === false) {
          resolve(msg);
        }
      });
      setTimeout(() => reject(new Error('Timeout waiting for offline presence broadcast')), 5000);
    });

    // Disconnect User A's only socket
    socketA.disconnect();

    const offlineMsg = await offlineUpdatePromise;
    assert.strictEqual(receivedEventName, 'presence_update', 'Event name must strictly be presence_update with underscore');
    assert.strictEqual(offlineMsg.type, 'presence_update');
    assert.strictEqual(offlineMsg.payload.userId, userA.id);
    assert.strictEqual(offlineMsg.payload.online, false);

    socketB.close();
  });

  // 10. Rate limit: 11th join_room in 60s -> close
  it('10. Rate limit: 11th join_room in 60s -> close socket', async () => {
    const socket: ClientSocketType = ClientSocket(serverUrl, {
      transports: ['websocket'],
      reconnection: false,
    });

    await new Promise<void>((resolve) => {
      socket.on('connect', () => {
        socket.emit('auth', {
          type: 'auth',
          payload: { token: userA.token, deviceId: 'dev_test_a' },
          timestamp: Date.now(),
        });
      });
      socket.on('auth_ok', () => resolve());
    });

    const closedPromise = new Promise<boolean>((resolve) => {
      socket.on('disconnect', () => resolve(true));
      setTimeout(() => resolve(false), 5000);
    });

    // Send 11 join_room requests rapidly
    for (let i = 0; i < 11; i++) {
      socket.emit('join_room', {
        type: 'join_room',
        payload: { room: `user:${userA.id}` },
        timestamp: Date.now(),
      });
    }

    const wasClosed = await closedPromise;
    assert.strictEqual(wasClosed, true, 'Socket must be closed when exceeding join_room rate limit (10/min)');
    socket.close();
  });

  // 11. Presence map clears after sweep when stale >90s
  it('11. Presence map clears after sweep when stale >90s', () => {
    presence._clear();
    const testUserId = 'stale_user_11';

    presence.addSocket(testUserId, 'socket_1');
    assert.strictEqual(presence.isOnline(testUserId), true);

    // Remove socket so socket count is 0
    presence.removeSocket(testUserId, 'socket_1');
    assert.strictEqual(presence.isOnline(testUserId), false);

    // Sweep with now = current time (lastSeen was just now) -> not stale yet
    const notStale = presence.sweepStale(Date.now());
    assert.strictEqual(notStale.includes(testUserId), false);

    // Sweep with now = 95 seconds later -> stale!
    const staleUserIds = presence.sweepStale(Date.now() + 95_000);
    assert.strictEqual(staleUserIds.includes(testUserId), true);
    assert.strictEqual(presence.lastSeen(testUserId), null);
  });

  // 12. Typing expiry: set typing, wait 4s, activeTypers returns []
  it('12. Typing expiry: set typing, wait 4s, activeTypers returns []', () => {
    typing._clear();
    const convId = 'conv_12';
    const userId = 'user_12';
    const now = 1_000_000;

    typing.setTyping(convId, userId, now);
    assert.deepStrictEqual(typing.activeTypers(convId, now + 1000), [userId]);
    assert.deepStrictEqual(typing.activeTypers(convId, now + 2999), [userId]);

    // 4s later (now + 4000) -> expired
    assert.deepStrictEqual(typing.activeTypers(convId, now + 4000), []);
  });

  // 13. deviceId handling: stored in socket.data.deviceId, missing -> auth_fail
  it('13. deviceId handling: stored in socket.data.deviceId, missing -> auth_fail', async () => {
    // 13a. Missing deviceId -> auth_fail
    const socketNoDev: ClientSocketType = ClientSocket(serverUrl, {
      transports: ['websocket'],
      reconnection: false,
    });

    const noDevResult = await new Promise<{ gotFail: boolean; message?: string }>((resolve) => {
      socketNoDev.on('connect', () => {
        socketNoDev.emit('auth', {
          type: 'auth',
          payload: { token: userA.token }, // missing deviceId
          timestamp: Date.now(),
        });
      });

      socketNoDev.on('auth_fail', (err: any) => {
        resolve({ gotFail: true, message: err?.payload?.message });
      });

      setTimeout(() => resolve({ gotFail: false }), 4000);
    });

    assert.strictEqual(noDevResult.gotFail, true, 'Must receive auth_fail when deviceId is missing');
    assert.strictEqual(noDevResult.message, 'deviceId required');
    socketNoDev.close();

    // 13b. Valid token + deviceId -> socket.data.deviceId === sent value
    const customDeviceId = 'dev_custom_unique_999';
    const socketWithDev: ClientSocketType = ClientSocket(serverUrl, {
      transports: ['websocket'],
      reconnection: false,
    });

    const clientSocketId = await new Promise<string>((resolve, reject) => {
      socketWithDev.on('connect', () => {
        socketWithDev.emit('auth', {
          type: 'auth',
          payload: { token: userA.token, deviceId: customDeviceId },
          timestamp: Date.now(),
        });
      });

      socketWithDev.on('auth_ok', (msg: any) => {
        assert.strictEqual(msg?.payload?.deviceId, customDeviceId);
        resolve(socketWithDev.id!);
      });

      socketWithDev.on('auth_fail', (err: any) => {
        reject(new Error(err?.payload?.message || 'auth_fail'));
      });

      setTimeout(() => reject(new Error('Timeout waiting for auth_ok')), 4000);
    });

    // Verify stored on socket.data on server
    const serverSideSocket = ioServer.sockets.sockets.get(clientSocketId);
    assert.ok(serverSideSocket, 'Server side socket instance must exist');
    assert.strictEqual(
      serverSideSocket.data.deviceId,
      customDeviceId,
      'socket.data.deviceId must match sent value'
    );

    socketWithDev.close();
  });

  it('14. listen_together_sync relays queueSnapshot (max 30 items) and filters items without trackId', async () => {
    const ts = Date.now();
    const hostUser = await createTestUser(`rt_sync_h_${ts}`, `rt_sync_h_${ts}@test.com`);
    const guestUser = await createTestUser(`rt_sync_g_${ts}`, `rt_sync_g_${ts}@test.com`);
    const hostToken = generateAccessToken(hostUser.id, hostUser.username);
    const guestToken = generateAccessToken(guestUser.id, guestUser.username);

    const testSessionId = crypto.randomUUID();
    const testJoinCode = makeJoinCode();
    await sqlAuthRole`
      INSERT INTO listen_sessions (id, join_code, host_user_id, control_mode, current_provider, current_provider_track_id, position_ms, playing)
      VALUES (${testSessionId}, ${testJoinCode}, ${hostUser.id}, 'open', 'jiosaavn', 'track_curr', 0, true)
    `;
    await sqlAuthRole`
      INSERT INTO listen_participants (session_id, user_id, joined_at)
      VALUES (${testSessionId}, ${guestUser.id}, NOW())
    `;

    const socketHost: ClientSocketType = ClientSocket(serverUrl, {
      transports: ['websocket'],
      reconnection: false,
    });
    const socketGuest: ClientSocketType = ClientSocket(serverUrl, {
      transports: ['websocket'],
      reconnection: false,
    });

    try {
      await Promise.all([
        new Promise<void>((resolve) => {
          const doAuth = () => {
            socketHost.emit('auth', {
              type: 'auth',
              payload: { token: hostToken, deviceId: 'dev_host_123' },
              timestamp: Date.now(),
            });
          };
          socketHost.on('auth_ok', () => resolve());
          if (socketHost.connected) doAuth();
          else socketHost.on('connect', doAuth);
        }),
        new Promise<void>((resolve) => {
          const doAuth = () => {
            socketGuest.emit('auth', {
              type: 'auth',
              payload: { token: guestToken, deviceId: 'dev_guest_456' },
              timestamp: Date.now(),
            });
          };
          socketGuest.on('auth_ok', () => resolve());
          if (socketGuest.connected) doAuth();
          else socketGuest.on('connect', doAuth);
        }),
      ]);

      const roomId = `listen:${testSessionId}`;
      await Promise.all([
        new Promise<void>((resolve) => socketHost.once('room_joined', () => resolve())),
        new Promise<void>((resolve) => socketGuest.once('room_joined', () => resolve())),
        (async () => {
          socketHost.emit('join_room', { room: roomId });
          socketGuest.emit('join_room', { room: roomId });
        })(),
      ]);

      // Prepare 35 items, some missing trackId
      const testItems: any[] = [];
      for (let i = 0; i < 35; i++) {
        testItems.push({
          trackId: i === 5 ? '' : `track_${i}`,
          provider: 'jiosaavn',
          title: `Song ${i}`,
          artist: 'Artist',
          artworkUrl: 'https://example.com/art.jpg',
        });
      }

      const receivedSyncPromise = new Promise<any>((resolve, reject) => {
        socketGuest.on('listen_together_sync', (msg: any) => {
          resolve(msg);
        });
        setTimeout(() => reject(new Error('Timed out waiting for listen_together_sync')), 12000);
      });

      socketHost.emit('listen_together_sync', {
        type: 'listen_together_sync',
        room: roomId,
        payload: {
          sessionId: testSessionId,
          trackId: 'track_curr',
          positionMs: 12000,
          playing: true,
          queueSnapshot: testItems,
        },
        timestamp: Date.now(),
      });

      const received = await receivedSyncPromise;
      assert.ok(received?.payload?.queueSnapshot, 'queueSnapshot must be present in sync message');
      const snapshot = received.payload.queueSnapshot;
      // Must be capped at <= 30
      assert.ok(snapshot.length <= 30, `Snapshot length ${snapshot.length} must be <= 30`);
      // Item missing trackId must be filtered out
      assert.strictEqual(snapshot.some((item: any) => !item.trackId), false, 'Items missing trackId must be filtered out');
    } finally {
      socketHost.close();
      socketGuest.close();
      await sqlAuthRole`DELETE FROM listen_participants WHERE session_id = ${testSessionId}`.catch(() => {});
      await sqlAuthRole`DELETE FROM listen_sessions WHERE id = ${testSessionId}`.catch(() => {});
      await cleanupTestUsers([hostUser.id, guestUser.id]).catch(() => {});
    }
  });
});

