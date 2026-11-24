import { describe, it, before, after } from 'node:test';
import assert from 'node:assert';
import { app } from '../src/app.js';
import { createTestUser, cleanupTestUsers } from './helpers/rls.js';
import { generateAccessToken } from '../src/services/authService.js';

describe('Profile Routes Tests', () => {
  let user: { id: string; username: string; email: string };
  let token: string;

  before(async () => {
    const ts = Date.now();
    user = await createTestUser(`prof_${ts}`, `prof_${ts}@test.com`, false);
    token = generateAccessToken(user.id, user.username);
  });

  after(async () => {
    try {
      await cleanupTestUsers([user.id]);
    } catch {}
  });

  it('GET /api/profile/me returns 401 without auth token', async () => {
    const res = await app.fetch(new Request('http://localhost:4000/api/profile/me'));
    assert.strictEqual(res.status, 401);
  });

  it('GET /api/profile/me returns 200 with user profile when authenticated', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/profile/me', {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      })
    );
    assert.strictEqual(res.status, 200);
    const body = (await res.json()) as any;
    assert.strictEqual(body.id, user.id);
    assert.strictEqual(body.username, user.username);
  });

  it('PATCH /api/profile/me updates displayName and bio', async () => {
    const newName = 'Updated Display Name';
    const newBio = 'This is an updated bio for testing.';

    const res = await app.fetch(
      new Request('http://localhost:4000/api/profile/me', {
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          displayName: newName,
          bio: newBio,
        }),
      })
    );

    assert.strictEqual(res.status, 200);
    const body = (await res.json()) as any;
    assert.strictEqual(body.displayName, newName);
    assert.strictEqual(body.bio, newBio);

    // Verify GET reflects the changes
    const getRes = await app.fetch(
      new Request('http://localhost:4000/api/profile/me', {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      })
    );
    assert.strictEqual(getRes.status, 200);
    const getBody = (await getRes.json()) as any;
    assert.strictEqual(getBody.displayName, newName);
    assert.strictEqual(getBody.bio, newBio);
  });

  it('PATCH /api/profile/me rejects invalid displayName lengths (empty or > 40 chars)', async () => {
    // Empty displayName
    const emptyRes = await app.fetch(
      new Request('http://localhost:4000/api/profile/me', {
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          displayName: '   ',
        }),
      })
    );
    assert.strictEqual(emptyRes.status, 400);

    // Excessively long displayName (>40 chars)
    const longName = 'A'.repeat(41);
    const longRes = await app.fetch(
      new Request('http://localhost:4000/api/profile/me', {
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          displayName: longName,
        }),
      })
    );
    assert.strictEqual(longRes.status, 400);
  });

  it('PATCH /api/profile/me rejects bio > 160 chars', async () => {
    const longBio = 'B'.repeat(161);
    const res = await app.fetch(
      new Request('http://localhost:4000/api/profile/me', {
        method: 'PATCH',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          bio: longBio,
        }),
      })
    );
    assert.strictEqual(res.status, 400);
  });
});

