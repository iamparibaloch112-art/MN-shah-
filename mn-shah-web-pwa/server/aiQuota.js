export function classifyAiError(err) {
  const text = `${err?.name || ''} ${err?.message || ''} ${err?.status || ''}`.toLowerCase();
  const hasResourceExhausted = text.includes('resource_exhausted') || text.includes('resource exhausted');
  const hasDailyQuota =
    text.includes('daily') ||
    text.includes('perday') ||
    text.includes('per_day') ||
    text.includes('generaterequestsperday') ||
    text.includes('quota exceeded') ||
    text.includes('quota exhausted') ||
    text.includes('exceeded your current quota') ||
    text.includes('limit: 0');
  const hasRpmCue = text.includes('per minute') || text.includes('rpm') || text.includes('tpm');

  if (hasDailyQuota || (hasResourceExhausted && !hasRpmCue)) {
    return 'DAILY_QUOTA_EXHAUSTED';
  }
  if (text.includes('429') || text.includes('rate limit') || text.includes('too many requests') || hasResourceExhausted) {
    return 'TEMPORARY_RATE_LIMIT';
  }
  if (text.includes('enotfound') || text.includes('econnrefused') || text.includes('fetch failed') || text.includes('network')) {
    return 'OFFLINE_NETWORK';
  }
  if (
    text.includes('500') ||
    text.includes('502') ||
    text.includes('503') ||
    text.includes('504') ||
    text.includes('timeout') ||
    text.includes('unavailable') ||
    text.includes('overloaded')
  ) {
    return 'TRANSIENT_SERVER';
  }
  if (text.includes('401') || text.includes('403') || text.includes('api key')) {
    return 'AUTH_OR_CONFIG';
  }
  return 'UNKNOWN';
}

function nextUtcMidnightMs(nowMs) {
  const d = new Date(nowMs);
  return Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate() + 1, 0, 0, 0, 0);
}

function utcDateStr(nowMs) {
  return new Date(nowMs).toISOString().slice(0, 10);
}

