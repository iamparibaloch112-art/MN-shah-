import React, { useState } from 'react';

export default function ManualTradeModal({ asset, initialDirection, entryPrice, onClose, onConfirm }) {
  const [direction, setDirection] = useState(initialDirection === 'DOWN' ? 'DOWN' : 'UP');
  const [stakeUsd, setStakeUsd] = useState('25');
  const [brokerName, setBrokerName] = useState('OANDA v20 (Manual)');
  const [notes, setNotes] = useState('');
  const [authorized, setAuthorized] = useState(false);

  const quotexBlocked = brokerName.toLowerCase().includes('quotex');
  const validStake = Number(stakeUsd) > 0;

  function submit(e) {
    e.preventDefault();
    if (!authorized || quotexBlocked || !validStake) return;
    onConfirm({
      symbol: asset.symbol,
      direction,
      stakeUsd: Number(stakeUsd),
      brokerName,
      notes,
      authorized: true
    });
    onClose();
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <form className="modal-sheet" onClick={(e) => e.stopPropagation()} onSubmit={submit}>
        <div className="row-between">
          <strong style={{ color: 'var(--neon)' }}>MANUAL REAL-TRADE CONFIRMATION</strong>
          <button type="button" className="btn-outline" onClick={onClose}>
            Close
          </button>
        </div>

        <div style={{ fontSize: 12, color: 'var(--text-sec)' }}>
          {asset.symbol} @ {entryPrice > 0 ? entryPrice.toFixed(asset.decimals) : '---'} • Never executes
          demo or unverified trades.
        </div>

        <div className="row-gap">
          <button
            type="button"
            className={`chip ${direction === 'UP' ? 'active' : ''}`}
            onClick={() => setDirection('UP')}
            style={{ flex: 1 }}
          >
            UP (BUY)
          </button>
          <button
            type="button"
            className={`chip ${direction === 'DOWN' ? 'active' : ''}`}
            onClick={() => setDirection('DOWN')}
            style={{ flex: 1 }}
          >
            DOWN (SELL)
          </button>
        </div>

        <input
          type="number"
          step="0.01"
          min="1"
          className="input"
          value={stakeUsd}
          onChange={(e) => setStakeUsd(e.target.value)}
          placeholder="Stake USD"
        />

        <input
          type="text"
          className="input"
          value={brokerName}
          onChange={(e) => setBrokerName(e.target.value)}
          placeholder="Authorized Broker Channel"
        />

        {quotexBlocked && (
          <div style={{ color: 'var(--red)', fontSize: 12 }}>
            Quotex has no official public retail API and is blocked.
          </div>
        )}

        <input
          type="text"
          className="input"
          value={notes}
          onChange={(e) => setNotes(e.target.value)}
          placeholder="Human verification notes"
        />

        <label style={{ display: 'flex', alignItems: 'center', gap: 10, fontSize: 12, color: 'var(--text)' }}>
          <input
            type="checkbox"
            checked={authorized}
            onChange={(e) => setAuthorized(e.target.checked)}
            style={{ width: 20, height: 20 }}
          />
          I explicitly authorize logging this manual real-trade confirmation after checking the chart.
        </label>

        <button type="submit" className="btn-primary" disabled={!authorized || quotexBlocked || !validStake}>
          CONFIRM MANUAL TRADE
        </button>
      </form>
    </div>
  );
}
