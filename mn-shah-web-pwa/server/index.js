import express from 'express';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createAuthManager } from './auth.js';
import { ASSETS, fetchMultiTimeframeSnapshot } from './market.js';
import { analyzeSnapshot } from './analysis.js';
import { createAiQuotaManager } from './aiQuota.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const distDir = path.resolve(__dirname, '..', 'dist');

export function createApp(options = {}) {
  const app = express();
  app.set('trust proxy', 1);
  app.use(express.json({ limit: '1mb' }));

  const auth = options.auth || createAuthManager();
  const aiQuota = options.aiQuota || createAiQuotaManager();
  const marketFetcher = options.marketFetcher || fetchMultiTimeframeSnapshot;

  // In-memory owner ledger (mirrors browser IndexedDB/localStorage across active container)
  const signalHistory = [];
  const manualTrades = [];

  app.get('/api/health', (_req, res) => {
    res.json({ ok: true, service: 'mn-shah-ai-pwa', timestamp: Date.now() });
  });

  app.get('/api/auth/config', (_req, res) => {
    res.json(auth.getPublicAuthConfig());
  });

  app.get('/api/auth/me', (req, res) => {
    const user = auth.getSessionUser(req);
    if (!user) return res.status(200).json({ authenticated: false, user: null });
    return res.status(200).json({ authenticated: true, user });
  });

  app.post('/api/auth/google', async (req, res) => {
    try {
      const user = await auth.verifyGoogleCredential(req.body?.idToken);
      auth.setSessionCookie(req, res, user);
      return res.json({ authenticated: true, user });
    } catch (err) {
      return res.status(err.status || 401).json({ error: err.message });
    }
  });

  app.post('/api/auth/password', (req, res) => {
    try {
      const ip = req.ip || req.socket?.remoteAddress || 'local';
      const user = auth.verifyOwnerPassword(req.body?.email, req.body?.password, ip);
      auth.setSessionCookie(req, res, user);
      return res.json({ authenticated: true, user });
    } catch (err) {
      return res.status(err.status || 401).json({ error: err.message });
    }
  });

  app.post('/api/auth/logout', (req, res) => {
    auth.clearSessionCookie(req, res);
    res.json({ ok: true });
  });

  // Protected Owner-Only Routes
  app.get('/api/markets', auth.requireOwnerAuth, (_req, res) => {
    const markets = Object.values(ASSETS).map((asset) => ({
      ...asset,
      label: asset.symbol
    }));
    res.json({ markets });
  });

  app.get('/api/market/:assetId', auth.requireOwnerAuth, async (req, res) => {
    const snapshot = await marketFetcher(req.params.assetId);
    const report = analyzeSnapshot(snapshot);
    res.json({ snapshot, report });
  });

  app.get('/api/ai/status', auth.requireOwnerAuth, (_req, res) => {
    res.json(aiQuota.getStatus());
  });

  app.post('/api/ai/recheck', auth.requireOwnerAuth, async (_req, res) => {
    const result = await aiQuota.recheckConnection();
    res.json({ ok: result.ok, status: aiQuota.getStatus() });
  });

  app.post('/api/analyze', auth.requireOwnerAuth, async (req, res) => {
    const assetId = req.body?.assetId || 'EUR_USD';
    const userCommand = req.body?.userCommand || 'MN SHAH mujhe signal do';
    const snapshot = await marketFetcher(assetId);
    const report = analyzeSnapshot(snapshot);

    const dec = snapshot.asset.decimals;
    const prompt = [
      'You are MN SHAH, an autonomous futuristic male Urdu-speaking AI trading robot.',
      'Return valid JSON ONLY with keys: direction ("UP","DOWN","UNCERTAIN","WAIT"), confidence (0..90), urduSpeech (Urdu script), romanUrduSummary, evidencePoints (array), riskNote.',
      'NEVER fabricate prices or promise guaranteed accuracy. Keep WAIT/UNCERTAIN if data is delayed/disconnected or conflicting.',
      `User command: ${userCommand}`,
      `Asset: ${snapshot.asset.symbol}, Price: ${snapshot.currentPrice.toFixed(dec)}, Feed: ${snapshot.status.type}`,
      `Quant Assessment: ${report.quantitativeDirection} (score=${report.totalScore}, conf=${report.confidenceEstimate}%)`,
      `Evidence: ${report.technicalEvidence.join(' | ')}`
    ].join('\n');

    const aiRes = await aiQuota.executeWithSmartQuota(prompt, { expectJson: true });
    let briefing = null;
    if (aiRes.ok) {
      try {
        const cleaned = aiRes.text.replace(/^```json\s*/i, '').replace(/```$/i, '').trim();
        const parsed = JSON.parse(cleaned);
        let dir = ['UP', 'DOWN', 'UNCERTAIN', 'WAIT'].includes((parsed.direction || '').toUpperCase())
          ? parsed.direction.toUpperCase()
          : report.quantitativeDirection;
        if (report.quantitativeDirection === 'WAIT' && snapshot.status.type !== 'LIVE') {
          dir = 'WAIT';
        }
        briefing = {
          direction: dir,
          confidence:
            dir === 'WAIT' || dir === 'UNCERTAIN'
              ? Math.min(49, Number(parsed.confidence || report.confidenceEstimate))
              : Math.min(90, Math.max(0, Number(parsed.confidence || report.confidenceEstimate))),
          urduSpeech: parsed.urduSpeech || buildFallbackUrdu(snapshot.asset.symbol, dir, false),
          romanUrduSummary:
            parsed.romanUrduSummary || report.insufficiencyReason || 'Multi-timeframe analysis complete.',
          evidencePoints: Array.isArray(parsed.evidencePoints) && parsed.evidencePoints.length
            ? parsed.evidencePoints
            : report.technicalEvidence,
          riskNote: parsed.riskNote || 'Quantitative + AI estimate only. No guaranteed accuracy.',
          aiBacked: true,
          providerLabel: aiRes.provider
        };
      } catch {
        briefing = null;
      }
    }

    if (!briefing) {
      briefing = {
        direction: report.quantitativeDirection,
        confidence: report.confidenceEstimate,
        urduSpeech: buildFallbackUrdu(
          snapshot.asset.symbol,
          report.quantitativeDirection,
          Boolean(aiRes.dailyQuotaExhausted)
        ),
        romanUrduSummary: aiRes.dailyQuotaExhausted
          ? `Daily AI quota mukammal hai. 1M/5M/15M indicators ke mutabiq signal ${report.quantitativeDirection} hai.`
          : report.insufficiencyReason || 'Quantitative 1M/5M/15M indicators computed.',
        evidencePoints: report.technicalEvidence,
        riskNote: 'AI commentary unavailable. Quantitative indicator reading only; never guaranteed.',
        aiBacked: false,
        providerLabel: 'Quantitative Engine'
      };
    }

    const signal = {
      id: `sig_${Date.now()}`,
      symbol: snapshot.asset.symbol,
      assetId: snapshot.asset.id,
      entryPrice: snapshot.currentPrice,
      generatedAtEpochMs: Date.now(),
      candleExpiryEpochMs: snapshot.candleExpiryEpochMs,
      dataSource: snapshot.status.source || snapshot.status.type,
      rsi1m: report.m1?.rsi14 ?? 50,
      macdSignal: report.m1?.macd?.statusLabel ?? 'N/A',
      emaTrend: report.m1?.emaBias ?? 'NEUTRAL',
      ...briefing
    };

    if (snapshot.currentPrice > 0) {
      signalHistory.unshift(signal);
      if (signalHistory.length > 50) signalHistory.pop();
    }

    res.json({
      snapshot,
      report,
      signal,
      aiStatus: aiQuota.getStatus()
    });
  });

  app.post('/api/assistant', auth.requireOwnerAuth, async (req, res) => {
    const query = (req.body?.query || '').trim();
    const assetId = req.body?.assetId || 'EUR_USD';
    if (!query) return res.status(400).json({ error: 'Query is required.' });

    const snapshot = await marketFetcher(assetId);
    const report = analyzeSnapshot(snapshot);
    const prompt = [
      'You are MN SHAH, a futuristic Urdu-speaking AI trading robot.',
      'Answer concisely using ONLY these live measurements. Return TWO lines separated by || : Line 1 Urdu script for TTS, Line 2 Roman Urdu summary.',
      `Query: ${query}`,
      `Asset: ${snapshot.asset.symbol}, Price: ${snapshot.currentPrice}, Direction: ${report.quantitativeDirection}, Evidence: ${report.technicalEvidence.join(' | ')}`
    ].join('\n');

    const aiRes = await aiQuota.executeWithSmartQuota(prompt, { expectJson: false });
    if (aiRes.ok) {
      const [urdu, roman] = aiRes.text.split('||').map((s) => s.trim());
      return res.json({
        urduSpeech: urdu || aiRes.text,
        romanSummary: roman || urdu || aiRes.text,
        aiStatus: aiQuota.getStatus()
      });
    }
    return res.json({
      urduSpeech:
        aiRes.alert?.urduMessage ||
        buildFallbackUrdu(snapshot.asset.symbol, report.quantitativeDirection, Boolean(aiRes.dailyQuotaExhausted)),
      romanSummary: aiRes.alert?.romanUrduMessage || aiRes.error || 'Quantitative fallback mode active.',
      aiStatus: aiQuota.getStatus()
    });
  });

  app.get('/api/signals', auth.requireOwnerAuth, (_req, res) => {
    res.json({ signals: signalHistory });
  });

  app.delete('/api/signals/:id', auth.requireOwnerAuth, (req, res) => {
    const idx = signalHistory.findIndex((s) => s.id === req.params.id);
    if (idx >= 0) signalHistory.splice(idx, 1);
    res.json({ ok: true });
  });

  app.get('/api/trades', auth.requireOwnerAuth, (_req, res) => {
    res.json({ trades: manualTrades });
  });

  app.post('/api/trades', auth.requireOwnerAuth, (req, res) => {
    const { symbol, direction, stakeUsd, brokerName, notes, authorized } = req.body || {};
    if (!authorized) {
      return res.status(400).json({ error: 'Explicit human authorization is required.' });
    }
    if ((brokerName || '').toLowerCase().includes('quotex')) {
      return res.status(400).json({ error: 'Quotex has no official public API and is blocked.' });
    }
    const trade = {
      id: `tr_${Date.now()}`,
      symbol: symbol || 'EUR/USD',
      direction: direction || 'UP',
      stakeUsd: Number(stakeUsd) || 10,
      brokerName: brokerName || 'OANDA v20 (Manual)',
      notes: notes || '',
      status: 'MANUALLY_CONFIRMED',
      createdAtEpochMs: Date.now()
    };
    manualTrades.unshift(trade);
    res.status(201).json({ trade });
  });

  app.post('/api/trades/:id/cancel', auth.requireOwnerAuth, (req, res) => {
    const found = manualTrades.find((t) => t.id === req.params.id);
    if (found) found.status = 'CANCELLED';
    res.json({ ok: true });
  });

  // Unknown API routes return clean JSON 404
  app.use('/api', (_req, res) => {
    res.status(404).json({ error: 'API endpoint not found' });
  });

  // Serve built React PWA static files cleanly in production
  if (fs.existsSync(distDir)) {
    app.get('/manifest.webmanifest', (_req, res) => {
      res.type('application/manifest+json');
      res.sendFile(path.join(distDir, 'manifest.webmanifest'));
    });

    app.get('/sw.js', (_req, res) => {
      res.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate');
      res.type('application/javascript');
      res.sendFile(path.join(distDir, 'sw.js'));
    });

    app.use(express.static(distDir, { maxAge: '1h', index: false }));

    // SPA fallback prevents HTTP 404 when opening or refreshing any frontend route in Chrome
    app.get('*', (_req, res) => {
      res.sendFile(path.join(distDir, 'index.html'));
    });
  }

  return app;
}

