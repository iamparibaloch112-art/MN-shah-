export const ASSETS = {
  EUR_USD: {
    id: 'EUR_USD',
    symbol: 'EUR/USD',
    yahooTicker: 'EURUSD=X',
    fallbackTicker: null,
    decimals: 5,
    category: 'FX Major'
  },
  GBP_USD: {
    id: 'GBP_USD',
    symbol: 'GBP/USD',
    yahooTicker: 'GBPUSD=X',
    fallbackTicker: null,
    decimals: 5,
    category: 'FX Major'
  },
  USD_JPY: {
    id: 'USD_JPY',
    symbol: 'USD/JPY',
    yahooTicker: 'USDJPY=X',
    fallbackTicker: null,
    decimals: 3,
    category: 'FX Major'
  },
  XAU_USD: {
    id: 'XAU_USD',
    symbol: 'XAU/USD',
    yahooTicker: 'GC=F',
    fallbackTicker: 'XAUUSD=X',
    decimals: 2,
    category: 'Gold Futures/Spot'
  },
  NZD_USD: {
    id: 'NZD_USD',
    symbol: 'NZD/USD (OTC)',
    yahooTicker: 'NZDUSD=X',
    fallbackTicker: null,
    decimals: 5,
    category: 'FX Live / OTC'
  },
  USD_NGN: {
    id: 'USD_NGN',
    symbol: 'USD/NGN (OTC)',
    yahooTicker: 'USDNGN=X',
    fallbackTicker: 'NGN=X',
    decimals: 2,
    category: 'FX Live / OTC'
  },
  AXS_USD: {
    id: 'AXS_USD',
    symbol: 'Axie Infinity (OTC)',
    yahooTicker: 'AXS-USD',
    fallbackTicker: null,
    decimals: 3,
    category: 'Crypto 24/7 (AXS/USD)'
  }
};

async function fetchYahooChart(symbol, interval, range) {
  const url = `https://query1.finance.yahoo.com/v8/finance/chart/${encodeURIComponent(
    symbol
  )}?interval=${interval}&range=${range}&includePrePost=true`;
  const resp = await fetch(url, {
    headers: {
      'User-Agent': 'Mozilla/5.0 (Linux; Android 14) MNShahAI-PWA/1.0',
      Accept: 'application/json'
    },
    signal: AbortSignal.timeout(10_000)
  });
  if (!resp.ok) {
    throw new Error(`Yahoo HTTP ${resp.status}`);
  }
  const json = await resp.json();
  const result = json?.chart?.result?.[0];
  if (!result) throw new Error('Yahoo chart result missing');

  const meta = result.meta || {};
  const timestamps = result.timestamp || [];
  const quote = result.indicators?.quote?.[0] || {};
  const opens = quote.open || [];
  const highs = quote.high || [];
  const lows = quote.low || [];
  const closes = quote.close || [];
  const volumes = quote.volume || [];

  const candles = [];
  for (let i = 0; i < timestamps.length; i += 1) {
    const o = opens[i];
    const h = highs[i];
    const l = lows[i];
    const c = closes[i];
    if (
      typeof o === 'number' &&
      typeof h === 'number' &&
      typeof l === 'number' &&
      typeof c === 'number' &&
      c > 0
    ) {
      candles.push({
        timestampMs: timestamps[i] * 1000,
        open: o,
        high: h,
        low: l,
        close: c,
        volume: typeof volumes[i] === 'number' ? volumes[i] : 0
      });
    }
  }

  return {
    candles: candles.slice(-90),
    regularMarketPrice: meta.regularMarketPrice ?? candles[candles.length - 1]?.close ?? 0,
    previousClose: meta.chartPreviousClose ?? meta.previousClose ?? null,
    regularMarketTimeMs: meta.regularMarketTime ? meta.regularMarketTime * 1000 : null,
    marketState: meta.marketState || 'UNKNOWN'
  };
}

