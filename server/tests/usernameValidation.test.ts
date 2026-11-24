import { describe, it } from 'node:test';
import assert from 'node:assert';
import {
  validateUsername,
  countDigits,
  MAX_USERNAME_DIGITS,
  USERNAME_REGEX,
} from '../src/utils/username.js';

describe('Server Username Validator (Source of Truth)', () => {
  it('rejects empty or whitespace-only values', () => {
    assert.deepStrictEqual(validateUsername(''), { ok: false, reason: 'Username is required.' });
    assert.deepStrictEqual(validateUsername('   '), { ok: false, reason: 'Username is required.' });
    assert.deepStrictEqual(validateUsername(null), { ok: false, reason: 'Username is required.' });
    assert.deepStrictEqual(validateUsername(undefined), { ok: false, reason: 'Username is required.' });
  });

  it('rejects uppercase letters (does not silently lowercase)', () => {
    const res = validateUsername('MUE');
    assert.strictEqual(res.ok, false);
    if (!res.ok) {
      assert.strictEqual(res.reason, 'Username must be lowercase.');
    }

    const mixed = validateUsername('mueTest');
    assert.strictEqual(mixed.ok, false);
    if (!mixed.ok) {
      assert.strictEqual(mixed.reason, 'Username must be lowercase.');
    }
  });

  it('rejects usernames shorter than 3 chars (e.g. "ab")', () => {
    const res = validateUsername('ab');
    assert.strictEqual(res.ok, false);
    if (!res.ok) {
      assert.strictEqual(res.reason, 'Username must be 3–20 characters.');
    }
  });

  it('rejects usernames longer than 20 chars (21 chars)', () => {
    const res = validateUsername('a'.repeat(21));
    assert.strictEqual(res.ok, false);
    if (!res.ok) {
      assert.strictEqual(res.reason, 'Username must be 3–20 characters.');
    }
  });

  it('rejects all digits (e.g. "1234", "12345")', () => {
    const res4 = validateUsername('1234');
    assert.strictEqual(res4.ok, false);
    if (!res4.ok) {
      assert.strictEqual(res4.reason, 'Username cannot be only numbers.');
    }

    const res5 = validateUsername('12345');
    assert.strictEqual(res5.ok, false);
    if (!res5.ok) {
      assert.strictEqual(res5.reason, 'Username cannot be only numbers.');
    }
  });

  it('rejects usernames with 5 digits (e.g. "abc12345")', () => {
    const res = validateUsername('abc12345');
    assert.strictEqual(res.ok, false);
    if (!res.ok) {
      assert.strictEqual(res.reason, 'Username can contain at most 4 numbers.');
    }
  });

  it('accepts usernames with 4 digits (e.g. "abc1234")', () => {
    const res = validateUsername('abc1234');
    assert.strictEqual(res.ok, true);
    if (res.ok) {
      assert.strictEqual(res.value, 'abc1234');
    }
  });

  it('rejects leading hyphen ("-mue")', () => {
    const res = validateUsername('-mue');
    assert.strictEqual(res.ok, false);
    if (!res.ok) {
      assert.ok(res.reason.includes('Username must start with a letter, number, or underscore'));
    }
  });

  it('rejects leading dot (".mue")', () => {
    const res = validateUsername('.mue');
    assert.strictEqual(res.ok, false);
    if (!res.ok) {
      assert.ok(res.reason.includes('Username must start with a letter, number, or underscore'));
    }
  });

  it('accepts leading underscore ("_mue_")', () => {
    const res = validateUsername('_mue_');
    assert.strictEqual(res.ok, true);
    if (res.ok) {
      assert.strictEqual(res.value, '_mue_');
    }
  });

  it('accepts leading digit ("234mue")', () => {
    const res = validateUsername('234mue');
    assert.strictEqual(res.ok, true);
    if (res.ok) {
      assert.strictEqual(res.value, '234mue');
    }
  });

  it('accepts dot in the middle ("mue.234")', () => {
    const res = validateUsername('mue.234');
    assert.strictEqual(res.ok, true);
    if (res.ok) {
      assert.strictEqual(res.value, 'mue.234');
    }
  });

  it('accepts valid usernames with hyphen and underscore ("mue-track_1")', () => {
    const res = validateUsername('mue-track_1');
    assert.strictEqual(res.ok, true);
    if (res.ok) {
      assert.strictEqual(res.value, 'mue-track_1');
    }
  });

  it('countDigits correctly calculates digit count', () => {
    assert.strictEqual(countDigits('mue'), 0);
    assert.strictEqual(countDigits('mue12'), 2);
    assert.strictEqual(countDigits('1234'), 4);
    assert.strictEqual(countDigits('12345'), 5);
  });
});

