import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { createAuthManager } from '../server/auth.js';
import { createAiQuotaManager, classifyAiError } from '../server/aiQuota.js';
import { analyzeSnapshot } from '../server/analysis.js';
import { ASSETS } from '../server/market.js';
import { createApp } from '../server/index.js';

test('Auth blocks unauthorized Gmail accounts and verifies owner password timing-safely', async () => {
  const auth = createAuthManager({
    allowedOwnerEmail: 'iamparibaloch112@gmail.com',
    googleClientId: 'test-client.apps.googleusercontent.com',
    ownerPasswordPlain: 'TestOwnerSecret!99',
    sessionSecret: '0123456789abcdef0123456789abcdef',
    fetchFn: async (url) => {
      const token = new URL(url).searchParams.get('id_token');
      if (token === 'valid_owner') {
        return new Response(
          JSON.stringify({
            aud: 'test-client.apps.googleusercontent.com',
            email_verified: 'true',
            email: 'iamparibaloch112@gmail.com',
            name: 'Owner'
          }),
          { status: 200 }
        );
      }
      return new Response(
        JSON.stringify({
          aud: 'test-client.apps.googleusercontent.com',
          email_verified: 'true',
          email: 'stranger@gmail.com',
          name: 'Stranger'
        }),
        { status: 200 }
      );
    }
  });

  const owner = await auth.verifyGoogleCredential('valid_owner');
  assert.equal(owner.email, 'iamparibaloch112@gmail.com');

  await assert.rejects(() => auth.verifyGoogleCredential('stranger_token'), /Access denied/);

  assert.throws(() => auth.verifyOwnerPassword('stranger@gmail.com', 'TestOwnerSecret!99'), /Access denied/);
  assert.throws(() => auth.verifyOwnerPassword('iamparibaloch112@gmail.com', 'wrong_password'), /Invalid owner password/);

  const pwUser = auth.verifyOwnerPassword('iamparibaloch112@gmail.com', 'TestOwnerSecret!99');
  assert.equal(pwUser.email, 'iamparibaloch112@gmail.com');
  assert.equal(auth.getPublicAuthConfig().ownerEmailMasked, 'ia***@gmail.com');
});

test('Smart AI Quota trips zero-retry circuit breaker on HTTP 429 RESOURCE_EXHAUSTED and retries 503 with backoff', async () => {
  assert.equal(
    classifyAiError(new Error('HTTP 429 RESOURCE_EXHAUSTED GenerateRequestsPerDay')),
    'DAILY_QUOTA_EXHAUSTED'
  );
  assert.equal(classifyAiError(new Error('HTTP 503 Service Unavailable')), 'TRANSIENT_SERVER');

  const delays = [];
  let attempts = 0;
  const quotaMgr = createAiQuotaManager({
    geminiApiKey: 'valid-test-gemini-key',
    backupApiKey: '',
    delayFn: async (ms) => delays.push(ms),
    fetchFn: async () => {
      attempts += 1;
      return new Response('RESOURCE_EXHAUSTED daily quota', { status: 429 });
    }
  });

  const first = await quotaMgr.executeWithSmartQuota('Analyze EUR/USD');
  assert.equal(first.ok, false);
  assert.equal(first.dailyQuotaExhausted, true);
  assert.equal(attempts, 1);
  assert.deepEqual(delays, []);
  assert.equal(quotaMgr.getStatus().connectionStatus, 'QUOTA EXCEEDED');
  assert.ok(quotaMgr.getStatus().quotaAlert.urduTitle.includes('کوٹہ'));

  // Subsequent request must NOT hammer Gemini
  const second = await quotaMgr.executeWithSmartQuota('Analyze GBP/USD');
  assert.equal(second.ok, false);
  assert.equal(attempts, 1);

  // Exponential backoff on transient 503
  const backoffDelays = [];
  let retryCalls = 0;
  const retryMgr = createAiQuotaManager({
    geminiApiKey: 'valid-test-gemini-key',
    delayFn: async (ms) => backoffDelays.push(ms),
    fetchFn: async () => {
      retryCalls += 1;
      if (retryCalls < 3) return new Response('Unavailable', { status: 503 });
      return new Response(
        JSON.stringify({ candidates: [{ content: { parts: [{ text: 'ONLINE' }] } }] }),
        { status: 200 }
      );
    }
  });
  const okRes = await retryMgr.executeWithSmartQuota('Check');
  assert.equal(okRes.ok, true);
  assert.equal(retryCalls, 3);
  assert.deepEqual(backoffDelays, [1000, 2000]);
});

