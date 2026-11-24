import { describe, it, before, after } from 'node:test';
import assert from 'node:assert';
import { app } from '../src/app.js';
import { sqlAuthRole } from './helpers/rls.js';
import { closeConnections } from '../src/db/connection.js';

describe('Device Management & Limit Enforcement Tests', () => {
  const timestamp = Math.floor(Math.random() * 9000 + 1000);
  const testUser = {
    username: `dev_u_${timestamp}`,
    email: `dev_${Date.now()}@example.com`,
    password: 'Password123!',
  };

  let userId = '';
  let accessToken = '';

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
  });

  after(async () => {
    if (userId) {
      try {
        await sqlAuthRole`DELETE FROM users WHERE id = ${userId}`;
      } catch {}
    }
  });

  it('Register device returns deviceId and metadata', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/devices/register', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${accessToken}`,
        },
        body: JSON.stringify({
          deviceName: 'Primary Test Phone',
          platform: 'android',
        }),
      })
    );

    assert.strictEqual(res.status, 201);
    const device = await res.json() as any;
    assert.ok(device.id, 'Register device must return deviceId');
    assert.strictEqual(device.deviceName, 'Primary Test Phone');
    assert.strictEqual(device.platform, 'android');
  });

  it('Enforces max 10 devices and revokes oldest on 11th registration', async () => {
    // Current devices count: 1 from previous test.
    // Register 9 more devices to reach 10 total.
    const registeredDeviceIds: string[] = [];

    // Let's get the existing device
    const listRes = await app.fetch(
      new Request('http://localhost:4000/api/auth/devices', {
        method: 'GET',
        headers: { Authorization: `Bearer ${accessToken}` },
      })
    );
    const initialList = await listRes.json() as any[];
    const firstDeviceId = initialList[0].id;

    for (let i = 2; i <= 10; i++) {
      const regRes = await app.fetch(
        new Request('http://localhost:4000/api/auth/devices/register', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${accessToken}`,
          },
          body: JSON.stringify({
            deviceName: `Test Device ${i}`,
            platform: 'android',
          }),
        })
      );
      assert.strictEqual(regRes.status, 201);
      const dev = await regRes.json() as any;
      registeredDeviceIds.push(dev.id);
    }

    // Verify 10 devices currently exist
    const countCheck = await sqlAuthRole`
      SELECT COUNT(*)::int as cnt FROM user_devices WHERE user_id = ${userId}
    `;
    assert.strictEqual(countCheck[0].cnt, 10, 'Must have exactly 10 devices registered');

    // Register 11th device
    const reg11Res = await app.fetch(
      new Request('http://localhost:4000/api/auth/devices/register', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${accessToken}`,
        },
        body: JSON.stringify({
          deviceName: '11th Overflow Device',
          platform: 'android',
        }),
      })
    );
    assert.strictEqual(reg11Res.status, 201);

    // Verify total count is still capped at 10
    const finalCount = await sqlAuthRole`
      SELECT COUNT(*)::int as cnt FROM user_devices WHERE user_id = ${userId}
    `;
    assert.strictEqual(finalCount[0].cnt, 10, 'Count must remain capped at max 10');

    // Verify the oldest device (firstDeviceId) was revoked/deleted
    const oldestCheck = await sqlAuthRole`
      SELECT id FROM user_devices WHERE id = ${firstDeviceId}
    `;
    assert.strictEqual(oldestCheck.length, 0, 'Oldest device must be removed on 11th registration');
  });

  it('Revoking a device invalidates that device refresh tokens', async () => {
    // Login to create a session bound to a device
    const loginRes = await app.fetch(
      new Request('http://localhost:4000/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          usernameOrEmail: testUser.username,
          password: testUser.password,
          deviceName: 'Revoke-Target Device',
        }),
      })
    );
    assert.strictEqual(loginRes.status, 200);
    const loginData = await loginRes.json() as any;
    const deviceRefreshToken = loginData.refreshToken;

    // Find the device created for this login
    const devicesRes = await app.fetch(
      new Request('http://localhost:4000/api/auth/devices', {
        method: 'GET',
        headers: { Authorization: `Bearer ${loginData.accessToken}` },
      })
    );
    const devicesList = await devicesRes.json() as any[];
    const targetDevice = devicesList.find((d) => d.deviceName === 'Revoke-Target Device');
    assert.ok(targetDevice, 'Must find Revoke-Target Device');

    // Verify refresh token works before revocation
    const refreshBefore = await app.fetch(
      new Request('http://localhost:4000/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: deviceRefreshToken }),
      })
    );
    assert.strictEqual(refreshBefore.status, 200);
    const newRefresh = (await refreshBefore.json() as any).refreshToken;

    // Revoke the device via DELETE /api/auth/devices/:id
    const revokeRes = await app.fetch(
      new Request(`http://localhost:4000/api/auth/devices/${targetDevice.id}`, {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${loginData.accessToken}` },
      })
    );
    assert.strictEqual(revokeRes.status, 200);

    // Refresh token bound to revoked device must now fail with 401
    const refreshAfterRevoke = await app.fetch(
      new Request('http://localhost:4000/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: newRefresh }),
      })
    );
    assert.strictEqual(refreshAfterRevoke.status, 401, 'Tokens of revoked device must fail refresh');
  });
});
