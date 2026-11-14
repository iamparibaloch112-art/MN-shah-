import React from 'react';

export default function SignalsPage({ signals, onDeleteSignal, onSpeakUrdu }) {
  return (
    <div className="page-content">
      <h2 className="brand-title" style={{ fontSize: 17 }}>
        SIGNAL ARCHIVE
      </h2>

      {signals.length === 0 ? (
        <div className="card" style={{ color: 'var(--text-sec)', fontSize: 13 }}>
          No signals analyzed yet. Tap ANALYZE NOW on the Command tab to generate and archive a multi-timeframe signal.
        </div>
      ) : (
        signals.map((sig) => {
          const color =
            sig.direction === 'UP'
              ? 'var(--green)'
              : sig.direction === 'DOWN'
                ? 'var(--red)'
                : 'var(--amber)';
          return (
            <div key={sig.id} className="card" style={{ borderColor: `${color}66`, display: 'flex', flexDirection: 'column', gap: 6 }}>
              <div className="row-between">
                <strong style={{ color }}>
                  {sig.symbol} • {sig.direction} ({sig.confidence}%)
                </strong>
                <span className="mono" style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                  {new Date(sig.generatedAtEpochMs).toLocaleTimeString()}
                </span>
              </div>
              <div className="urdu-text" style={{ fontSize: 15 }}>
                {sig.urduSpeech}
              </div>
              <div style={{ fontSize: 12, color: 'var(--text-sec)' }}>
                Source: {sig.dataSource} • RSI {sig.rsi1m.toFixed(1)} • MACD {sig.macdSignal}
              </div>
              <div className="row-between">
                <button type="button" className="btn-outline" onClick={() => onSpeakUrdu(sig.urduSpeech)}>
                  🔊 Replay Urdu
                </button>
                <button
                  type="button"
                  className="btn-outline"
                  style={{ color: 'var(--red)', borderColor: 'var(--red)' }}
                  onClick={() => onDeleteSignal(sig.id)}
                >
                  Delete
                </button>
              </div>
            </div>
          );
        })
      )}
    </div>
  );
}
