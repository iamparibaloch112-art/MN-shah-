function calculateEmaSeries(values, period) {
  if (!values.length) return [];
  const k = 2 / (period + 1);
  const series = [values[0]];
  for (let i = 1; i < values.length; i += 1) {
    series.push(values[i] * k + series[i - 1] * (1 - k));
  }
  return series;
}

function calculateRsi(closes, period = 14) {
  if (closes.length <= period) return 50;
  let gains = 0;
  let losses = 0;
  for (let i = 1; i <= period; i += 1) {
    const diff = closes[i] - closes[i - 1];
    if (diff >= 0) gains += diff;
    else losses += Math.abs(diff);
  }
  let avgGain = gains / period;
  let avgLoss = losses / period;
  for (let i = period + 1; i < closes.length; i += 1) {
    const diff = closes[i] - closes[i - 1];
    const gain = diff > 0 ? diff : 0;
    const loss = diff < 0 ? Math.abs(diff) : 0;
    avgGain = (avgGain * (period - 1) + gain) / period;
    avgLoss = (avgLoss * (period - 1) + loss) / period;
  }
  if (avgLoss === 0) return avgGain === 0 ? 50 : 100;
  const rs = avgGain / avgLoss;
  return 100 - 100 / (1 + rs);
}

function calculateMacd(closes) {
  if (closes.length < 30) {
    return { macdLine: 0, signalLine: 0, histogram: 0, statusLabel: 'INSUFFICIENT_DATA' };
  }
  const ema12 = calculateEmaSeries(closes, 12);
  const ema26 = calculateEmaSeries(closes, 26);
  const macdSeries = closes.map((_, i) => ema12[i] - ema26[i]);
  const signalSeries = calculateEmaSeries(macdSeries, 9);
  const macdLine = macdSeries[macdSeries.length - 1];
  const signalLine = signalSeries[signalSeries.length - 1];
  const histogram = macdLine - signalLine;
  const statusLabel =
    histogram > 0 && macdLine > signalLine
      ? 'BULLISH'
      : histogram < 0 && macdLine < signalLine
        ? 'BEARISH'
        : 'NEUTRAL';
  return { macdLine, signalLine, histogram, statusLabel };
}

function detectCandlePattern(candles) {
  if (candles.length < 2) return 'Insufficient Candles';
  const prev = candles[candles.length - 2];
  const cur = candles[candles.length - 1];
  const body = Math.abs(cur.close - cur.open);
  const range = Math.max(cur.high - cur.low, 1e-9);
  const upperWick = cur.high - Math.max(cur.open, cur.close);
  const lowerWick = Math.min(cur.open, cur.close) - cur.low;

  if (body / range < 0.12) return 'Doji Indecision';
  if (prev.close < prev.open && cur.close > cur.open && cur.open <= prev.close && cur.close >= prev.open) {
    return 'Bullish Engulfing';
  }
  if (prev.close > prev.open && cur.close < cur.open && cur.open >= prev.close && cur.close <= prev.open) {
    return 'Bearish Engulfing';
  }
  if (lowerWick > body * 2.1 && upperWick < body * 0.6) return 'Bullish Hammer';
  if (upperWick > body * 2.1 && lowerWick < body * 0.6) return 'Bearish Shooting Star';
  return cur.close >= cur.open ? 'Bullish Momentum Bar' : 'Bearish Momentum Bar';
}

export function analyzeTimeframe(timeframe, candles) {
  if (!candles || candles.length < 20) return null;
  const closes = candles.map((c) => c.close);
  const latest = closes[closes.length - 1];
  const ema9Series = calculateEmaSeries(closes, 9);
  const ema21Series = calculateEmaSeries(closes, 21);
  const ema50Series = calculateEmaSeries(closes, Math.min(50, closes.length));

  const ema9 = ema9Series[ema9Series.length - 1];
  const ema21 = ema21Series[ema21Series.length - 1];
  const ema50 = ema50Series[ema50Series.length - 1];
  const rsi14 = calculateRsi(closes, 14);
  const macd = calculateMacd(closes);

  const emaBias =
    ema9 > ema21 && latest > ema21
      ? 'BULLISH'
      : ema9 < ema21 && latest < ema21
        ? 'BEARISH'
        : 'NEUTRAL';

  const recent = candles.slice(-24);
  const nearestSupport = Math.min(...recent.map((c) => c.low));
  const nearestResistance = Math.max(...recent.map((c) => c.high));

  const mid = Math.floor(recent.length / 2);
  const firstHalfHigh = Math.max(...recent.slice(0, mid).map((c) => c.high));
  const secondHalfHigh = Math.max(...recent.slice(mid).map((c) => c.high));
  const firstHalfLow = Math.min(...recent.slice(0, mid).map((c) => c.low));
  const secondHalfLow = Math.min(...recent.slice(mid).map((c) => c.low));

  const marketStructure =
    secondHalfHigh > firstHalfHigh && secondHalfLow > firstHalfLow
      ? 'Higher Highs / Higher Lows'
      : secondHalfHigh < firstHalfHigh && secondHalfLow < firstHalfLow
        ? 'Lower Highs / Lower Lows'
        : 'Range / Consolidation';

  return {
    timeframe,
    latestClose: latest,
    rsi14,
    macd,
    ema9,
    ema21,
    ema50,
    emaBias,
    candlePattern: detectCandlePattern(candles),
    marketStructure,
    supportResistance: { nearestSupport, nearestResistance }
  };
}

