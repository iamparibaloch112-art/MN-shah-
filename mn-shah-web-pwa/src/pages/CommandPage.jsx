import React, { useEffect, useState } from 'react';
import RobotFace from '../components/RobotFace.jsx';
import { AiConnectionBadge, AiQuotaBanner, AiUsageMonitor } from '../components/AiQuotaWidgets.jsx';
import ManualTradeModal from '../components/ManualTradeModal.jsx';
import { DEFAULT_MARKETS } from '../constants/markets.js';

function CandleCountdown({ expiryEpochMs, mode = 'badge' }) {
  const [sec, setSec] = useState(() =>
    expiryEpochMs ? Math.max(0, Math.floor((expiryEpochMs - Date.now()) / 1000)) : 0
  );

  useEffect(() => {
    const update = () => {
      setSec(expiryEpochMs ? Math.max(0, Math.floor((expiryEpochMs - Date.now()) / 1000)) : 0);
    };
    update();
    if (!expiryEpochMs) return undefined;
    const id = setInterval(update, 1000);
    return () => clearInterval(id);
  }, [expiryEpochMs]);

  if (mode === 'inline') {
    return <div style={{ fontSize: 11, color: 'var(--neon)' }}>Expiry in {sec}s</div>;
  }
  return (
    <div className="mono" style={{ fontSize: 22, fontWeight: 700, color: sec <= 10 ? 'var(--amber)' : 'var(--neon)' }}>
      {sec}s
    </div>
  );
}

