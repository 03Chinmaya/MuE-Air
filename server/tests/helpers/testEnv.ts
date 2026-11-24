process.env.NODE_ENV = 'test';
process.env.SOMU_TEST_MODE = '1';
process.env.JWT_SECRET = process.env.JWT_SECRET || 'test-jwt-secret-min-32-characters-long!';
if (!process.env.INTEGRATION_TOKEN_KEY || process.env.INTEGRATION_TOKEN_KEY.length !== 64) {
  process.env.INTEGRATION_TOKEN_KEY = '0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef';
}

if (process.env.NODE_ENV !== 'test' || process.env.SOMU_TEST_MODE !== '1') {
  throw new Error('testEnv.ts failed to set test env vars — check --import order');
}
