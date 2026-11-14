import crypto from 'node:crypto';

const SESSION_COOKIE_NAME = 'mn_shah_session';
const SESSION_TTL_SECONDS = 60 * 60 * 24 * 30; // 30 days persistent login

export function createAuthManager({
  allowedOwnerEmail = process.env.ALLOWED_OWNER_EMAIL || 'iamparibaloch112@gmail.com',
  googleClientId = process.env.GOOGLE_CLIENT_ID || '',
  ownerPasswordPlain = process.env.OWNER_PASSWORD || '',
  ownerPasswordHash = process.env.OWNER_PASSWORD_HASH || '',
  sessionSecret = process.env.SESSION_SECRET || crypto.randomBytes(32).toString('hex'),
  fetchFn = fetch
} = {}) {
  const normalizedOwnerEmail = allowedOwnerEmail.trim().toLowerCase();

  // Derive a salted scrypt hash in memory if OWNER_PASSWORD was provided in server env
  const runtimePasswordRecord = (() => {
    if (ownerPasswordHash && ownerPasswordHash.includes(':')) {
      const [saltHex, keyHex] = ownerPasswordHash.split(':');
      if (saltHex && keyHex) return { saltHex, keyHex };
    }
    const clean = ownerPasswordPlain.trim();
    if (!clean || clean === 'CHANGE_ME_SET_SECURE_SECRET') return null;
    const saltHex = crypto.randomBytes(16).toString('hex');
    const derivedHex = crypto.scryptSync(clean, saltHex, 64).toString('hex');
    return { saltHex, keyHex: derivedHex };
  })();

  const hasValidGoogleClient = Boolean(
    googleClientId && !googleClientId.startsWith('YOUR_')
  );

  // Simple in-memory rate limiter for password attempts
  const failedAttemptsByIp = new Map();

  function isRateLimited(ip) {
    const now = Date.now();
    const record = failedAttemptsByIp.get(ip);
    if (!record) return false;
    if (now > record.resetAt) {
      failedAttemptsByIp.delete(ip);
      return false;
    }
    return record.count >= 5;
  }

  function recordFailure(ip) {
    const now = Date.now();
    const record = failedAttemptsByIp.get(ip);
    if (!record || now > record.resetAt) {
      failedAttemptsByIp.set(ip, { count: 1, resetAt: now + 15 * 60 * 1000 });
    } else {
      record.count += 1;
    }
  }

  function clearFailures(ip) {
    failedAttemptsByIp.delete(ip);
  }

  function signSession(payload) {
    const encoded = Buffer.from(JSON.stringify(payload), 'utf8').toString('base64url');
    const sig = crypto
      .createHmac('sha256', sessionSecret)
      .update(encoded)
      .digest('base64url');
    return `${encoded}.${sig}`;
  }

  function verifySessionToken(token) {
    if (!token || typeof token !== 'string' || !token.includes('.')) return null;
    const [encoded, sig] = token.split('.');
    if (!encoded || !sig) return null;
    const expectedSig = crypto
      .createHmac('sha256', sessionSecret)
      .update(encoded)
      .digest('base64url');

    const sigBuf = Buffer.from(sig);
    const expBuf = Buffer.from(expectedSig);
    if (sigBuf.length !== expBuf.length || !crypto.timingSafeEqual(sigBuf, expBuf)) {
      return null;
    }

    try {
      const parsed = JSON.parse(Buffer.from(encoded, 'base64url').toString('utf8'));
      if (!parsed.exp || Date.now() > parsed.exp) return null;
      if ((parsed.email || '').toLowerCase() !== normalizedOwnerEmail) return null;
      return parsed;
    } catch {
      return null;
    }
  }

  function parseCookies(req) {
    const raw = req.headers.cookie || '';
    const out = {};
    for (const part of raw.split(';')) {
      const idx = part.indexOf('=');
      if (idx > 0) {
        const k = part.slice(0, idx).trim();
        const v = decodeURIComponent(part.slice(idx + 1).trim());
        out[k] = v;
      }
    }
    return out;
  }

  function setSessionCookie(req, res, user) {
    const exp = Date.now() + SESSION_TTL_SECONDS * 1000;
    const token = signSession({
      email: user.email,
      name: user.name || 'MN Shah Owner',
      method: user.method,
      exp
    });
    const isHttps = req.secure || req.headers['x-forwarded-proto'] === 'https';
    const parts = [
      `${SESSION_COOKIE_NAME}=${encodeURIComponent(token)}`,
      'Path=/',
      `Max-Age=${SESSION_TTL_SECONDS}`,
      'HttpOnly',
      'SameSite=Lax'
    ];
    if (isHttps) parts.push('Secure');
    res.setHeader('Set-Cookie', parts.join('; '));
  }

  function clearSessionCookie(req, res) {
    const isHttps = req.secure || req.headers['x-forwarded-proto'] === 'https';
    const parts = [
      `${SESSION_COOKIE_NAME}=`,
      'Path=/',
      'Max-Age=0',
      'HttpOnly',
      'SameSite=Lax'
    ];
    if (isHttps) parts.push('Secure');
    res.setHeader('Set-Cookie', parts.join('; '));
  }

  async function verifyGoogleCredential(idToken) {
    if (!hasValidGoogleClient) {
      throw Object.assign(new Error('Google OAuth client is not configured on the server.'), { status: 503 });
    }
    if (!idToken || typeof idToken !== 'string') {
      throw Object.assign(new Error('Missing Google ID token.'), { status: 400 });
    }
    const resp = await fetchFn(
      `https://oauth2.googleapis.com/tokeninfo?id_token=${encodeURIComponent(idToken)}`
    );
    if (!resp.ok) {
      throw Object.assign(new Error('Invalid Google ID token.'), { status: 401 });
    }
    const info = await resp.json();
    if (info.aud !== googleClientId) {
      throw Object.assign(new Error('Google token audience mismatch.'), { status: 401 });
    }
    const verified = info.email_verified === true || info.email_verified === 'true';
    const email = (info.email || '').trim().toLowerCase();
    if (!verified || email !== normalizedOwnerEmail) {
      throw Object.assign(
        new Error('Access denied: Only the authorized owner Gmail account is permitted.'),
        { status: 403 }
      );
    }
    return {
      email,
      name: info.name || 'MN Shah Owner',
      picture: info.picture || null,
      method: 'google'
    };
  }

  function verifyOwnerPassword(email, password, clientIp = 'local') {
    if (isRateLimited(clientIp)) {
      throw Object.assign(
        new Error('Too many failed login attempts. Try again in 15 minutes.'),
        { status: 429 }
      );
    }
    if (!runtimePasswordRecord) {
      throw Object.assign(
        new Error('Server owner password is not configured in environment secrets.'),
        { status: 503 }
      );
    }
    const submittedEmail = (email || '').trim().toLowerCase();
    if (submittedEmail !== normalizedOwnerEmail) {
      recordFailure(clientIp);
      throw Object.assign(
        new Error('Access denied: Only the authorized owner Gmail account is permitted.'),
        { status: 403 }
      );
    }
    if (!password || typeof password !== 'string') {
      recordFailure(clientIp);
      throw Object.assign(new Error('Password is required.'), { status: 400 });
    }

    const derived = crypto.scryptSync(password, runtimePasswordRecord.saltHex, 64);
    const expected = Buffer.from(runtimePasswordRecord.keyHex, 'hex');
    if (derived.length !== expected.length || !crypto.timingSafeEqual(derived, expected)) {
      recordFailure(clientIp);
      throw Object.assign(new Error('Invalid owner password.'), { status: 401 });
    }

    clearFailures(clientIp);
    return {
      email: normalizedOwnerEmail,
      name: 'MN Shah Owner',
      method: 'password'
    };
  }

  function getSessionUser(req) {
    const cookies = parseCookies(req);
    const token = cookies[SESSION_COOKIE_NAME];
    return verifySessionToken(token);
  }

  function requireOwnerAuth(req, res, next) {
    const user = getSessionUser(req);
    if (!user) {
      return res.status(401).json({
        error: 'Authentication required. Please sign in with the authorized owner account.'
      });
    }
    req.ownerUser = user;
    return next();
  }

  function getPublicAuthConfig() {
    return {
      googleEnabled: hasValidGoogleClient,
      googleClientId: hasValidGoogleClient ? googleClientId : null,
      passwordEnabled: Boolean(runtimePasswordRecord),
      ownerEmailMasked: maskEmail(normalizedOwnerEmail)
    };
  }

  return {
    getSessionUser,
    requireOwnerAuth,
    setSessionCookie,
    clearSessionCookie,
    verifyGoogleCredential,
    verifyOwnerPassword,
    getPublicAuthConfig
  };
}

function maskEmail(email) {
  const [local, domain] = email.split('@');
  if (!local || !domain) return '***';
  const visible = local.slice(0, Math.min(2, local.length));
  return `${visible}***@${domain}`;
}
