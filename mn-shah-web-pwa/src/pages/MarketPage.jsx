import React, { useState } from 'react';
import CandlestickChart from '../components/CandlestickChart.jsx';
import { DEFAULT_MARKETS } from '../constants/markets.js';

export default function MarketPage({
  markets = DEFAULT_MARKETS,
  selectedAssetId,
  onSelectAsset,
  snapshot,
  report,
  onReconnect
}) {
  const [timeframe, setTimeframe] = useState('1M');
  const fallbackMeta = markets.find((m) => m.id === selectedAssetId);
  const asset = snapshot?.asset || fallbackMeta || { symbol: selectedAssetId.replace('_', '/'), decimals: 5 };
  const candles =
    timeframe === '1M'
      ? snapshot?.candles1m || []
      : timeframe === '5M'
        ? snapshot?.candles5m || []
        : snapshot?.candles15m || [];
  const tfInd = timeframe === '1M' ? report?.m1 : timeframe === '5M' ? report?.m5 : report?.m15;

  return (
    <div className="page-content">
      <div className="row-between">
        <h2 className="brand-title" style={{ fontSize: 17 }}>
          LIVE MARKET & CHART
        </h2>
        <button type="button" className="btn-outline" onClick={onReconnect}>
          Refresh
        </button>
      </div>

      <div className="chip-strip">
        {markets.map((a) => (
          <button
            key={a.id}
            type="button"
            className={`chip ${selectedAssetId === a.id ? 'active' : ''}`}
            onClick={() => onSelectAsset(a.id)}
          >
            {a.label || a.symbol}
          </button>
        ))}
      </div>

      <div className="row-gap">
        {['1M', '5M', '15M'].map((tf) => (
          <button
            key={tf}
            type="button"
            className={`chip ${timeframe === tf ? 'active' : ''}`}
            onClick={() => setTimeframe(tf)}
            style={{ flex: 1 }}
          >
            {tf}
          </button>
        ))}
      </div>

      <CandlestickChart
        asset={asset}
        candles={candles}
        feedStatus={snapshot?.status}
        onReconnect={onReconnect}
      />

      {tfInd && (
        <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 6, fontSize: 13 }}>
          <div style={{ color: 'var(--neon)', fontWeight: 700, fontSize: 12 }}>
            {timeframe} MULTI-INDICATOR SNAPSHOT
          </div>
          <div className="row-between">
            <span>RSI (14): {tfInd.rsi14.toFixed(1)}</span>
            <span>MACD: {tfInd.macd.statusLabel}</span>
          </div>
          <div className="row-between">
            <span>EMA9: {tfInd.ema9.toFixed(asset.decimals)}</span>
            <span>EMA21: {tfInd.ema21.toFixed(asset.decimals)}</span>
          </div>
          <div>Pattern: {tfInd.candlePattern} • {tfInd.marketStructure}</div>
          <div style={{ color: 'var(--cyan)' }}>
            Support: {tfInd.supportResistance.nearestSupport.toFixed(asset.decimals)} | Resistance:{' '}
            {tfInd.supportResistance.nearestResistance.toFixed(asset.decimals)}
          </div>
        </div>
      )}
    </div>
  );
}
