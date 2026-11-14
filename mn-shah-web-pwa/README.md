# MN SHAH AI — Installable Chrome PWA (React + Node.js)

Standalone mobile-first PWA version of **MN SHAH AI** prepared for **Google Cloud Run**, leaving the native Android project untouched.

## Features
- **Owner-Only Persistent Login**: Restricted to `ALLOWED_OWNER_EMAIL` (`iamparibaloch112@gmail.com`) via Google Sign-In and/or server-verified `scrypt` password + 30-day signed `HttpOnly` cookie.
- **Full-Screen Android Chrome PWA**: `manifest.webmanifest` (`display: standalone`), Service Worker (`sw.js`), custom black & neon-yellow icons, and mobile bottom navigation.
- **Live Market & Technical Engine**: Express proxy fetches real-time `EUR/USD`, `GBP/USD`, `USD/JPY`, `XAU/USD`, `NZD/USD (OTC)`, `USD/NGN (OTC)`, and `Axie Infinity (OTC)` (`1M`, `5M`, `15M`) candles and computes RSI-14, MACD, EMA 9/21/50, Support/Resistance, and candle patterns.
- **Smart AI Quota Manager**: Detects HTTP `429 RESOURCE_EXHAUSTED` with zero repeated retries, shows Urdu quota notice, retries transient 5xx errors with exponential backoff, and optionally supports a backup AI key if configured.

## Quick Cloud Run Deployment
1. In Google Cloud Console:
   - Create an OAuth 2.0 **Web application** Client ID and add your Cloud Run HTTPS URL to **Authorized JavaScript origins**.
   - Store `mn-shah-owner-password`, `mn-shah-session-secret` (`openssl rand -hex 32`), and `mn-shah-gemini-key` in **Secret Manager**.
2. From the `mn-shah-web-pwa` directory, run:
   ```bash
   PROJECT_ID=your-gcp-project-id GOOGLE_CLIENT_ID=your-web-client-id.apps.googleusercontent.com bash deploy-cloudrun.sh
   ```
3. Open the resulting `https://<service>-<hash>.a.run.app` link in **Android Chrome**, sign in once, and tap **Install App** / **Add to Home screen**.
