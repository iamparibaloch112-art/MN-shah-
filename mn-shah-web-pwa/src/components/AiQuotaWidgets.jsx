import React from 'react';

export function AiConnectionBadge({ status }) {
  const tone =
    status === 'ONLINE'
      ? { color: 'var(--green)', bg: 'var(--green-bg)', label: 'AI: ONLINE' }
      : status === 'QUOTA EXCEEDED'
        ? { color: 'var(--amber)', bg: 'var(--amber-bg)', label: 'AI: QUOTA EXCEEDED' }
        : { color: 'var(--red)', bg: 'var(--red-bg)', label: 'AI: OFFLINE' };

  return (
    <span className="pill" style={{ color: tone.color, background: tone.bg, borderColor: tone.color }}>
      <span className="dot" style={{ background: tone.color }} />
      {tone.label}
    </span>
  );
}

export function AiQuotaBanner({ alert, onSpeakUrdu, onRecheck, checking }) {
  if (!alert) return null;
  return (
    <div
      className="panel"
      style={{
        borderColor: 'var(--amber)',
        background: 'linear-gradient(180deg, rgba(255,184,0,0.16), var(--panel))',
        display: 'flex',
        flexDirection: 'column',
        gap: 8
      }}
    >
      <div className="row-between">
        <div>
          <div style={{ color: 'var(--neon)', fontWeight: 700, fontSize: 15 }}>{alert.urduTitle}</div>
          <div style={{ color: 'var(--amber)', fontSize: 11 }}>
            {alert.errorCodeLabel} • Cooldown: {alert.cooldownRemainingText}
          </div>
        </div>
        <button type="button" className="btn-icon" onClick={() => onSpeakUrdu(alert.urduMessage)} title="Speak Urdu">
          🔊
        </button>
      </div>

      <div
        style={{
          background: 'var(--void)',
          border: '1px solid var(--border)',
          borderRadius: 12,
          padding: 10
        }}
      >
        <div className="urdu-text">{alert.urduMessage}</div>
        <div style={{ fontSize: 12, color: 'var(--text-sec)', marginTop: 6 }}>{alert.romanUrduMessage}</div>
      </div>

      <div className="row-between">
        <span style={{ fontSize: 11, color: 'var(--cyan)' }}>{alert.backupProviderNote}</span>
        <button type="button" className="btn-outline" onClick={onRecheck} disabled={checking}>
          {checking ? 'Checking...' : 'Re-test AI'}
        </button>
      </div>
    </div>
  );
}

export function AiUsageMonitor({ aiStatus, onRecheck, checking }) {
  if (!aiStatus) return null;
  return (
    <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
      <div className="row-between">
        <div>
          <div style={{ color: 'var(--neon)', fontSize: 12, fontWeight: 700 }}>AI USAGE & QUOTA MONITOR</div>
          <div style={{ color: 'var(--text-sec)', fontSize: 11 }}>Provider: {aiStatus.activeProvider}</div>
        </div>
        <button type="button" className="btn-outline" onClick={onRecheck} disabled={checking}>
          {checking ? '...' : 'Re-check'}
        </button>
      </div>

      <div className="metrics-grid">
        <div className="metric-box">
          <div className="mono" style={{ color: 'var(--neon)', fontSize: 16, fontWeight: 700 }}>
            {aiStatus.requestsToday}
          </div>
          <div style={{ fontSize: 10, color: 'var(--text-muted)' }}>REQUESTS</div>
        </div>
        <div className="metric-box">
          <div className="mono" style={{ color: 'var(--green)', fontSize: 16, fontWeight: 700 }}>
            {aiStatus.successfulResponsesToday}
          </div>
          <div style={{ fontSize: 10, color: 'var(--text-muted)' }}>SUCCESS</div>
        </div>
        <div className="metric-box">
          <div className="mono" style={{ color: 'var(--cyan)', fontSize: 16, fontWeight: 700 }}>
            {aiStatus.temporaryRetriesToday}
          </div>
          <div style={{ fontSize: 10, color: 'var(--text-muted)' }}>RETRIES</div>
        </div>
        <div className="metric-box">
          <div className="mono" style={{ color: 'var(--amber)', fontSize: 16, fontWeight: 700 }}>
            {aiStatus.quotaErrorsToday}
          </div>
          <div style={{ fontSize: 10, color: 'var(--text-muted)' }}>QUOTA HITS</div>
        </div>
      </div>

      <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>
        Backup AI:{' '}
        {aiStatus.backupConfigured
          ? `READY (${aiStatus.backupCallsToday}/${aiStatus.backupQuotaLimit})`
          : 'DISABLED (Primary Gemini Mode)'}
        {aiStatus.lastErrorSummary ? ` • Last Event: ${aiStatus.lastErrorSummary}` : ''}
      </div>
    </div>
  );
}
