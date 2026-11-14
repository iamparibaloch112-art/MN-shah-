import React, { useEffect, useRef } from 'react';

function emaSeries(values, period) {
  if (!values.length) return [];
  const k = 2 / (period + 1);
  const out = [values[0]];
  for (let i = 1; i < values.length; i += 1) {
    out.push(values[i] * k + out[i - 1] * (1 - k));
  }
  return out;
}

export default function CandlestickChart({ asset, candles, feedStatus, onReconnect }) {
  const canvasRef = useRef(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas || !candles || candles.length === 0) return;

    const dpr = window.devicePixelRatio || 1;
    const width = canvas.clientWidth;
    const height = canvas.clientHeight;
    canvas.width = Math.floor(width * dpr);
    canvas.height = Math.floor(height * dpr);
    const ctx = canvas.getContext('2d');
    ctx.scale(dpr, dpr);

    ctx.clearRect(0, 0, width, height);
    const visible = candles.slice(-45);
    const padX = 10;
    const padRight = 58;
    const padY = 14;
    const chartW = Math.max(40, width - padX - padRight);
    const chartH = Math.max(40, height - padY * 2);

    let minP = Math.min(...visible.map((c) => c.low));
    let maxP = Math.max(...visible.map((c) => c.high));
    if (maxP - minP < 1e-6) {
      maxP += 0.001;
      minP -= 0.001;
    }

    const toY = (p) => padY + ((maxP - p) / (maxP - minP)) * chartH;

    // Grid lines
    ctx.strokeStyle = '#1D2534';
    ctx.lineWidth = 1;
    for (let g = 0; g <= 3; g += 1) {
      const y = padY + (chartH * g) / 3;
      ctx.beginPath();
      ctx.moveTo(padX, y);
      ctx.lineTo(width - padRight, y);
      ctx.stroke();

      const price = maxP - ((maxP - minP) * g) / 3;
      ctx.fillStyle = '#6B768B';
      ctx.font = '10px JetBrains Mono, monospace';
      ctx.fillText(price.toFixed(asset.decimals), width - padRight + 4, y + 3);
    }

    const step = chartW / visible.length;
    const bodyW = Math.max(3, step * 0.62);

    // Candles
    visible.forEach((c, idx) => {
      const xCenter = padX + idx * step + step / 2;
      const bullish = c.close >= c.open;
      const color = bullish ? '#00E676' : '#FF3B5C';

      ctx.strokeStyle = color;
      ctx.lineWidth = 1.4;
      ctx.beginPath();
      ctx.moveTo(xCenter, toY(c.high));
      ctx.lineTo(xCenter, toY(c.low));
      ctx.stroke();

      const top = toY(Math.max(c.open, c.close));
      const bot = toY(Math.min(c.open, c.close));
      const h = Math.max(2, bot - top);
      ctx.fillStyle = color;
      ctx.fillRect(xCenter - bodyW / 2, top, bodyW, h);
    });

    // EMA9 (Neon Yellow) & EMA21 (Cyan)
    const closes = visible.map((c) => c.close);
    const e9 = emaSeries(closes, 9);
    const e21 = emaSeries(closes, 21);

    const drawLine = (vals, color) => {
      ctx.strokeStyle = color;
      ctx.lineWidth = 1.5;
      ctx.beginPath();
      vals.forEach((v, i) => {
        const x = padX + i * step + step / 2;
        const y = toY(v);
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      });
      ctx.stroke();
    };
    drawLine(e9, '#EEFF00');
    drawLine(e21, '#00E5FF');
  }, [asset, candles]);

  if (!candles || candles.length === 0) {
    return (
      <div
        className="card"
        style={{
          minHeight: 220,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: 10,
          textAlign: 'center'
        }}
      >
        <div style={{ color: 'var(--amber)', fontWeight: 700, fontSize: 13 }}>
          {feedStatus?.reason || 'Waiting for live market candles...'}
        </div>
        <button type="button" className="btn-outline" onClick={onReconnect}>
          Reconnect Feed
        </button>
      </div>
    );
  }

  return (
    <div className="card" style={{ padding: 10 }}>
      <div className="row-between" style={{ marginBottom: 6, fontSize: 11 }}>
        <span style={{ color: 'var(--neon)', fontWeight: 700 }}>{asset.symbol} CANDLESTICK</span>
        <span style={{ color: 'var(--text-sec)' }}>
          <span style={{ color: '#EEFF00' }}>■ EMA9</span> · <span style={{ color: '#00E5FF' }}>■ EMA21</span>
        </span>
      </div>
      <canvas ref={canvasRef} style={{ width: '100%', height: 220, display: 'block' }} />
    </div>
  );
}