function buildFallbackUrdu(symbol, direction, quotaExhausted) {
  const core =
    direction === 'UP'
      ? `${symbol} پر اگلی ایک منٹ کی کینڈل میں اوپر جانے کا امکان ہے، شواہد کی تصدیق کریں۔`
      : direction === 'DOWN'
        ? `${symbol} پر اگلی ایک منٹ کی کینڈل میں نیچے دباؤ نظر آ رہا ہے، احتیاط سے جائزہ لیں۔`
        : direction === 'UNCERTAIN'
          ? `${symbol} کی صورتحال غیر واضح ہے، فی الحال انتظار کرنا بہتر ہے۔`
          : `${symbol} پر شواہد ناکافی ہیں یا ڈیٹا تاخیر کا شکار ہے، ابھی انتظار کریں۔`;
  return quotaExhausted ? `اے آئی کا یومیہ کوٹہ مکمل ہے، لیکن لائیو انڈیکیٹرز کے مطابق ${core}` : core;
}

// Start server when run directly (e.g. Cloud Run or `npm start`)
const isDirectRun = process.argv[1] && path.resolve(process.argv[1]) === __filename;
if (isDirectRun) {
  const port = Number(process.env.PORT || 8080);
  const app = createApp();
  app.listen(port, '0.0.0.0', () => {
    console.log(`MN SHAH AI PWA listening on http://0.0.0.0:${port}`);
  });
}
