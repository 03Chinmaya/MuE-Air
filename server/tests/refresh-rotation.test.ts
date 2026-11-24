import { describe, it, before, after } from 'node:test';
import assert from 'node:assert';
import { app } from '../src/app.js';
import { sqlAuthRole } from './helpers/rls.js';
import { closeConnections } from '../src/db/connection.js';

describe('Refresh Rotation & Token Reuse Detection', () => {
  const timestamp = Math.floor(Math.random() * 9000 + 1000);
  const testUser = {
    username: `rot_u_${timestamp}`,
    email: `rot_${Date.now()}@example.com`,
    password: 'Password123!',
  };

  let userId = '';
  let accessToken = '';
  let refreshToken = '';

  before(async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/signup', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(testUser),
      })
    );
    assert.strictEqual(res.status, 201);
    const data = await res.json() as any;
    userId = data.user.id;
    accessToken = data.accessToken;
    refreshToken = data.refreshToken;
  });

  after(async () => {
    if (userId) {
      try {
        await sqlAuthRole`DELETE FROM users WHERE id = ${userId}`;
      } catch {}
    }
  });

  it('Refresh returns new token pair and rotates token', async () => {
    const initialRefresh = refreshToken;
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: initialRefresh }),
      })
    );

    assert.strictEqual(res.status, 200);
    const data = await res.json() as any;
    assert.ok(data.accessToken);
    assert.ok(data.refreshToken);
    assert.notStrictEqual(data.refreshToken, initialRefresh);

    // Verify previous_token_hash is correctly set in database
    const sessions = await sqlAuthRole`
      SELECT id, refresh_token_hash, previous_token_hash
      FROM user_sessions
      WHERE user_id = ${userId}
    `;
    assert.strictEqual(sessions.length, 1);
    assert.ok(sessions[0].previous_token_hash, 'previous_token_hash must be set after rotation');

    // Update current refreshToken
    const rotatedToken = data.refreshToken;

    // Old token is immediately invalid for standard refresh
    const oldTokenRes = await app.fetch(
      new Request('http://localhost:4000/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: initialRefresh }),
      })
    );
    assert.strictEqual(oldTokenRes.status, 401);
    const oldData = await oldTokenRes.json() as any;
    assert.ok(
      oldData.error.includes('Token reuse detected') || oldData.error.includes('Invalid'),
      'Must reject reused token'
    );

    // Reusing old token revokes ALL sessions for user
    const remainingSessions = await sqlAuthRole`
      SELECT id FROM user_sessions WHERE user_id = ${userId}
    `;
    assert.strictEqual(
      remainingSessions.length,
      0,
      'Reusing old rotated token must revoke all active sessions for security'
    );

    // Even the rotated token is now invalid because session was revoked
    const rotatedRes = await app.fetch(
      new Request('http://localhost:4000/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: rotatedToken }),
      })
    );
    assert.strictEqual(rotatedRes.status, 401);
  });
});