test('Technical analysis detects UP trend and WAIT on disconnected feed', () => {
  const now = Date.now();
  const upCandles = Array.from({ length: 35 }, (_, i) => ({
    timestampMs: now - (35 - i) * 60_000,
    open: 1.08 + i * 0.0002,
    high: 1.0803 + i * 0.0002,
    low: 1.0799 + i * 0.0002,
    close: 1.0802 + i * 0.0002,
    volume: 100
  }));

  const upReport = analyzeSnapshot({
    asset: { id: 'EUR_USD', symbol: 'EUR/USD', decimals: 5 },
    candles1m: upCandles,
    candles5m: upCandles,
    candles15m: upCandles,
    status: { type: 'LIVE', source: 'Yahoo', lastTickEpochMs: now }
  });
  assert.equal(upReport.quantitativeDirection, 'UP');

  const waitReport = analyzeSnapshot({
    asset: { id: 'EUR_USD', symbol: 'EUR/USD', decimals: 5 },
    candles1m: [],
    candles5m: [],
    candles15m: [],
    status: { type: 'DISCONNECTED', reason: 'Timeout' }
  });
  assert.equal(waitReport.quantitativeDirection, 'WAIT');
});

test('Express routes enforce authentication, persist session cookies, and block Quotex', async () => {
  const auth = createAuthManager({
    allowedOwnerEmail: 'iamparibaloch112@gmail.com',
    ownerPasswordPlain: 'ValidPass123!',
    sessionSecret: 'test-session-secret-0123456789abcdef'
  });

  const app = createApp({
    auth,
    marketFetcher: async () => ({
      asset: { id: 'EUR_USD', symbol: 'EUR/USD', decimals: 5 },
      currentPrice: 1.085,
      candles1m: [],
      candles5m: [],
      candles15m: [],
      status: { type: 'DISCONNECTED', reason: 'Test' },
      fetchedAtEpochMs: Date.now(),
      candleExpiryEpochMs: Date.now() + 30_000
    })
  });

  const server = http.createServer(app);
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const { port } = server.address();
  const baseUrl = `http://127.0.0.1:${port}`;

  try {
    // Unauthenticated market call must return 401
    const unauth = await fetch(`${baseUrl}/api/market/EUR_USD`);
    assert.equal(unauth.status, 401);

    // Login as owner
    const login = await fetch(`${baseUrl}/api/auth/password`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'iamparibaloch112@gmail.com', password: 'ValidPass123!' })
    });
    assert.equal(login.status, 200);
    const cookie = login.headers.get('set-cookie').split(';')[0];
    assert.ok(cookie.startsWith('mn_shah_session='));

    // Authenticated /api/auth/me stays signed in
    const me = await fetch(`${baseUrl}/api/auth/me`, { headers: { Cookie: cookie } });
    const meJson = await me.json();
    assert.equal(meJson.authenticated, true);
    assert.equal(meJson.user.email, 'iamparibaloch112@gmail.com');

    // Protected markets catalog & market endpoint work with cookie
    const marketsList = await fetch(`${baseUrl}/api/markets`, { headers: { Cookie: cookie } });
    assert.equal(marketsList.status, 200);
    const marketsJson = await marketsList.json();
    const marketIds = marketsJson.markets.map((m) => m.id);
    assert.ok(marketIds.includes('NZD_USD'));
    assert.ok(marketIds.includes('USD_NGN'));
    assert.ok(marketIds.includes('AXS_USD'));

    const market = await fetch(`${baseUrl}/api/market/EUR_USD`, { headers: { Cookie: cookie } });
    assert.equal(market.status, 200);

    // Quotex trade blocked
    const quotexRes = await fetch(`${baseUrl}/api/trades`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Cookie: cookie },
      body: JSON.stringify({
        symbol: 'EUR/USD',
        direction: 'UP',
        stakeUsd: 20,
        brokerName: 'Quotex',
        authorized: true
      })
    });
    assert.equal(quotexRes.status, 400);

    // PWA manifest, service worker, and SPA fallback return 200 (never 404)
    const manifestRes = await fetch(`${baseUrl}/manifest.webmanifest`);
    assert.equal(manifestRes.status, 200);
    const manifestJson = await manifestRes.json();
    assert.equal(manifestJson.display, 'standalone');

    const swRes = await fetch(`${baseUrl}/sw.js`);
    assert.equal(swRes.status, 200);

    const spaRes = await fetch(`${baseUrl}/market`);
    assert.equal(spaRes.status, 200);
    const spaHtml = await spaRes.text();
    assert.ok(spaHtml.includes('<title>MN SHAH AI</title>'));
  } finally {
    server.close();
  }
});

test('Market catalog includes NZD/USD (OTC), USD/NGN (OTC), and Axie Infinity (OTC)', () => {
  assert.equal(ASSETS.NZD_USD.yahooTicker, 'NZDUSD=X');
  assert.equal(ASSETS.USD_NGN.yahooTicker, 'USDNGN=X');
  assert.equal(ASSETS.AXS_USD.yahooTicker, 'AXS-USD');
});
