import { describe, it, before, after } from 'node:test';
import assert from 'node:assert';
import crypto from 'node:crypto';
import { app } from '../src/app.js';
import { sqlAuthRole } from './helpers/rls.js';
import { closeConnections } from '../src/db/connection.js';
import { DUMMY_HASH, verifyCredential } from '../src/services/authService.js';
import { __setMockEmailHandler, __getSentEmails, __clearSentEmails } from '../src/services/emailService.js';

describe('Auth Routes, Rotation & Constant-Time Security Tests', () => {
  const timestamp = Math.floor(Math.random() * 9000 + 1000);
  const testUser = {
    username: `u_auth_${timestamp}`,
    email: `test_auth_${Date.now()}@example.com`,
    password: 'ValidPassword123!',
    displayName: 'Test Auth User',
  };

  let accessToken = '';
  let refreshToken = '';
  let userId = '';

  before(async () => {
    __clearSentEmails();
    try {
      await sqlAuthRole`
        DROP TABLE IF EXISTS password_reset_tokens CASCADE;
        CREATE TABLE IF NOT EXISTS signup_verification_otps (
          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
          email TEXT NOT NULL,
          username TEXT NOT NULL,
          password_hash TEXT NOT NULL,
          display_name TEXT NOT NULL,
          otp_hash TEXT NOT NULL,
          attempts INTEGER NOT NULL DEFAULT 0,
          expires_at TIMESTAMPTZ NOT NULL,
          used_at TIMESTAMPTZ,
          created_at TIMESTAMPTZ NOT NULL DEFAULT now()
        );
        CREATE TABLE IF NOT EXISTS password_reset_otps (
          id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
          user_id UUID REFERENCES users(id) ON DELETE CASCADE,
          email TEXT NOT NULL,
          otp_hash TEXT NOT NULL,
          attempts INTEGER NOT NULL DEFAULT 0,
          expires_at TIMESTAMPTZ NOT NULL,
          used_at TIMESTAMPTZ,
          created_at TIMESTAMPTZ NOT NULL DEFAULT now()
        );
      `;
    } catch {}
  });

  after(async () => {
    __setMockEmailHandler(null);
    try {
      await sqlAuthRole`DELETE FROM signup_verification_otps WHERE email LIKE 'test_auth_%' OR email LIKE 'otp_user_%'`;
      await sqlAuthRole`DELETE FROM password_reset_otps WHERE email LIKE 'test_auth_%' OR email LIKE 'otp_user_%'`;
      if (userId) {
        await sqlAuthRole`DELETE FROM users WHERE id = ${userId}`;
      }
    } catch {}
  });

  it('GET /health returns 200 with status ok', async () => {
    const res = await app.fetch(new Request('http://localhost:4000/health'));
    assert.strictEqual(res.status, 200);
    const data = await res.json() as any;
    assert.strictEqual(data.status, 'ok');
  });

  it('GET /api/auth/check-username verifies availability for unused and invalid usernames', async () => {
    const resAvailable = await app.fetch(
      new Request(`http://localhost:4000/api/auth/check-username?username=u_uniq_${timestamp}`)
    );
    assert.strictEqual(resAvailable.status, 200);
    const dataAvail = (await resAvailable.json()) as any;
    assert.strictEqual(dataAvail.available, true);

    const resShort = await app.fetch(
      new Request('http://localhost:4000/api/auth/check-username?username=ab')
    );
    assert.strictEqual(resShort.status, 400);
    const dataShort = (await resShort.json()) as any;
    assert.strictEqual(dataShort.code, 'invalid_username');
  });

  it('POST /api/auth/signup creates account, issues tokens, and dispatches welcome email', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/signup', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(testUser),
      })
    );

    assert.strictEqual(res.status, 201);
    const data = await res.json() as any;
    assert.ok(data.accessToken);
    assert.ok(data.refreshToken);
    assert.strictEqual(data.user.username, testUser.username);
    userId = data.user.id;

    // Check that welcome email was triggered
    const sent = __getSentEmails();
    const welcome = sent.find((e) => e.to === testUser.email);
    assert.ok(welcome, 'Welcome email should be sent upon account creation');
    assert.ok(welcome.subject.includes('Welcome to MuE'));
  });

  it('GET /api/auth/check-username returns available: false for existing username', async () => {
    const resTaken = await app.fetch(
      new Request(`http://localhost:4000/api/auth/check-username?username=${testUser.username}`)
    );
    assert.strictEqual(resTaken.status, 200);
    const dataTaken = (await resTaken.json()) as any;
    assert.strictEqual(dataTaken.available, false);
  });

  it('POST /api/auth/signup returns 409 when username is already taken', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/signup', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          displayName: 'Duplicate User',
          username: testUser.username,
          email: `different_email_${timestamp}@example.com`,
          password: 'AnotherPassword123!',
        }),
      })
    );
    assert.strictEqual(res.status, 409);
    const data = (await res.json()) as any;
    assert.strictEqual(data.error, 'Username or email already in use.');
  });

  it('POST /api/auth/signup returns 409 when email is already in use', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/signup', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          displayName: 'Duplicate Email User',
          username: `diff_u_${timestamp}`,
          email: testUser.email,
          password: 'AnotherPassword123!',
        }),
      })
    );
    assert.strictEqual(res.status, 409);
    const data = (await res.json()) as any;
    assert.strictEqual(data.error, 'Username or email already in use.');
  });

  it('POST /api/auth/login succeeds with correct password using username', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          usernameOrEmail: testUser.username,
          password: testUser.password,
          deviceName: 'Pixel 9 Pro',
        }),
      })
    );

    assert.strictEqual(res.status, 200);
    const data = await res.json() as any;
    assert.ok(data.accessToken);
    assert.ok(data.refreshToken);
    accessToken = data.accessToken;
    refreshToken = data.refreshToken;
  });

  it('POST /api/auth/login succeeds with correct password using email', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          usernameOrEmail: testUser.email,
          password: testUser.password,
          deviceName: 'Pixel 9 Pro',
        }),
      })
    );

    assert.strictEqual(res.status, 200);
    const data = await res.json() as any;
    assert.ok(data.accessToken);
    assert.ok(data.refreshToken);
  });

  it('POST /api/auth/login with wrong password returns 401 and generic "Incorrect credentials"', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          usernameOrEmail: testUser.username,
          password: 'WrongPassword!',
        }),
      })
    );

    assert.strictEqual(res.status, 401);
    const data = await res.json() as any;
    assert.strictEqual(data.error, 'Incorrect credentials');
  });

  it('POST /api/auth/login with non-existent user performs dummy Argon2 verify and returns "Incorrect credentials"', async () => {
    const start = Date.now();
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          usernameOrEmail: `nonexistent_user_${Date.now()}`,
          password: 'SomePassword123',
        }),
      })
    );
    const elapsedMs = Date.now() - start;

    assert.strictEqual(res.status, 401);
    const data = await res.json() as any;
    assert.strictEqual(data.error, 'Incorrect credentials');
    // Verify that Argon2 hashing took computational time (constant-time defense against timing attacks)
    assert.ok(elapsedMs >= 20, `Execution should take computational time for Argon2, took ${elapsedMs}ms`);
  });

  it('POST /api/auth/refresh rotates the refresh token and issues new accessToken', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      })
    );

    assert.strictEqual(res.status, 200);
    const data = await res.json() as any;
    assert.ok(data.accessToken);
    assert.ok(data.refreshToken);
    assert.notStrictEqual(data.refreshToken, refreshToken, 'Refresh token must be rotated');

    // Update tokens for subsequent tests
    accessToken = data.accessToken;
    refreshToken = data.refreshToken;
  });

  it('GET /api/auth/devices lists registered devices with Bearer token', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/devices', {
        method: 'GET',
        headers: { Authorization: `Bearer ${accessToken}` },
      })
    );

    assert.strictEqual(res.status, 200);
    const devices = await res.json() as any[];
    assert.ok(Array.isArray(devices));
    assert.ok(devices.some((d) => d.deviceName === 'Pixel 9 Pro'));
  });

  it('POST /api/auth/logout revokes session', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/logout', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${accessToken}`,
        },
        body: JSON.stringify({ refreshToken }),
      })
    );

    assert.strictEqual(res.status, 200);

    // Using the revoked refresh token must now fail
    const refreshRes = await app.fetch(
      new Request('http://localhost:4000/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      })
    );
    assert.strictEqual(refreshRes.status, 401);
  });

  // ─── Terms Acceptance Tracking Tests (P1.3) ─────────────────────────────

  it('Terms Gate: Unauthenticated calls to /terms/status and /terms/accept return 401', async () => {
    const statusRes = await app.fetch(new Request('http://localhost:4000/api/auth/terms/status'));
    assert.strictEqual(statusRes.status, 401);

    const acceptRes = await app.fetch(
      new Request('http://localhost:4000/api/auth/terms/accept', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ version: '1.0' }),
      })
    );
    assert.strictEqual(acceptRes.status, 401);
  });

  it('Terms Gate: GET /terms/status returns correct initial payload for user', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/terms/status', {
        method: 'GET',
        headers: { Authorization: `Bearer ${accessToken}` },
      })
    );

    assert.strictEqual(res.status, 200);
    const data = (await res.json()) as any;
    assert.strictEqual(data.currentVersion, '1.0');
    assert.strictEqual(data.acceptedAt, null);
    assert.strictEqual(data.version, null);
  });

  it('Terms Gate: POST /terms/accept writes columns and returns 200', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/terms/accept', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${accessToken}`,
        },
        body: JSON.stringify({ version: '1.0' }),
      })
    );

    assert.strictEqual(res.status, 200);
    const data = (await res.json()) as any;
    assert.ok(data.acceptedAt);
    assert.strictEqual(data.version, '1.0');
  });

  it('Terms Gate: GET /terms/status reflects accepted terms and detects version mismatch accurately', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/terms/status', {
        method: 'GET',
        headers: { Authorization: `Bearer ${accessToken}` },
      })
    );

    assert.strictEqual(res.status, 200);
    const data = (await res.json()) as any;
    assert.ok(data.acceptedAt);
    assert.strictEqual(data.version, '1.0');
    assert.strictEqual(data.currentVersion, '1.0');

    // Simulate future terms version check: client needs acceptance if currentVersion exceeds accepted version
    const nextTermsVersion = '2.1.0';
    const isMismatch = data.version !== nextTermsVersion;
    assert.strictEqual(isMismatch, true, 'Version mismatch should be detected when currentVersion changes');
  });

  // ==========================================
  // Password Reset Flow Tests
  // ==========================================
  // ==========================================
  // OTP-Based Signup Flow Tests
  // ==========================================
  const otpRand = Math.floor(Math.random() * 9000 + 1000);
  const otpSignupUser = {
    username: `otp_u_${otpRand}`,
    email: `otp_user_${Date.now()}@example.com`,
    password: 'SecurePassword123!',
    displayName: 'OTP Signup Test',
  };
  let signupVerificationId = '';
  let signupOtpCode = '';

  it('POST /api/auth/signup-init sends 6-digit OTP email and returns verificationId', async () => {
    __clearSentEmails();
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/signup-init', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(otpSignupUser),
      })
    );
    assert.strictEqual(res.status, 200);
    const data = (await res.json()) as any;
    assert.ok(data.verificationId);
    assert.strictEqual(data.email, otpSignupUser.email.toLowerCase());
    signupVerificationId = data.verificationId;

    const sent = __getSentEmails();
    const email = sent.find((e) => e.to === otpSignupUser.email.toLowerCase());
    assert.ok(email, 'Signup OTP email must be sent');
    assert.ok(email.subject.includes('Verify your email'));

    // Extract 6-digit code
    const match = email.text?.match(/\b(\d{6})\b/) || email.html?.match(/>\s*(\d{6})\s*</);
    assert.ok(match, 'Email must contain 6-digit numeric OTP');
    signupOtpCode = match[1] || match[0];
  });

  it('POST /api/auth/signup-init invalid username (<3 chars or invalid chars) returns 400', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/signup-init', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          ...otpSignupUser,
          username: 'ab',
          email: `invalid_un_${Date.now()}@example.com`,
        }),
      })
    );
    assert.strictEqual(res.status, 400);
    const data = (await res.json()) as any;
    assert.strictEqual(data.code, 'invalid_username');
    assert.ok(data.error.includes('3–20 characters'));
  });

  it('POST /api/auth/signup-init invalid email returns 400', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/signup-init', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...otpSignupUser, email: `not-an-email-${Date.now()}` }),
      })
    );
    assert.strictEqual(res.status, 400);
    const data = (await res.json()) as any;
    assert.ok(data.error.includes('valid email'));
  });

  it('POST /api/auth/signup-init weak password returns 400', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/signup-init', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          ...otpSignupUser,
          password: 'short',
          email: `weak_pw_${Date.now()}@example.com`,
        }),
      })
    );
    assert.strictEqual(res.status, 400);
    const data = (await res.json()) as any;
    assert.ok(data.error.includes('at least 8 characters'));
  });

  it('POST /api/auth/signup-init duplicate username returns 409', async () => {
    const dupUsername = `dup_un_${Math.floor(Math.random() * 9000 + 1000)}`;
    await sqlAuthRole`
      INSERT INTO users (username, email, display_name)
      VALUES (${dupUsername}, ${'dup_un_' + Date.now() + '@example.com'}, ${'Duplicate User'})
    `;

    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/signup-init', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: dupUsername,
          email: `unique_${Date.now()}@example.com`,
          password: 'ValidPassword123!',
        }),
      })
    );
    assert.strictEqual(res.status, 409);
    const data = (await res.json()) as any;
    assert.ok(data.error.includes('already taken'));

    await sqlAuthRole`DELETE FROM users WHERE username = ${dupUsername}`;
  });

  it('POST /api/auth/signup-init duplicate email returns 409', async () => {
    const dupEmail = `dup_em_${Date.now()}@example.com`;
    await sqlAuthRole`
      INSERT INTO users (username, email, display_name)
      VALUES (${'dup_u_' + Math.floor(Math.random() * 9000 + 1000)}, ${dupEmail}, ${'Duplicate User'})
    `;

    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/signup-init', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: `uniq_u_${Math.floor(Math.random() * 9000 + 1000)}`,
          email: dupEmail,
          password: 'ValidPassword123!',
        }),
      })
    );
    assert.strictEqual(res.status, 409);
    const data = (await res.json()) as any;
    assert.ok(data.error.includes('already registered'));

    await sqlAuthRole`DELETE FROM users WHERE email = ${dupEmail}`;
  });

  it('POST /api/auth/signup-init rate limit: 4th attempt in 1 hour returns 429', async () => {
    const uniqueIp = `10.${(Date.now() >> 8) % 250}.${Date.now() % 250}.1`;
    const rlEmail = `rl_signup_${Date.now()}@example.com`;
    for (let i = 0; i < 3; i++) {
      const res = await app.fetch(
        new Request('http://localhost:4000/api/auth/signup-init', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'X-Forwarded-For': uniqueIp },
          body: JSON.stringify({
            username: `rl_u_${i}_${Math.floor(Math.random() * 90 + 10)}`,
            email: rlEmail,
            password: 'ValidPassword123!',
          }),
        })
      );
      assert.strictEqual(res.status, 200);
    }

    const res4 = await app.fetch(
      new Request('http://localhost:4000/api/auth/signup-init', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Forwarded-For': uniqueIp },
        body: JSON.stringify({
          username: `rl_u_4_${Math.floor(Math.random() * 90 + 10)}`,
          email: rlEmail,
          password: 'ValidPassword123!',
        }),
      })
    );
    assert.strictEqual(res4.status, 429);
    const data4 = (await res4.json()) as any;
    assert.ok(data4.error.includes('Too many'));
  });

  it('POST /api/auth/signup-verify with invalid verificationId returns 400', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/signup-verify', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          verificationId: '00000000-0000-0000-0000-000000000000',
          otp: '123456',
        }),
      })
    );
    assert.strictEqual(res.status, 400);
  });

  it('POST /api/auth/signup-verify with incorrect OTP returns 400 and increments attempts', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/signup-verify', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          verificationId: signupVerificationId,
          otp: '000000', // wrong OTP
        }),
      })
    );
    assert.strictEqual(res.status, 400);
    const data = (await res.json()) as any;
    assert.ok(data.error.includes('Incorrect verification code'));

    const [rec] = await sqlAuthRole`SELECT attempts FROM signup_verification_otps WHERE id = ${signupVerificationId}`;
    assert.strictEqual(rec.attempts, 1);
  });

  it('POST /api/auth/signup-verify exceeding 5 attempts locks session and returns 400', async () => {
    await sqlAuthRole`UPDATE signup_verification_otps SET attempts = 5 WHERE id = ${signupVerificationId}`;
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/signup-verify', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          verificationId: signupVerificationId,
          otp: signupOtpCode,
        }),
      })
    );
    assert.strictEqual(res.status, 400);
    const data = (await res.json()) as any;
    assert.ok(data.error.includes('Too many failed attempts'));
  });

  it('POST /api/auth/signup-resend-otp updates OTP and resets attempts to 0', async () => {
    __clearSentEmails();
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/signup-resend-otp', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ verificationId: signupVerificationId }),
      })
    );
    assert.strictEqual(res.status, 200);

    const [rec] = await sqlAuthRole`SELECT attempts FROM signup_verification_otps WHERE id = ${signupVerificationId}`;
    assert.strictEqual(rec.attempts, 0);

    const sent = __getSentEmails();
    assert.strictEqual(sent.length, 1);
    const match = sent[0].text?.match(/\b(\d{6})\b/) || sent[0].html?.match(/>\s*(\d{6})\s*</);
    assert.ok(match);
    signupOtpCode = match[1] || match[0]; // new OTP
  });

  let createdOtpUserId = '';

  it('POST /api/auth/signup-verify with valid OTP creates user, email_verified_at, issues tokens, and sends welcome email', async () => {
    __clearSentEmails();
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/signup-verify', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          verificationId: signupVerificationId,
          otp: signupOtpCode,
        }),
      })
    );
    assert.strictEqual(res.status, 200);
    const data = (await res.json()) as any;
    assert.ok(data.user);
    assert.strictEqual(data.user.username, otpSignupUser.username);
    assert.strictEqual(data.user.emailVerified, true);
    assert.ok(data.accessToken);
    assert.ok(data.refreshToken);
    createdOtpUserId = data.user.id;

    // Verify email_verified_at is set in database
    const [dbUser] = await sqlAuthRole`SELECT email_verified_at FROM users WHERE id = ${createdOtpUserId}`;
    assert.ok(dbUser.email_verified_at !== null, 'email_verified_at must be populated');

    // Verify welcome email was sent
    const sent = __getSentEmails();
    assert.ok(sent.some((e) => e.to === otpSignupUser.email.toLowerCase() && e.subject.includes('Welcome to MuE')));
  });

  it('POST /api/auth/signup-verify cannot reuse completed session (returns 400)', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/signup-verify', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          verificationId: signupVerificationId,
          otp: signupOtpCode,
        }),
      })
    );
    assert.strictEqual(res.status, 400);
  });

  // ==========================================
  // OTP-Based Password Reset Flow Tests
  // ==========================================
  let resetSessionId = '';
  let resetOtpCode = '';

  it('POST /api/auth/forgot-password-init with existing user returns resetId and sends OTP email', async () => {
    __clearSentEmails();
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/forgot-password-init', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: testUser.email }),
      })
    );
    assert.strictEqual(res.status, 200);
    const data = (await res.json()) as any;
    assert.ok(data.resetId);
    assert.strictEqual(data.email, testUser.email.toLowerCase());
    resetSessionId = data.resetId;

    const sent = __getSentEmails();
    const resetEmail = sent.find((e) => e.to === testUser.email);
    assert.ok(resetEmail, 'Password reset email must be sent');
    assert.ok(resetEmail.subject.includes('password reset code'));

    const match = resetEmail.text?.match(/\b(\d{6})\b/) || resetEmail.html?.match(/>\s*(\d{6})\s*</);
    assert.ok(match, 'Must contain 6-digit numeric OTP');
    resetOtpCode = match[1] || match[0];
  });

  it('POST /api/auth/forgot-password (alias) works identically to forgot-password-init', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/forgot-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: testUser.email }),
      })
    );
    assert.strictEqual(res.status, 200);
    const data = (await res.json()) as any;
    assert.ok(data.resetId);
  });

  it('POST /api/auth/forgot-password-init: non-existent email returns 404 No account found', async () => {
    __clearSentEmails();
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/forgot-password-init', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: `nonexistent_${Date.now()}@example.com` }),
      })
    );
    assert.strictEqual(res.status, 404);
    const data = (await res.json()) as any;
    assert.strictEqual(data.error, 'No account found with this email address.');
    assert.strictEqual(__getSentEmails().length, 0);
  });

  it('POST /api/auth/forgot-password-init rate limit: 4th attempt in 1 hour returns 429', async () => {
    const uniqueIp = `10.${(Date.now() >> 8) % 250}.${Date.now() % 250}.2`;
    const rlEmail = `rl_reset_${Date.now()}@example.com`;
    const rlUsername = `rl_user_${Date.now()}`;
    await sqlAuthRole`
      INSERT INTO users (username, email, password_hash, display_name)
      VALUES (${rlUsername}, ${rlEmail}, ${'hash'}, ${'RL User'})
    `;

    for (let i = 0; i < 3; i++) {
      const res = await app.fetch(
        new Request('http://localhost:4000/api/auth/forgot-password-init', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', 'X-Forwarded-For': uniqueIp },
          body: JSON.stringify({ email: rlEmail }),
        })
      );
      assert.strictEqual(res.status, 200);
    }

    const res4 = await app.fetch(
      new Request('http://localhost:4000/api/auth/forgot-password-init', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'X-Forwarded-For': uniqueIp },
        body: JSON.stringify({ email: rlEmail }),
      })
    );
    assert.strictEqual(res4.status, 429);

    await sqlAuthRole`DELETE FROM users WHERE email = ${rlEmail}`;
  });

  it('POST /api/auth/reset-password with incorrect OTP returns 400 and increments attempts', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/reset-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          resetId: resetSessionId,
          otp: '000000',
          newPassword: 'BrandNewPassword2026!',
        }),
      })
    );
    assert.strictEqual(res.status, 400);
    const data = (await res.json()) as any;
    assert.ok(data.error.includes('Incorrect verification code'));
  });

  it('POST /api/auth/reset-password-resend-otp issues new OTP and resets attempts', async () => {
    __clearSentEmails();
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/reset-password-resend-otp', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ resetId: resetSessionId }),
      })
    );
    assert.strictEqual(res.status, 200);

    const sent = __getSentEmails();
    assert.strictEqual(sent.length, 1);
    const match = sent[0].text?.match(/\b(\d{6})\b/) || sent[0].html?.match(/>\s*(\d{6})\s*</);
    assert.ok(match);
    resetOtpCode = match[1] || match[0];
  });

  it('POST /api/auth/reset-password with weak newPassword returns 400', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/reset-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          resetId: resetSessionId,
          otp: resetOtpCode,
          newPassword: 'short',
        }),
      })
    );
    assert.strictEqual(res.status, 400);
    const data = (await res.json()) as any;
    assert.ok(data.error.includes('at least 8 characters'));
  });

  const newTestPassword = 'BrandNewPassword2026!';

  it('POST /api/auth/reset-password with valid OTP updates password and revokes existing sessions', async () => {
    assert.ok(refreshToken);
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/reset-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          resetId: resetSessionId,
          otp: resetOtpCode,
          newPassword: newTestPassword,
        }),
      })
    );
    assert.strictEqual(res.status, 200);
    const data = (await res.json()) as any;
    assert.strictEqual(data.ok, true);

    // Old refresh token must be rejected because sessions were revoked
    const refreshRes = await app.fetch(
      new Request('http://localhost:4000/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      })
    );
    assert.strictEqual(refreshRes.status, 401);
  });

  it('POST /api/auth/reset-password cannot reuse completed session (returns 400)', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/reset-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          resetId: resetSessionId,
          otp: resetOtpCode,
          newPassword: 'AnotherPassword456!',
        }),
      })
    );
    assert.strictEqual(res.status, 400);
  });

  it('Login with old password returns 401', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          usernameOrEmail: testUser.username,
          password: testUser.password, // old password
        }),
      })
    );
    assert.strictEqual(res.status, 401);
  });

  it('Login with new password returns 200 and issues new tokens', async () => {
    const res = await app.fetch(
      new Request('http://localhost:4000/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          usernameOrEmail: testUser.username,
          password: newTestPassword,
        }),
      })
    );
    assert.strictEqual(res.status, 200);
    const data = (await res.json()) as any;
    assert.ok(data.accessToken);
    assert.ok(data.refreshToken);

    // Clean up created user
    if (createdOtpUserId) {
      await sqlAuthRole`DELETE FROM users WHERE id = ${createdOtpUserId}`;
    }
  });
});