async function fetchSpotFallback(asset) {
  if (asset.id === 'XAU_USD') return null;
  if (asset.id === 'AXS_USD') {
    try {
      const resp = await fetch(
        'https://api.coingecko.com/api/v3/simple/price?ids=axie-infinity&vs_currencies=usd&include_last_updated_at=true',
        { signal: AbortSignal.timeout(8_000) }
      );
      if (!resp.ok) return null;
      const json = await resp.json();
      const spot = json?.['axie-infinity']?.usd;
      if (!spot) return null;
      return {
        source: 'CoinGecko Spot (AXS/USD)',
        price: spot,
        timestampMs: (json['axie-infinity'].last_updated_at || Math.floor(Date.now() / 1000)) * 1000
      };
    } catch {
      return null;
    }
  }
  try {
    const resp = await fetch('https://open.er-api.com/v6/latest/USD', {
      signal: AbortSignal.timeout(8_000)
    });
    if (!resp.ok) return null;
    const json = await resp.json();
    const rates = json?.rates || {};
    let spot = null;
    if (asset.id === 'EUR_USD' && rates.EUR) spot = 1 / rates.EUR;
    if (asset.id === 'GBP_USD' && rates.GBP) spot = 1 / rates.GBP;
    if (asset.id === 'USD_JPY' && rates.JPY) spot = rates.JPY;
    if (asset.id === 'NZD_USD' && rates.NZD) spot = 1 / rates.NZD;
    if (asset.id === 'USD_NGN' && rates.NGN) spot = rates.NGN;
    if (!spot) return null;
    return {
      source: 'Open Exchange Rates (Spot Only)',
      price: spot,
      timestampMs: (json.time_last_update_unix || Math.floor(Date.now() / 1000)) * 1000
    };
  } catch {
    return null;
  }
}

export async function fetchMultiTimeframeSnapshot(assetId) {
  const asset = ASSETS[assetId] || ASSETS.EUR_USD;
  const now = Date.now();
  const candleExpiryEpochMs = (Math.floor(now / 60_000) + 1) * 60_000;

  try {
    const [m1Primary, m5, m15] = await Promise.all([
      fetchYahooChart(asset.yahooTicker, '1m', '1d'),
      fetchYahooChart(asset.yahooTicker, '5m', '5d'),
      fetchYahooChart(asset.yahooTicker, '15m', '5d')
    ]);

    let m1 = m1Primary;
    let source = `Yahoo Finance (${asset.yahooTicker})`;
    if (m1.candles.length < 15 && asset.fallbackTicker) {
      const fb = await fetchYahooChart(asset.fallbackTicker, '1m', '1d').catch(() => null);
      if (fb && fb.candles.length > m1.candles.length) {
        m1 = fb;
        source = `Yahoo Finance (${asset.fallbackTicker})`;
      }
    }

    const lastTick = m1.candles[m1.candles.length - 1]?.timestampMs || m1.regularMarketTimeMs || now;
    const delaySeconds = Math.max(0, Math.floor((now - lastTick) / 1000));
    const status =
      delaySeconds > 300
        ? {
            type: 'DELAYED',
            source,
            marketState: m1.marketState,
            delaySeconds,
            reason: `Latest candle is ${delaySeconds}s old`
          }
        : {
            type: 'LIVE',
            source,
            marketState: m1.marketState,
            lastTickEpochMs: lastTick
          };

    return {
      asset,
      currentPrice: m1.regularMarketPrice || m1.candles[m1.candles.length - 1]?.close || 0,
      previousClose: m1.previousClose,
      candles1m: m1.candles,
      candles5m: m5.candles,
      candles15m: m15.candles,
      status,
      fetchedAtEpochMs: now,
      candleExpiryEpochMs
    };
  } catch (primaryErr) {
    const spotFallback = await fetchSpotFallback(asset);
    if (spotFallback) {
      const delaySeconds = Math.max(0, Math.floor((now - spotFallback.timestampMs) / 1000));
      return {
        asset,
        currentPrice: spotFallback.price,
        previousClose: null,
        candles1m: [],
        candles5m: [],
        candles15m: [],
        status: {
          type: 'DELAYED',
          source: spotFallback.source || 'Spot Fallback',
          marketState: 'SPOT_FALLBACK',
          delaySeconds,
          reason: `Yahoo feed unavailable (${primaryErr.message}); candle history missing`
        },
        fetchedAtEpochMs: now,
        candleExpiryEpochMs
      };
    }

    return {
      asset,
      currentPrice: 0,
      previousClose: null,
      candles1m: [],
      candles5m: [],
      candles15m: [],
      status: {
        type: 'DISCONNECTED',
        reason: primaryErr.message || 'Live market feed unreachable',
        lastAttemptEpochMs: now
      },
      fetchedAtEpochMs: now,
      candleExpiryEpochMs
    };
  }
}
