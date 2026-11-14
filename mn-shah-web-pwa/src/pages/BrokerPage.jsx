import React, { useState } from 'react';
import ManualTradeModal from '../components/ManualTradeModal.jsx';

export default function BrokerPage({
  user,
  snapshot,
  selectedAssetId,
  trades,
  onConfirmTrade,
  onCancelTrade,
  onLogout
}) {
  const [showModal, setShowModal] = useState(false);
  const asset = snapshot?.asset || { symbol: selectedAssetId.replace('_', '/'), decimals: 5 };

  return (
    <div className="page-content">
      <h2 className="brand-title" style={{ fontSize: 17 }}>
        REAL TRADING & OWNER SESSION
      </h2>

      {/* Owner Account & Persistent Logout Button */}
      <div className="card row-between">
        <div>
          <div style={{ fontWeight: 700, fontSize: 14 }}>{user?.name || 'Authorized Owner'}</div>
          <div style={{ fontSize: 12, color: 'var(--cyan)' }}>{user?.email}</div>
          <div style={{ fontSize: 11, color: 'var(--green)' }}>Persistent One-Time Login Active</div>
        </div>
        <div className="row-gap">
          <a
            className="btn-outline"
            href={`https://wa.me/?text=${encodeURIComponent(`MN SHAH AI App: ${window.location.origin}`)}`}
            target="_blank"
            rel="noopener noreferrer"
            style={{ textDecoration: 'none', display: 'inline-flex', alignItems: 'center' }}
          >
            Share
          </a>
          <button
            type="button"
            className="btn-outline"
            style={{ color: 'var(--red)', borderColor: 'var(--red)' }}
            onClick={onLogout}
          >
            Log Out
          </button>
        </div>
      </div>

      {/* Compliance Notice */}
      <div className="panel" style={{ borderColor: 'var(--amber)', fontSize: 13, lineHeight: 1.55 }}>
        <div style={{ color: 'var(--amber)', fontWeight: 700, marginBottom: 6 }}>
          OFFICIAL BROKER & SECURITY POLICY
        </div>
        <div>• NO DEMO / SIMULATED TRADES: Never places fake demo trades.</div>
        <div>
          • QUOTEX BLOCKED: Quotex has no official public retail API. Never connects via unofficial APIs or collects broker passwords.
        </div>
        <div>
          • OFFICIAL AUTHORIZED INTEGRATIONS: Real trading requires regulated brokers with official OAuth2/Bearer APIs (OANDA v20, IG Group, Interactive Brokers) and explicit human authorization.
        </div>
      </div>

      <button type="button" className="btn-primary" onClick={() => setShowModal(true)}>
        RECORD MANUAL REAL-TRADE CONFIRMATION
      </button>

      <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--cyan)' }}>MANUAL TRADE AUDIT LEDGER</div>
      {trades.length === 0 ? (
        <div className="card" style={{ color: 'var(--text-muted)', fontSize: 13 }}>
          No manual real-trade confirmations recorded yet.
        </div>
      ) : (
        trades.map((tr) => (
          <div key={tr.id} className="card" style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
            <div className="row-between">
              <strong style={{ color: tr.direction === 'UP' ? 'var(--green)' : 'var(--red)' }}>
                {tr.symbol} • {tr.direction} • ${Number(tr.stakeUsd).toFixed(2)}
              </strong>
              <span style={{ fontSize: 11, color: 'var(--neon)' }}>{tr.status}</span>
            </div>
            <div style={{ fontSize: 12, color: 'var(--text-sec)' }}>Channel: {tr.brokerName}</div>
            {tr.notes && <div style={{ fontSize: 12 }}>Notes: {tr.notes}</div>}
            {tr.status !== 'CANCELLED' && (
              <div style={{ marginTop: 4 }}>
                <button type="button" className="btn-outline" onClick={() => onCancelTrade(tr.id)}>
                  Mark Cancelled
                </button>
              </div>
            )}
          </div>
        ))
      )}

      {showModal && (
        <ManualTradeModal
          asset={asset}
          initialDirection="UP"
          entryPrice={snapshot?.currentPrice || 0}
          onClose={() => setShowModal(false)}
          onConfirm={onConfirmTrade}
        />
      )}
    </div>
  );
}