export function analyzeSnapshot(snapshot) {
  const m1 = analyzeTimeframe('1M', snapshot.candles1m);
  const m5 = analyzeTimeframe('5M', snapshot.candles5m);
  const m15 = analyzeTimeframe('15M', snapshot.candles15m);
  const dec = snapshot.asset.decimals;

  if (snapshot.status.type === 'DISCONNECTED') {
    return {
      quantitativeDirection: 'WAIT',
      totalScore: 0,
      confidenceEstimate: 0,
      m1,
      m5,
      m15,
      technicalEvidence: ['Market feed is disconnected. No synthetic candles generated.'],
      insufficiencyReason: snapshot.status.reason || 'Market feed disconnected.'
    };
  }

  if (snapshot.status.type === 'DELAYED' && (snapshot.status.delaySeconds > 600 || !m1)) {
    return {
      quantitativeDirection: 'WAIT',
      totalScore: 0,
      confidenceEstimate: 20,
      m1,
      m5,
      m15,
      technicalEvidence: [`Feed delayed (${snapshot.status.delaySeconds}s). Insufficient real-time 1M candle data.`],
      insufficiencyReason: snapshot.status.reason || 'Data feed delayed beyond safe 1M threshold.'
    };
  }

  if (!m1) {
    return {
      quantitativeDirection: 'WAIT',
      totalScore: 0,
      confidenceEstimate: 15,
      m1: null,
      m5,
      m15,
      technicalEvidence: ['Fewer than 20 real 1M candles available for reliable RSI/EMA scoring.'],
      insufficiencyReason: 'Insufficient 1M candlestick history.'
    };
  }

  let score = 0;
  const evidence = [];

  if (m1.rsi14 >= 55 && m1.rsi14 <= 72) {
    score += 18;
    evidence.push(`1M RSI(14) bullish momentum at ${m1.rsi14.toFixed(1)}`);
  } else if (m1.rsi14 <= 45 && m1.rsi14 >= 28) {
    score -= 18;
    evidence.push(`1M RSI(14) bearish pressure at ${m1.rsi14.toFixed(1)}`);
  } else if (m1.rsi14 > 75) {
    score -= 10;
    evidence.push(`1M RSI(14) overbought warning (${m1.rsi14.toFixed(1)})`);
  } else if (m1.rsi14 < 25) {
    score += 10;
    evidence.push(`1M RSI(14) oversold bounce zone (${m1.rsi14.toFixed(1)})`);
  } else {
    evidence.push(`1M RSI(14) neutral at ${m1.rsi14.toFixed(1)}`);
  }

  if (m1.macd.statusLabel === 'BULLISH') {
    score += 20;
    evidence.push(`1M MACD bullish crossover (hist=${m1.macd.histogram.toFixed(dec)})`);
  } else if (m1.macd.statusLabel === 'BEARISH') {
    score -= 20;
    evidence.push(`1M MACD bearish momentum (hist=${m1.macd.histogram.toFixed(dec)})`);
  }

  if (m1.emaBias === 'BULLISH') {
    score += 22;
    evidence.push(`1M EMA9 (${m1.ema9.toFixed(dec)}) > EMA21 (${m1.ema21.toFixed(dec)})`);
  } else if (m1.emaBias === 'BEARISH') {
    score -= 22;
    evidence.push(`1M EMA9 (${m1.ema9.toFixed(dec)}) < EMA21 (${m1.ema21.toFixed(dec)})`);
  }

  if (m1.candlePattern.includes('Bullish')) score += 12;
  if (m1.candlePattern.includes('Bearish')) score -= 12;
  evidence.push(`1M Pattern: ${m1.candlePattern} • ${m1.marketStructure}`);

  if (m5) {
    if (m5.emaBias === 'BULLISH') score += 14;
    else if (m5.emaBias === 'BEARISH') score -= 14;
    evidence.push(`5M Bias: ${m5.emaBias} (RSI ${m5.rsi14.toFixed(1)}, MACD ${m5.macd.statusLabel})`);
  }

  if (m15) {
    if (m15.emaBias === 'BULLISH') score += 12;
    else if (m15.emaBias === 'BEARISH') score -= 12;
    evidence.push(`15M Structure: ${m15.emaBias} (${m15.marketStructure})`);
  }

  evidence.push(
    `1M S/R: Support ${m1.supportResistance.nearestSupport.toFixed(dec)} | Resistance ${m1.supportResistance.nearestResistance.toFixed(dec)}`
  );

  let direction = 'UNCERTAIN';
  let insufficiencyReason = null;
  if (score >= 32) direction = 'UP';
  else if (score <= -32) direction = 'DOWN';
  else if (Math.abs(score) < 16) {
    direction = 'WAIT';
    insufficiencyReason = 'Indicators conflict across 1M/5M/15M timeframes; waiting for clearer alignment.';
  } else {
    insufficiencyReason = 'Moderate directional bias with mixed confirmation.';
  }

  const confidenceEstimate =
    direction === 'WAIT' || direction === 'UNCERTAIN'
      ? Math.min(48, Math.max(20, Math.abs(score) + 18))
      : Math.min(86, Math.max(55, Math.abs(score) + 25));

  return {
    quantitativeDirection: direction,
    totalScore: score,
    confidenceEstimate,
    m1,
    m5,
    m15,
    technicalEvidence: evidence,
    insufficiencyReason
  };
}