export default function CommandPage({
  markets = DEFAULT_MARKETS,
  selectedAssetId,
  onSelectAsset,
  snapshot,
  report,
  activeSignal,
  assistantReply,
  aiStatus,
  isAnalyzing,
  isRefreshing,
  isMonitoring,
  onToggleMonitoring,
  voiceAlertsEnabled,
  onToggleVoiceAlerts,
  isSpeaking,
  isListening,
  partialTranscript,
  micNotice,
  onAnalyzeNow,
  onReconnectFeed,
  onRecheckAi,
  checkingAi,
  onStartMic,
  onStopMic,
  onSubmitCommand,
  onSpeakUrdu,
  onStopSpeaking,
  onConfirmTrade,
  canInstallPwa,
  onInstallPwa
}) {
  const [commandText, setCommandText] = useState('');
  const [showTradeModal, setShowTradeModal] = useState(false);

  const feed = snapshot?.status || { type: 'CONNECTING' };
  const feedTone =
    feed.type === 'LIVE'
      ? { color: 'var(--green)', label: 'LIVE' }
      : feed.type === 'DELAYED'
        ? { color: 'var(--amber)', label: 'DELAYED' }
        : feed.type === 'DISCONNECTED'
          ? { color: 'var(--red)', label: 'OFFLINE' }
          : { color: 'var(--cyan)', label: 'SYNCING' };

  const robotStatus = isListening
    ? 'MIC ACTIVE • LISTENING'
    : isSpeaking
      ? 'SPEAKING URDU ANALYSIS'
      : isAnalyzing
        ? 'SCANNING 1M / 5M / 15M'
        : feed.type === 'LIVE'
          ? 'FEED LIVE • MONITORING'
          : feed.type === 'DELAYED'
            ? `FEED DELAYED (${feed.delaySeconds}s)`
            : 'FEED CHECKING...';

  const fallbackMeta = markets.find((m) => m.id === selectedAssetId);
  const assetObj = snapshot?.asset || fallbackMeta || { symbol: selectedAssetId.replace('_', '/'), decimals: 5 };
  const sigDir = activeSignal?.direction;
  const dirColor =
    sigDir === 'UP'
      ? 'var(--green)'
      : sigDir === 'DOWN'
        ? 'var(--red)'
        : 'var(--amber)';

  return (
    <div className="page-content">
      {/* Header */}
      <div className="row-between">
        <div>
          <h1 className="brand-title" style={{ fontSize: 20 }}>
            MN SHAH AI
          </h1>
          <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>Autonomous Urdu Market Robot</div>
        </div>
        <div className="row-gap">
          <span className="pill" style={{ color: feedTone.color, borderColor: feedTone.color }}>
            <span className="dot" style={{ background: feedTone.color }} />
            {feedTone.label}
          </span>
          <button type="button" className="btn-icon" onClick={onReconnectFeed} title="Reconnect Market">
            {isRefreshing ? '⏳' : '↻'}
          </button>
        </div>
      </div>

      {/* AI Connection Status & PWA Install Prompt */}
      <div className="row-between">
        <AiConnectionBadge status={aiStatus?.connectionStatus || 'ONLINE'} />
        {canInstallPwa ? (
          <button type="button" className="btn-outline" onClick={onInstallPwa}>
            Install App
          </button>
        ) : (
          <span style={{ fontSize: 11, color: 'var(--cyan)' }}>{aiStatus?.activeProvider || 'Gemini AI'}</span>
        )}
      </div>

      <RobotFace
        isAnalyzing={isAnalyzing}
        isSpeaking={isSpeaking}
        isListening={isListening}
        direction={activeSignal?.direction || report?.quantitativeDirection}
        statusLabel={robotStatus}
      />

      {/* Asset Chips */}
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

      {/* Live Price Card */}
      <div className="card">
        <div className="row-between">
          <div>
            <div style={{ fontSize: 13, color: 'var(--text-sec)' }}>{assetObj.symbol}</div>
            <div className="mono" style={{ fontSize: 28, fontWeight: 700 }}>
              {snapshot?.currentPrice > 0 ? snapshot.currentPrice.toFixed(assetObj.decimals) : '---'}
            </div>
          </div>
          <div style={{ textAlign: 'right' }}>
            <div style={{ fontSize: 10, color: 'var(--text-muted)' }}>1M CANDLE EXPIRY</div>
            <CandleCountdown expiryEpochMs={activeSignal?.candleExpiryEpochMs || snapshot?.candleExpiryEpochMs || 0} />
          </div>
        </div>
        <div style={{ fontSize: 11, color: feed.type === 'LIVE' ? 'var(--text-sec)' : 'var(--amber)', marginTop: 6 }}>
          {feed.type === 'LIVE'
            ? `Source: ${feed.source} • Tick ${new Date(feed.lastTickEpochMs).toLocaleTimeString()}`
            : feed.reason || 'Syncing live market feed...'}
        </div>
      </div>

      {/* Primary ANALYZE NOW Button */}
      <button type="button" className="btn-primary" disabled={isAnalyzing} onClick={() => onAnalyzeNow()}>
        {isAnalyzing ? 'ANALYZING 1M / 5M / 15M...' : '⚡ ANALYZE NOW'}
      </button>

      {/* Auto-Monitor & Urdu Voice Switches */}
      <div className="card row-between" style={{ padding: '10px 14px' }}>
        <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13 }}>
          <input
            type="checkbox"
            checked={isMonitoring}
            onChange={(e) => onToggleMonitoring(e.target.checked)}
            style={{ width: 18, height: 18 }}
          />
          Auto-Monitor
        </label>
        <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13 }}>
          <input
            type="checkbox"
            checked={voiceAlertsEnabled}
            onChange={(e) => {
              onToggleVoiceAlerts(e.target.checked);
              if (!e.target.checked) onStopSpeaking();
            }}
            style={{ width: 18, height: 18 }}
          />
          Urdu Voice
        </label>
      </div>

      {/* Quota Exhausted Urdu Banner */}
      <AiQuotaBanner
        alert={aiStatus?.quotaAlert}
        onSpeakUrdu={onSpeakUrdu}
        onRecheck={onRecheckAi}
        checking={checkingAi}
      />

      {/* Active Signal Card */}
      {activeSignal ? (
        <div className="panel" style={{ borderColor: dirColor, display: 'flex', flexDirection: 'column', gap: 10 }}>
          <div className="row-between">
            <div>
              <div style={{ fontSize: 10, color: 'var(--text-sec)' }}>{activeSignal.symbol} NEXT 1M ESTIMATE</div>
              <div
                className="mono"
                style={{
                  display: 'inline-block',
                  marginTop: 4,
                  padding: '6px 14px',
                  borderRadius: 10,
                  border: `1px solid ${dirColor}`,
                  color: dirColor,
                  fontSize: 20,
                  fontWeight: 700
                }}
              >
                {activeSignal.direction}
              </div>
            </div>
            <div style={{ textAlign: 'right' }}>
              <div style={{ fontSize: 12, fontWeight: 700, color: dirColor }}>
                CONFIDENCE {activeSignal.confidence}%
              </div>
              <div style={{ fontSize: 11, color: 'var(--text-muted)' }}>
                {new Date(activeSignal.generatedAtEpochMs).toLocaleTimeString()}
              </div>
              <CandleCountdown expiryEpochMs={activeSignal.candleExpiryEpochMs} mode="inline" />
            </div>
          </div>

          <div
            style={{
              background: 'var(--void)',
              border: '1px solid var(--border)',
              borderRadius: 12,
              padding: 12
            }}
          >
            <div className="row-between" style={{ marginBottom: 4 }}>
              <span style={{ fontSize: 11, fontWeight: 700, color: 'var(--neon)' }}>
                MN SHAH URDU BRIEFING • {activeSignal.providerLabel}
              </span>
              <button
                type="button"
                className="btn-icon"
                onClick={() => (isSpeaking ? onStopSpeaking() : onSpeakUrdu(activeSignal.urduSpeech))}
              >
                {isSpeaking ? '⏹' : '🔊'}
              </button>
            </div>
            <div className="urdu-text">{activeSignal.urduSpeech}</div>
            <div style={{ fontSize: 12, color: 'var(--text-sec)', marginTop: 6 }}>{activeSignal.romanUrduSummary}</div>
          </div>

          <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--cyan)' }}>TECHNICAL EVIDENCE</div>
          <ul style={{ margin: 0, paddingLeft: 18, fontSize: 13, color: 'var(--text)', lineHeight: 1.5 }}>
            {activeSignal.evidencePoints.map((pt, i) => (
              <li key={i}>{pt}</li>
            ))}
          </ul>
          <div style={{ fontSize: 11, color: 'var(--amber)' }}>{activeSignal.riskNote}</div>

          {(activeSignal.direction === 'UP' || activeSignal.direction === 'DOWN') && (
            <button type="button" className="btn-outline" onClick={() => setShowTradeModal(true)}>
              LOG MANUAL REAL TRADE ({activeSignal.direction})
            </button>
          )}
        </div>
      ) : (
        <div className="card">
          <div style={{ color: 'var(--neon)', fontWeight: 700, fontSize: 13, marginBottom: 4 }}>
            READY FOR SIGNAL COMMAND
          </div>
          <div style={{ fontSize: 13, color: 'var(--text-sec)' }}>
            Tap <strong>ANALYZE NOW</strong> or tap the mic and say{' '}
            <em>"MN SHAH mujhe signal do"</em> for live 1M / 5M / 15M technical + Urdu analysis.
          </div>
        </div>
      )}

      {assistantReply && (
        <div className="card" style={{ borderColor: 'var(--cyan)' }}>
          <div style={{ fontSize: 11, color: 'var(--cyan)', marginBottom: 4 }}>Q: {assistantReply.query}</div>
          <div className="urdu-text">{assistantReply.urduSpeech}</div>
          <div style={{ fontSize: 12, color: 'var(--text-sec)', marginTop: 4 }}>{assistantReply.romanSummary}</div>
        </div>
      )}

      {/* Voice & Text Command Center */}
      <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
        <div style={{ fontSize: 11, fontWeight: 700, color: 'var(--neon)' }}>VOICE & TEXT COMMAND CENTER</div>
        <div className="chip-strip">
          <button
            type="button"
            className="chip"
            onClick={() => onSubmitCommand('MN SHAH mujhe signal do', true)}
          >
            MN SHAH mujhe signal do
          </button>
          <button type="button" className="chip" onClick={() => onSubmitCommand('XAU USD signal do', true)}>
            Gold XAU/USD signal
          </button>
        </div>

        {isListening && partialTranscript && (
          <div style={{ fontSize: 12, color: 'var(--cyan)' }}>Mic: {partialTranscript}</div>
        )}
        {micNotice && <div style={{ fontSize: 12, color: 'var(--amber)' }}>{micNotice}</div>}

        <form
          className="row-gap"
          onSubmit={(e) => {
            e.preventDefault();
            if (!commandText.trim()) return;
            onSubmitCommand(commandText.trim(), false);
            setCommandText('');
          }}
        >
          <button
            type="button"
            className="btn-icon"
            style={{ background: isListening ? 'var(--cyan)' : 'var(--panel)', color: isListening ? '#06070A' : 'var(--neon)' }}
            onClick={() => (isListening ? onStopMic() : onStartMic())}
            title="Speak Command"
          >
            🎤
          </button>
          <input
            type="text"
            className="input"
            placeholder="MN SHAH mujhe signal do"
            value={commandText}
            onChange={(e) => setCommandText(e.target.value)}
          />
          <button
            type="submit"
            className="btn-icon"
            style={{ background: 'var(--neon)', color: 'var(--void)', fontWeight: 700 }}
            title="Send"
          >
            ➤
          </button>
        </form>
      </div>

      <AiUsageMonitor aiStatus={aiStatus} onRecheck={onRecheckAi} checking={checkingAi} />

      <div style={{ fontSize: 11, color: 'var(--text-muted)', textAlign: 'center' }}>
        Quantitative + AI estimation only. Never guaranteed; manual human confirmation required.
      </div>

      {showTradeModal && (
        <ManualTradeModal
          asset={assetObj}
          initialDirection={activeSignal?.direction || 'UP'}
          entryPrice={snapshot?.currentPrice || 0}
          onClose={() => setShowTradeModal(false)}
          onConfirm={onConfirmTrade}
        />
      )}
    </div>
  );
}
