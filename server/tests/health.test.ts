import { describe, it } from 'node:test';
import assert from 'node:assert';
import { app } from '../src/app.js';

describe('Health Endpoint Verification Tests', () => {
  it('GET /health returns 200 with status ok and service health', async () => {
    const res = await app.fetch(new Request('http://localhost:4000/health'));
    assert.strictEqual(res.status, 200);

    const data = await res.json() as any;
    assert.strictEqual(data.status, 'ok');
    assert.ok(data.version);
    assert.ok(typeof data.uptimeSeconds === 'number');

    assert.ok(data.services, 'services object must be present in health check response');
    assert.strictEqual(data.services.database, 'ok', 'services.database must be "ok"');
    assert.strictEqual(data.services.redis, 'ok', 'services.redis must be "ok"');
  });
});
