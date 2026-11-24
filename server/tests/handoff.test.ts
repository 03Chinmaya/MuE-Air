/**
 * Phase 11E.1 — Playback Handoff Session & Device Registration Tests
 * Verifies:
 * - POST /api/handoff/request creates row with expiresAt = now + 30s and status = 'pending'
 * - GET /api/handoff/devices returns caller's other devices only (excludeDeviceId filter)
 * - RLS enforcement: non-owner SELECT on handoff_sessions returns 0 rows (via sqlAppUser)
 * - Non-participant access to another user's handoff session is rejected (404/403)
 * - Attacker using another user's device UUID is rejected with 400 ('device_id_taken')
 */

import { describe, it, before, after } from 'node:test';
import assert from 'node:assert';
import http from 'node:http';
import { io as ClientSocket, Socket as ClientSocketType } from 'socket.io-client';
import { app } from '../src/app.js';
import { asUser, sqlAuthRole } from './helpers/rls.js';
import { closeConnections } from '../src/db/connection.js';
import { initializeRealtimeServer } from '../src/realtime/socketServer.js';
import { presence } from '../src/realtime/presence.js';
import { runMessageSweep } from '../src/jobs/messageSweeper.js';

function connectAuthSocket(url: string, token: string, deviceId: string): Promise<ClientSocketType> {
  return new Promise((resolve, reject) => {
    const socket = ClientSocket(url, {
      transports: ['websocket'],
      reconnection: false,
    });
    socket.on('connect', () => {
      socket.emit('auth', {
        type: 'auth',
        payload: { token, deviceId },
        timestamp: Date.now(),
      });
    });
    socket.on('auth_ok', () => resolve(socket));
    socket.on('auth_fail', (err) => reject(new Error('Auth failed: ' + JSON.stringify(err))));
    setTimeout(() => reject(new Error('Socket connect timed out')), 5000);
  });
}

function joinRoom(socket: ClientSocketType, room: string): Promise<void> {
  return new Promise((resolve, reject) => {
    socket.emit('join_room', {
      type: 'join_room',
      payload: { room },
      timestamp: Date.now(),
    });
    socket.on('room_joined', (msg) => {
      if (msg?.payload?.room === room || msg?.room === room) {
        resolve();
      }
    });
    setTimeout(() => reject(new Error(`Failed to join room ${room}`)), 4000);
  });
}