export function createAiQuotaManager({
  geminiApiKey = process.env.GEMINI_API_KEY || '',
  geminiModel = process.env.GEMINI_MODEL || 'gemini-2.5-flash',
  backupApiKey = process.env.BACKUP_AI_API_KEY || '',
  backupBaseUrl = process.env.BACKUP_AI_BASE_URL || 'https://api.openai.com/v1/chat/completions',
  backupModel = process.env.BACKUP_AI_MODEL || 'gpt-4o-mini',
  backupDailyLimit = Number(process.env.BACKUP_AI_DAILY_LIMIT || 50),
  clockMs = () => Date.now(),
  delayFn = (ms) => new Promise((r) => setTimeout(r, ms)),
  fetchFn = fetch
} = {}) {
  const isGeminiConfigured = Boolean(geminiApiKey && !geminiApiKey.startsWith('YOUR_'));
  const isBackupConfigured = Boolean(
    backupApiKey &&
      !backupApiKey.startsWith('YOUR_') &&
      !backupApiKey.toUpperCase().includes('PLACEHOLDER') &&
      backupApiKey.trim().length >= 12 &&
      backupBaseUrl.startsWith('https://')
  );

  let state = {
    dateUtc: utcDateStr(clockMs()),
    connectionStatus: isGeminiConfigured ? 'ONLINE' : 'OFFLINE',
    requestsToday: 0,
    successfulResponsesToday: 0,
    temporaryRetriesToday: 0,
    quotaErrorsToday: 0,
    backupCallsToday: 0,
    backupQuotaLimit: Number.isFinite(backupDailyLimit) && backupDailyLimit > 0 ? backupDailyLimit : 50,
    backupConfigured: isBackupConfigured,
    backupQuotaExhaustedToday: false,
    cooldownUntilEpochMs: 0,
    activeProvider: isGeminiConfigured ? 'Gemini API' : 'Quantitative Engine',
    lastErrorSummary: isGeminiConfigured ? null : 'GEMINI_API_KEY not configured on server',
    quotaAlert: null
  };

  function rolloverIfNeeded(now) {
    const today = utcDateStr(now);
    if (state.dateUtc !== today) {
      state = {
        ...state,
        dateUtc: today,
        connectionStatus: isGeminiConfigured ? 'ONLINE' : 'OFFLINE',
        requestsToday: 0,
        successfulResponsesToday: 0,
        temporaryRetriesToday: 0,
        quotaErrorsToday: 0,
        backupCallsToday: 0,
        backupQuotaExhaustedToday: false,
        cooldownUntilEpochMs: 0,
        activeProvider: isGeminiConfigured ? 'Gemini API' : 'Quantitative Engine',
        lastErrorSummary: null,
        quotaAlert: null
      };
    }
  }

  function buildQuotaAlert(now, codeLabel = 'HTTP 429 RESOURCE_EXHAUSTED') {
    const diffMs = Math.max(0, state.cooldownUntilEpochMs - now);
    const totalMin = Math.max(1, Math.floor(diffMs / 60_000));
    const hrs = Math.floor(totalMin / 60);
    const mins = totalMin % 60;
    const remaining = diffMs <= 0 ? 'Reset Ready' : hrs > 0 ? `${hrs}h ${mins}m` : `${mins}m`;
    return {
      urduTitle: 'روزانہ اے آئی کوٹہ مکمل ہو چکا ہے',
      urduMessage:
        'جیمنائی اے آئی کا یومیہ کوٹہ عارضی طور پر ختم ہو گیا ہے۔ سسٹم خودکار دوبارہ کوشش نہیں کرے گا تاکہ اضافی ناکام کالز نہ ہوں۔ جب تک کوٹہ بحال نہ ہو، ایم این شاہ کے لائیو 1M، 5M اور 15M ٹیکنیکل انڈیکیٹرز بدستور کام کر رہے ہیں۔',
      romanUrduMessage:
        'Gemini AI ka rozana quota mukammal ho gaya hai. Auto-retry band hai; live 1M/5M/15M technical signals aur Urdu voice jaari hain.',
      cooldownRemainingText: remaining,
      errorCodeLabel: codeLabel,
      backupProviderNote: state.backupConfigured
        ? 'Backup AI quota checked.'
        : 'Backup AI auto-disabled (Primary Gemini + Live Indicators active).'
    };
  }

  async function callGemini(prompt, expectJson) {
    if (!isGeminiConfigured) {
      throw Object.assign(new Error('401 GEMINI_API_KEY missing on server'), { status: 401 });
    }
    const url = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(
      geminiModel
    )}:generateContent?key=${encodeURIComponent(geminiApiKey)}`;
    const body = {
      contents: [{ role: 'user', parts: [{ text: prompt }] }],
      generationConfig: {
        temperature: expectJson ? 0.25 : 0.35,
        ...(expectJson ? { responseMimeType: 'application/json' } : {})
      }
    };
    const resp = await fetchFn(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(35_000)
    });
    const raw = await resp.text();
    if (!resp.ok) {
      throw Object.assign(new Error(`HTTP ${resp.status} ${raw.slice(0, 260)}`), { status: resp.status });
    }
    const parsed = JSON.parse(raw);
    const text = parsed?.candidates?.[0]?.content?.parts?.[0]?.text || '';
    if (!text.trim()) throw new Error('Gemini returned empty text');
    return text.trim();
  }

  async function callBackup(prompt, expectJson) {
    const resp = await fetchFn(backupBaseUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${backupApiKey.trim()}`
      },
      body: JSON.stringify({
        model: backupModel,
        temperature: expectJson ? 0.25 : 0.35,
        ...(expectJson ? { response_format: { type: 'json_object' } } : {}),
        messages: [{ role: 'user', content: prompt }]
      }),
      signal: AbortSignal.timeout(30_000)
    });
    const raw = await resp.text();
    if (!resp.ok) {
      throw Object.assign(new Error(`Backup HTTP ${resp.status} ${raw.slice(0, 200)}`), { status: resp.status });
    }
    const parsed = JSON.parse(raw);
    const text = parsed?.choices?.[0]?.message?.content || '';
    if (!text.trim()) throw new Error('Backup AI returned empty text');
    return text.trim();
  }

  async function executeWithSmartQuota(prompt, { expectJson = false } = {}) {
    const now = clockMs();
    rolloverIfNeeded(now);

    if (state.cooldownUntilEpochMs > now) {
      if (isBackupConfigured && !state.backupQuotaExhaustedToday && state.backupCallsToday < state.backupQuotaLimit) {
        try {
          const text = await callBackup(prompt, expectJson);
          state.requestsToday += 1;
          state.successfulResponsesToday += 1;
          state.backupCallsToday += 1;
          state.connectionStatus = 'ONLINE';
          state.activeProvider = 'Backup AI Provider';
          state.quotaAlert = null;
          return { ok: true, text, provider: 'Backup AI Provider' };
        } catch {
          state.backupQuotaExhaustedToday = true;
        }
      }
      state.connectionStatus = 'QUOTA EXCEEDED';
      state.activeProvider = 'Quantitative Engine';
      state.quotaAlert = buildQuotaAlert(now, state.lastErrorSummary || 'HTTP 429 RESOURCE_EXHAUSTED');
      return { ok: false, dailyQuotaExhausted: true, alert: state.quotaAlert };
    }

    state.requestsToday += 1;
    let lastErr = null;
    for (let attempt = 1; attempt <= 3; attempt += 1) {
      try {
        const text = await callGemini(prompt, expectJson);
        state.successfulResponsesToday += 1;
        state.cooldownUntilEpochMs = 0;
        state.connectionStatus = 'ONLINE';
        state.activeProvider = 'Gemini API';
        state.lastErrorSummary = null;
        state.quotaAlert = null;
        return { ok: true, text, provider: 'Gemini API' };
      } catch (err) {
        lastErr = err;
        const kind = classifyAiError(err);
        if (kind === 'DAILY_QUOTA_EXHAUSTED') {
          const nowQ = clockMs();
          state.quotaErrorsToday += 1;
          state.cooldownUntilEpochMs = nextUtcMidnightMs(nowQ);
          state.lastErrorSummary = 'HTTP 429 RESOURCE_EXHAUSTED';
          if (isBackupConfigured && !state.backupQuotaExhaustedToday && state.backupCallsToday < state.backupQuotaLimit) {
            try {
              const bText = await callBackup(prompt, expectJson);
              state.successfulResponsesToday += 1;
              state.backupCallsToday += 1;
              state.connectionStatus = 'ONLINE';
              state.activeProvider = 'Backup AI Provider';
              state.quotaAlert = null;
              return { ok: true, text: bText, provider: 'Backup AI Provider' };
            } catch {
              state.backupQuotaExhaustedToday = true;
            }
          }
          state.connectionStatus = 'QUOTA EXCEEDED';
          state.activeProvider = 'Quantitative Engine';
          state.quotaAlert = buildQuotaAlert(nowQ, 'HTTP 429 RESOURCE_EXHAUSTED');
          return { ok: false, dailyQuotaExhausted: true, alert: state.quotaAlert };
        }

        const retryable = kind === 'TEMPORARY_RATE_LIMIT' || kind === 'TRANSIENT_SERVER';
        if (!retryable || attempt >= 3) break;
        state.temporaryRetriesToday += 1;
        state.lastErrorSummary = `Retry ${attempt}/2 (${kind})`;
        await delayFn(1000 * 2 ** (attempt - 1));
      }
    }

    const finalKind = classifyAiError(lastErr);
    state.activeProvider = 'Quantitative Engine';
    state.lastErrorSummary = lastErr?.message?.slice(0, 80) || 'AI Unavailable';
    state.connectionStatus = finalKind === 'TEMPORARY_RATE_LIMIT' ? 'QUOTA EXCEEDED' : 'OFFLINE';
    if (finalKind === 'TEMPORARY_RATE_LIMIT') {
      state.quotaAlert = buildQuotaAlert(clockMs(), 'HTTP 429 Rate Limit');
    }
    return { ok: false, dailyQuotaExhausted: false, error: state.lastErrorSummary, alert: state.quotaAlert };
  }

  async function recheckConnection() {
    state.cooldownUntilEpochMs = 0;
    state.quotaAlert = null;
    return executeWithSmartQuota('Reply with exactly: ONLINE', { expectJson: false });
  }

  function getStatus() {
    rolloverIfNeeded(clockMs());
    return { ...state };
  }

  return { executeWithSmartQuota, recheckConnection, getStatus };
}
