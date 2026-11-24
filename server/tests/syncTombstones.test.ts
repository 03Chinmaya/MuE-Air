import { describe, it, before, after } from 'node:test';
import assert from 'node:assert';
import { eq } from 'drizzle-orm';
import { app } from '../src/app.js';
import { createTestUser, cleanupTestUsers } from './helpers/rls.js';
import { generateAccessToken } from '../src/services/authService.js';
import { authDb } from '../src/db/connection.js';
import { syncOperations } from '../src/db/schema.js';
import { runSyncTombstoneSweep } from '../src/jobs/syncTombstoneSweeper.js';

describe('Phase 12 Part 1a — Server Sync Tombstones & Sweeper Tests', () => {
  let userA: { id: string; username: string; email: string };
  let userB: { id: string; username: string; email: string };
  let tokenA: string;
  let tokenB: string;

  before(async () => {
    const ts = Date.now();
    userA = await createTestUser(`tomb_user_a_${ts}`, `tomb_a_${ts}@test.com`);
    userB = await createTestUser(`tomb_user_b_${ts}`, `tomb_b_${ts}@test.com`);

    tokenA = generateAccessToken(userA.id, userA.username);
    tokenB = generateAccessToken(userB.id, userB.username);

    const testIds = [
      'aaaaaaaa-1111-4111-8111-aaaaaaaaaaaa',
      'bbbbbbbb-4646-4111-8111-bbbbbbbbbbbb',
      'cccccccc-3030-4111-8111-cccccccccccc',
      'dddddddd-1515-4111-8111-dddddddddddd',
      'eeeeeeee-1010-4111-8111-eeeeeeeeeeee',
    ];
    for (const id of testIds) {
      await authDb.delete(syncOperations).where(eq(syncOperations.id, id)).catch(() => {});
    }
  });

  after(async () => {
    const testIds = [
      'aaaaaaaa-1111-4111-8111-aaaaaaaaaaaa',
      'bbbbbbbb-4646-4111-8111-bbbbbbbbbbbb',
      'cccccccc-3030-4111-8111-cccccccccccc',
      'dddddddd-1515-4111-8111-dddddddddddd',
      'eeeeeeee-1010-4111-8111-eeeeeeeeeeee',
    ];
    for (const id of testIds) {
      await authDb.delete(syncOperations).where(eq(syncOperations.id, id)).catch(() => {});
    }
    try {
      await cleanupTestUsers([userA.id, userB.id]);
    } catch {}
  });

  it('pull with since returns delete ops for the user', async () => {
    const t0 = new Date(Date.now() - 60000);

    // Seed a delete operation in sync_operations for user A
    const opId = 'aaaaaaaa-1111-4111-8111-aaaaaaaaaaaa';
    await authDb.insert(syncOperations).values({
      id: opId,
      userId: userA.id,
      type: 'delete',
      entityType: 'like',
      entityId: 'track_del_test_1',
      payload: { provider: 'youtube_music' },
      createdAt: new Date(Date.now() - 30000),
      status: 'pending',
    });

    const res = await app.fetch(
      new Request(`http://localhost:4000/api/sync/pull?since=${encodeURIComponent(t0.toISOString())}`, {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${tokenA}`,
        },
      })
    );

    assert.strictEqual(res.status, 200);
    const data = (await res.json()) as any;
    const items = data.items || [];

    const deleteItem = items.find((i: any) => i.entity === 'like' && i.type === 'delete' && i.payload?.providerTrackId === 'track_del_test_1');
    assert.ok(deleteItem, 'Expected tombstone item for deleted track');
    assert.strictEqual(deleteItem.type, 'delete');
  });

  it('pull without since does NOT include tombstones (full sync gets current state, not historical deletions)', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/sync/pull', {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${tokenA}`,
        },
      })
    );

    assert.strictEqual(res.status, 200);
    const data = (await res.json()) as any;
    const items = data.items || [];

    const deleteItem = items.find((i: any) => i.type === 'delete');
    assert.strictEqual(deleteItem, undefined, 'Full sync without since must NOT include historical tombstones');
  });

  it('tombstones are user-scoped (no cross-user leak)', async () => {
    const t0 = new Date(Date.now() - 60000);

    // User B pulls with since = t0
    const res = await app.fetch(
      new Request(`http://localhost:4000/api/sync/pull?since=${encodeURIComponent(t0.toISOString())}`, {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${tokenB}`,
        },
      })
    );

    assert.strictEqual(res.status, 200);
    const data = (await res.json()) as any;
    const items = data.items || [];

    const leakedItem = items.find((i: any) => i.payload?.providerTrackId === 'track_del_test_1');
    assert.strictEqual(leakedItem, undefined, 'User B must not see User A tombstones');
  });

  it('46-day-old delete op is purged by sweeper', async () => {
    const opId = 'bbbbbbbb-4646-4111-8111-bbbbbbbbbbbb';
    const oldTime = new Date(Date.now() - 46 * 86400 * 1000);

    await authDb.insert(syncOperations).values({
      id: opId,
      userId: userA.id,
      type: 'delete',
      entityType: 'like',
      entityId: 'track_old_del',
      payload: {},
      createdAt: oldTime,
      status: 'pending',
    });

    await runSyncTombstoneSweep();

    const rows = await authDb.select({ id: syncOperations.id }).from(syncOperations).where(eq(syncOperations.id, opId));
    assert.strictEqual(rows.length, 0, '46-day-old delete tombstone should be purged');
  });

  it('30-day-old delete op is retained by sweeper', async () => {
    const opId = 'cccccccc-3030-4111-8111-cccccccccccc';
    const retainTime = new Date(Date.now() - 30 * 86400 * 1000);

    await authDb.insert(syncOperations).values({
      id: opId,
      userId: userA.id,
      type: 'delete',
      entityType: 'like',
      entityId: 'track_retain_del',
      payload: {},
      createdAt: retainTime,
      status: 'pending',
    });

    await runSyncTombstoneSweep();

    const rows = await authDb.select({ id: syncOperations.id }).from(syncOperations).where(eq(syncOperations.id, opId));
    assert.strictEqual(rows.length, 1, '30-day-old delete tombstone should be retained within 45d window');
  });

  it('15-day-old create/update op is purged by sweeper', async () => {
    const opId = 'dddddddd-1515-4111-8111-dddddddddddd';
    const oldCreateTime = new Date(Date.now() - 15 * 86400 * 1000);

    await authDb.insert(syncOperations).values({
      id: opId,
      userId: userA.id,
      type: 'create',
      entityType: 'like',
      entityId: 'track_old_create',
      payload: {},
      createdAt: oldCreateTime,
      status: 'pending',
    });

    await runSyncTombstoneSweep();

    const rows = await authDb.select({ id: syncOperations.id }).from(syncOperations).where(eq(syncOperations.id, opId));
    assert.strictEqual(rows.length, 0, '15-day-old create op should be purged (> 14d limit)');
  });

  it('10-day-old create/update op is retained by sweeper', async () => {
    const opId = 'eeeeeeee-1010-4111-8111-eeeeeeeeeeee';
    const retainCreateTime = new Date(Date.now() - 10 * 86400 * 1000);

    await authDb.insert(syncOperations).values({
      id: opId,
      userId: userA.id,
      type: 'create',
      entityType: 'like',
      entityId: 'track_retain_create',
      payload: {},
      createdAt: retainCreateTime,
      status: 'pending',
    });

    await runSyncTombstoneSweep();

    const rows = await authDb.select({ id: syncOperations.id }).from(syncOperations).where(eq(syncOperations.id, opId));
    assert.strictEqual(rows.length, 1, '10-day-old create op should be retained (< 14d limit)');
  });
});