describe('Phase 11E.1 & 11E.2 — Playback Handoff Tests', () => {
  let server: http.Server;
  let serverPort = 0;
  let serverUrl = '';

  const ts = Math.floor(Math.random() * 9000 + 1000);
  const userAData = {
    username: `ho_a_${ts}`,
    email: `handoff_a_${ts}@example.com`,
    password: 'Password123!',
  };
  const userBData = {
    username: `ho_b_${ts}`,
    email: `handoff_b_${ts}@example.com`,
    password: 'Password123!',
  };

  let userAId = '';
  let tokenA = '';
  let userBId = '';
  let tokenB = '';

  let deviceA1Id = '';
  let deviceA2Id = '';

  let createdSessionId = '';

  before(async () => {
    server = http.createServer();
    initializeRealtimeServer(server);
    await new Promise<void>((resolve) => {
      server.listen(0, () => {
        const addr = server.address() as any;
        serverPort = addr.port;
        serverUrl = `http://localhost:${serverPort}`;
        resolve();
      });
    });

    // 1. Create User A
    const resA = await app.fetch(
      new Request('http://localhost:4000/api/auth/signup', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(userAData),
      })
    );
    assert.strictEqual(resA.status, 201);
    const dataA = (await resA.json()) as any;
    userAId = dataA.user.id;
    tokenA = dataA.accessToken;

    // 2. Create User B
    const resB = await app.fetch(
      new Request('http://localhost:4000/api/auth/signup', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(userBData),
      })
    );
    assert.strictEqual(resB.status, 201);
    const dataB = (await resB.json()) as any;
    userBId = dataB.user.id;
    tokenB = dataB.accessToken;

    // 3. Register two devices for User A
    const dev1Res = await app.fetch(
      new Request('http://localhost:4000/api/auth/devices/register', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          deviceName: 'Pixel 8 Phone',
          platform: 'android',
        }),
      })
    );
    assert.strictEqual(dev1Res.status, 201);
    const dev1 = (await dev1Res.json()) as any;
    deviceA1Id = dev1.id;

    const dev2Res = await app.fetch(
      new Request('http://localhost:4000/api/auth/devices/register', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          deviceName: 'Galaxy Tab Tablet',
          platform: 'android',
        }),
      })
    );
    assert.strictEqual(dev2Res.status, 201);
    const dev2 = (await dev2Res.json()) as any;
    deviceA2Id = dev2.id;

    // Mark deviceA2Id online in presence for handoff requests
    presence.addSocket(userAId, 'mock-socket-a2', deviceA2Id);
  });

  after(async () => {
    if (server) {
      await new Promise<void>((resolve) => server.close(() => resolve()));
    }
    presence._clear();
    try {
      if (userAId) {
        await sqlAuthRole`DELETE FROM users WHERE id = ${userAId}`;
      }
      if (userBId) {
        await sqlAuthRole`DELETE FROM users WHERE id = ${userBId}`;
      }
    } catch {}
    await closeConnections();
  });

  it('POST /api/handoff/request creates row with expiresAt = now + 30s', async () => {
    const beforeRequest = Date.now();
    const playbackState = {
      positionMs: 42000,
      isPlaying: true,
      trackMetadata: {
        provider: 'jiosaavn',
        providerTrackId: 'track_123',
        title: 'Midnight Drive',
        artist: 'Synthwave Duo',
        durationMs: 210000,
      },
      queue: [
        {
          provider: 'jiosaavn',
          providerTrackId: 'track_123',
          title: 'Midnight Drive',
          artist: 'Synthwave Duo',
          durationMs: 210000,
        },
      ],
      queueIndex: 0,
      shuffle: false,
      repeatMode: 'off',
    };

    const res = await app.fetch(
      new Request('http://localhost:4000/api/handoff/request', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenA}`,
        },
        body: JSON.stringify({
          sourceDeviceId: deviceA1Id,
          targetDeviceId: deviceA2Id,
          playbackState,
        }),
      })
    );

    assert.strictEqual(res.status, 201);
    const session = (await res.json()) as any;
    assert.ok(session.id, 'Session id must be returned');
    assert.strictEqual(session.userId, userAId);
    assert.strictEqual(session.sourceDeviceId, deviceA1Id);
    assert.strictEqual(session.targetDeviceId, deviceA2Id);
    assert.strictEqual(session.status, 'pending');

    createdSessionId = session.id;

    assert.ok(session.expiresAt, 'Session must contain expiresAt timestamp');
    const expiresMs = new Date(session.expiresAt).getTime();
    const diffMs = expiresMs - beforeRequest;
    // Expected ~30 seconds into future (allow 20s - 45s window for concurrent DB latency)
    assert.ok(diffMs >= 20000 && diffMs <= 45000, `expiresAt diff (${diffMs}ms) must be ~30s`);
  });

  it("GET /api/handoff/devices returns caller's other devices only", async () => {
    // 1. Without exclude parameter, returns both devices
    const allRes = await app.fetch(
      new Request('http://localhost:4000/api/handoff/devices', {
        headers: { Authorization: `Bearer ${tokenA}` },
      })
    );
    assert.strictEqual(allRes.status, 200);
    const allDevices = (await allRes.json()) as any[];
    assert.ok(allDevices.length >= 2);

    // 2. With excludeDeviceId parameter, excludes caller's current device
    const filteredRes = await app.fetch(
      new Request(`http://localhost:4000/api/handoff/devices?excludeDeviceId=${deviceA1Id}`, {
        headers: { Authorization: `Bearer ${tokenA}` },
      })
    );
    assert.strictEqual(filteredRes.status, 200);
    const filteredDevices = (await filteredRes.json()) as any[];
    assert.ok(!filteredDevices.some((d) => d.id === deviceA1Id), 'Must exclude caller device');
    assert.ok(filteredDevices.some((d) => d.id === deviceA2Id), 'Must include other registered device');
  });

  it('non-owner SELECT on handoff_sessions returns 0 rows via sqlAppUser (RLS enforcement)', async () => {
    assert.ok(createdSessionId, 'Created session id must exist from earlier test');

    // Owner (User A) querying through sqlAppUser sees their row
    const ownerRows = await asUser(userAId, async (sql) => {
      return await sql`SELECT id, user_id FROM handoff_sessions WHERE id = ${createdSessionId}`;
    });
    assert.strictEqual(ownerRows.length, 1);
    assert.strictEqual(ownerRows[0].user_id, userAId);

    // Non-owner (User B) querying through sqlAppUser sees 0 rows
    const nonOwnerRows = await asUser(userBId, async (sql) => {
      return await sql`SELECT id, user_id FROM handoff_sessions WHERE id = ${createdSessionId}`;
    });
    assert.strictEqual(nonOwnerRows.length, 0, 'non-owner SELECT on handoff_sessions must return 0 rows');
  });

  it('non-participant rejected on handoff session operations', async () => {
    assert.ok(createdSessionId, 'Created session id must exist from earlier test');

    // User B tries to view User A's handoff session -> rejected (404/403)
    const getRes = await app.fetch(
      new Request(`http://localhost:4000/api/handoff/${createdSessionId}`, {
        headers: { Authorization: `Bearer ${tokenB}` },
      })
    );
    assert.ok([403, 404].includes(getRes.status), `Expected 403 or 404, got ${getRes.status}`);

    // User B tries to accept User A's handoff session -> rejected (404/403)
    const acceptRes = await app.fetch(
      new Request(`http://localhost:4000/api/handoff/${createdSessionId}/accept`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${tokenB}` },
      })
    );
    assert.ok([403, 404].includes(acceptRes.status), `Expected 403 or 404, got ${acceptRes.status}`);

    // User B tries to reject User A's handoff session -> rejected (404/403)
    const rejectRes = await app.fetch(
      new Request(`http://localhost:4000/api/handoff/${createdSessionId}/reject`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${tokenB}` },
      })
    );
    assert.ok([403, 404].includes(rejectRes.status), `Expected 403 or 404, got ${rejectRes.status}`);
  });

  it("attacker using another user's device UUID rejected 400", async () => {
    const playbackState = {
      positionMs: 12000,
      isPlaying: true,
      trackMetadata: {
        provider: 'jiosaavn',
        providerTrackId: 'track_steal',
        title: 'Hijack Attempt',
        artist: 'Unknown',
        durationMs: 180000,
      },
      queue: [],
      queueIndex: 0,
      shuffle: false,
      repeatMode: 'off',
    };

    // User B attempts to initiate handoff claiming User A's registered device (deviceA1Id)
    const res = await app.fetch(
      new Request('http://localhost:4000/api/handoff/request', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenB}`,
        },
        body: JSON.stringify({
          sourceDeviceId: deviceA1Id,
          playbackState,
        }),
      })
    );

    assert.strictEqual(res.status, 400, 'Attacker using another user device must be rejected with 400');
    const body = (await res.json()) as any;
    assert.strictEqual(body.error, 'device_id_taken');
  });

  // ==========================================
  // Phase 11E.2 — WebSocket Signaling & Sweeper Subtests
  // ==========================================

  it("handoff_request emits handoff_incoming to sender's own room", async () => {
    const socketA1 = await connectAuthSocket(serverUrl, tokenA, deviceA1Id);
    const socketA2 = await connectAuthSocket(serverUrl, tokenA, deviceA2Id);

    await joinRoom(socketA1, `handoff:${userAId}`);
    await joinRoom(socketA2, `handoff:${userAId}`);

    const incomingPromise = new Promise<any>((resolve) => {
      socketA2.on('handoff_incoming', (msg) => {
        resolve(msg);
      });
    });

    socketA1.emit('handoff_request', {
      type: 'handoff_request',
      payload: {
        sessionId: createdSessionId,
        targetDeviceId: deviceA2Id,
        playbackState: { positionMs: 5000, isPlaying: true },
      },
      timestamp: Date.now(),
    });

    const received = await incomingPromise;
    assert.strictEqual(received.type, 'handoff_incoming');
    assert.strictEqual(received.payload.sessionId, createdSessionId);
    assert.strictEqual(received.payload.fromUserId, userAId);
    assert.strictEqual(received.payload.targetDeviceId, deviceA2Id);

    socketA1.close();
    socketA2.close();
  });

  it('handoff_accept from non-owner is silently ignored', async () => {
    const socketB = await connectAuthSocket(serverUrl, tokenB, 'dev_user_b');
    await joinRoom(socketB, `handoff:${userBId}`);

    let completedReceived = false;
    socketB.on('handoff_completed', () => {
      completedReceived = true;
    });

    // User B tries to accept User A's session
    socketB.emit('handoff_accept', {
      type: 'handoff_accept',
      payload: {
        sessionId: createdSessionId,
      },
      timestamp: Date.now(),
    });

    // Wait 300ms to verify nothing is emitted
    await new Promise((r) => setTimeout(r, 300));
    assert.strictEqual(completedReceived, false, 'Non-owner must not receive handoff_completed');

    // DB session must still be pending
    const [session] = await sqlAuthRole`
      SELECT status FROM handoff_sessions WHERE id = ${createdSessionId}
    `;
    assert.strictEqual(session?.status, 'pending', 'Session must remain pending');

    socketB.close();
  });

  it('handoff_accept on already-accepted session returns error', async () => {
    const socketA1 = await connectAuthSocket(serverUrl, tokenA, deviceA1Id);
    const socketA2 = await connectAuthSocket(serverUrl, tokenA, deviceA2Id);

    await joinRoom(socketA1, `handoff:${userAId}`);
    await joinRoom(socketA2, `handoff:${userAId}`);

    const completedPromise = new Promise<any>((resolve) => {
      socketA1.on('handoff_completed', (msg) => resolve(msg));
    });

    // First accept: valid
    socketA2.emit('handoff_accept', {
      type: 'handoff_accept',
      payload: {
        sessionId: createdSessionId,
      },
      timestamp: Date.now(),
    });

    const completed = await completedPromise;
    assert.strictEqual(completed.payload.sessionId, createdSessionId);

    // Verify DB updated to accepted
    const [session] = await sqlAuthRole`
      SELECT status FROM handoff_sessions WHERE id = ${createdSessionId}
    `;
    assert.strictEqual(session?.status, 'accepted');

    // Second accept: already accepted -> returns 409 error
    const errorPromise = new Promise<any>((resolve) => {
      socketA2.on('error', (err) => resolve(err));
    });

    socketA2.emit('handoff_accept', {
      type: 'handoff_accept',
      payload: {
        sessionId: createdSessionId,
      },
      timestamp: Date.now(),
    });

    const err = await errorPromise;
    // Error envelope: { type: 'error', payload: { code, message }, timestamp }
    assert.strictEqual(err.type, 'error', 'Must use standard error envelope type');
    assert.strictEqual(err.payload.code, 409, 'Must return 409 error for already accepted session');
    assert.strictEqual(err.payload.message, 'handoff_not_pending');

    socketA1.close();
    socketA2.close();
  });

  it('handoff_reject emits with reason', async () => {
    // Insert a new pending session for User A
    const [session] = await sqlAuthRole`
      INSERT INTO handoff_sessions (user_id, source_device_id, status, expires_at)
      VALUES (${userAId}, ${deviceA1Id}, 'pending', NOW() + INTERVAL '30 seconds')
      RETURNING id
    `;
    assert.ok(session?.id);

    const socketA1 = await connectAuthSocket(serverUrl, tokenA, deviceA1Id);
    const socketA2 = await connectAuthSocket(serverUrl, tokenA, deviceA2Id);

    await joinRoom(socketA1, `handoff:${userAId}`);
    await joinRoom(socketA2, `handoff:${userAId}`);

    const rejectedPromise = new Promise<any>((resolve) => {
      socketA1.on('handoff_rejected', (msg) => resolve(msg));
    });

    socketA2.emit('handoff_reject', {
      type: 'handoff_reject',
      payload: {
        sessionId: session.id,
        reason: 'declined',
      },
      timestamp: Date.now(),
    });

    const rejected = await rejectedPromise;
    assert.strictEqual(rejected.type, 'handoff_rejected');
    assert.strictEqual(rejected.payload.sessionId, session.id);
    assert.strictEqual(rejected.payload.reason, 'declined');

    // Verify DB status is rejected
    const [dbSession] = await sqlAuthRole`
      SELECT status FROM handoff_sessions WHERE id = ${session.id}
    `;
    assert.strictEqual(dbSession?.status, 'rejected');

    socketA1.close();
    socketA2.close();
  });

  it('POST /request returns 409 device_offline when target not connected', async () => {
    const offlineDeviceId = '00000000-0000-0000-0000-000000000099';
    const res = await app.fetch(
      new Request('http://localhost:4000/api/handoff/request', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${tokenB}`,
        },
        body: JSON.stringify({
          sourceDeviceId: '00000000-0000-0000-0000-000000000001',
          targetDeviceId: offlineDeviceId,
          playbackState: { positionMs: 1000, isPlaying: true },
        }),
      })
    );
    assert.strictEqual(res.status, 409);
    const json = (await res.json()) as any;
    assert.strictEqual(json.error, 'device_offline');
  });

  it('Sweeper deletes pending sessions older than 30s', async () => {
    const [expired] = await sqlAuthRole`
      INSERT INTO handoff_sessions (user_id, source_device_id, status, expires_at)
      VALUES (${userAId}, ${deviceA1Id}, 'pending', NOW() - INTERVAL '31 seconds')
      RETURNING id
    `;
    assert.ok(expired?.id);

    const [active] = await sqlAuthRole`
      INSERT INTO handoff_sessions (user_id, source_device_id, status, expires_at)
      VALUES (${userAId}, ${deviceA1Id}, 'pending', NOW() + INTERVAL '30 seconds')
      RETURNING id
    `;
    assert.ok(active?.id);

    await runMessageSweep();

    const [foundExpired] = await sqlAuthRole`
      SELECT id FROM handoff_sessions WHERE id = ${expired.id}
    `;
    assert.strictEqual(foundExpired, undefined, 'Expired pending session must be swept');

    const [foundActive] = await sqlAuthRole`
      SELECT id FROM handoff_sessions WHERE id = ${active.id}
    `;
    assert.ok(foundActive, 'Active pending session must remain');

    await sqlAuthRole`DELETE FROM handoff_sessions WHERE id = ${active.id}`;
  });

  it('Rate limit: 4th handoff_request within 60s returns 429', async () => {
    // User A has already made 1 request in the first test.
    // Make requests until we hit the 4th total for User A.
    let hit429 = false;
    for (let i = 0; i < 4; i++) {
      const res = await app.fetch(
        new Request('http://localhost:4000/api/handoff/request', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${tokenA}`,
          },
          body: JSON.stringify({
            sourceDeviceId: deviceA1Id,
            targetDeviceId: deviceA2Id,
            playbackState: { positionMs: 1000, isPlaying: true },
          }),
        })
      );
      if (res.status === 429) {
        hit429 = true;
        const json = (await res.json()) as any;
        assert.strictEqual(json.error, 'rate_limited');
        break;
      }
    }
    assert.strictEqual(hit429, true, '4th request within 60s window must return 429');
  });
});
