import { test, describe, before, after } from 'node:test';
import assert from 'node:assert/strict';
import { app } from '../src/app.js';
import { safeError } from '../src/lib/errors.js';
import { logger } from '../src/lib/logger.js';
import { Hono } from 'hono';

describe('Phase 7.1: Observability & Logging Tests', () => {
  test('attaches incoming X-Request-Id to response and context', async () => {
    const res = await app.request('/health', {
      headers: {
        'x-request-id': 'custom-req-id-12345',
      },
    });

    assert.equal(res.status, 200);
    assert.equal(res.headers.get('x-request-id'), 'custom-req-id-12345');
  });

  test('generates a new UUID X-Request-Id when none provided', async () => {
    const res = await app.request('/health');

    assert.equal(res.status, 200);
    const reqId = res.headers.get('x-request-id');
    assert.ok(reqId);
    assert.match(
      reqId!,
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
    );
  });

  test('safeError logs unhandled 500 error before masking in production', async () => {
    const origEnv = process.env.NODE_ENV;
    let loggedObj: any = null;
    const origError = logger.error.bind(logger);

    // Mock logger.error to capture logged payload
    (logger as any).error = (obj: any, msg: string) => {
      loggedObj = { obj, msg };
    };

    try {
      process.env.NODE_ENV = 'production';
      const mockContext = {
        get: (key: string) => (key === 'requestId' ? 'test-req-999' : undefined),
      } as any;

      const fakeErr = new Error('Database connection reset');
      const responseBody = safeError(fakeErr, mockContext, 500);

      assert.deepEqual(responseBody, { error: 'internal_error' });
      assert.ok(loggedObj, 'logger.error should have been called');
      assert.equal(loggedObj.obj.requestId, 'test-req-999');
      assert.equal(loggedObj.obj.err, fakeErr);
      assert.equal(loggedObj.msg, 'Unhandled Server Error');
    } finally {
      process.env.NODE_ENV = origEnv;
      (logger as any).error = origError;
    }
  });
});
