import React, { useCallback, useEffect, useRef, useState } from 'react';
import { api } from './api.js';
import { createSpeechListener, parseVoiceCommand, speakUrduText, stopSpeaking } from './voice.js';
import AuthGate from './components/AuthGate.jsx';
import RobotFace from './components/RobotFace.jsx';
import CommandPage from './pages/CommandPage.jsx';
import MarketPage from './pages/MarketPage.jsx';
import SignalsPage from './pages/SignalsPage.jsx';
import BrokerPage from './pages/BrokerPage.jsx';
import { DEFAULT_MARKETS } from './constants/markets.js';

export default function App() {
  const [authChecking, setAuthChecking] = useState(true);
  const [ownerUser, setOwnerUser] = useState(null);
  const [activeTab, setActiveTab] = useState('command');

  const [selectedAssetId, setSelectedAssetId] = useState('EUR_USD');
  const [markets, setMarkets] = useState(DEFAULT_MARKETS);
  const [snapshot, setSnapshot] = useState(null);
  const [report, setReport] = useState(null);
  const [activeSignal, setActiveSignal] = useState(null);
  const [assistantReply, setAssistantReply] = useState(null);
  const [aiStatus, setAiStatus] = useState(null);
  const [signals, setSignals] = useState([]);
  const [trades, setTrades] = useState([]);

  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [checkingAi, setCheckingAi] = useState(false);
  const [isMonitoring, setIsMonitoring] = useState(true);
  const [voiceAlertsEnabled, setVoiceAlertsEnabled] = useState(true);

  const [isSpeaking, setIsSpeaking] = useState(false);
  const [isListening, setIsListening] = useState(false);
  const [partialTranscript, setPartialTranscript] = useState('');
  const [micNotice, setMicNotice] = useState('');

  const [installPromptEvent, setInstallPromptEvent] = useState(null);
  const speechListenerRef = useRef(null);
  const refreshingRef = useRef(false);
  const analyzingRef = useRef(false);
  analyzingRef.current = isAnalyzing;

  useEffect(() => {
    const onBeforeInstall = (e) => {
      e.preventDefault();
      setInstallPromptEvent(e);
    };
    window.addEventListener('beforeinstallprompt', onBeforeInstall);
    return () => window.removeEventListener('beforeinstallprompt', onBeforeInstall);
  }, []);

  useEffect(() => {
    api
      .getMe()
      .then((res) => {
        if (res.authenticated && res.user) setOwnerUser(res.user);
      })
      .catch(() => {})
      .finally(() => setAuthChecking(false));
  }, []);

  const speakUrdu = useCallback(
    (text) => {
      if (!voiceAlertsEnabled || !text) return;
      speakUrduText(text, {
        onStart: () => setIsSpeaking(true),
        onEnd: () => setIsSpeaking(false)
      });
    },
    [voiceAlertsEnabled]
  );

  const refreshMarket = useCallback(
    async (assetId = selectedAssetId) => {
      if (!ownerUser || refreshingRef.current) return;
      refreshingRef.current = true;
      setIsRefreshing(true);
      try {
        const res = await api.getMarket(assetId);
        setSnapshot(res.snapshot);
        setReport(res.report);
      } catch (err) {
        if (err.status === 401) setOwnerUser(null);
      } finally {
        refreshingRef.current = false;
        setIsRefreshing(false);
      }
    },
    [ownerUser, selectedAssetId]
  );

  useEffect(() => {
    try {
      const cachedSignals = JSON.parse(localStorage.getItem('mn_shah_signals') || '[]');
      if (Array.isArray(cachedSignals) && cachedSignals.length) setSignals(cachedSignals);
      const cachedTrades = JSON.parse(localStorage.getItem('mn_shah_trades') || '[]');
      if (Array.isArray(cachedTrades) && cachedTrades.length) setTrades(cachedTrades);
    } catch {}
  }, []);

  useEffect(() => {
    try {
      localStorage.setItem('mn_shah_signals', JSON.stringify(signals.slice(0, 50)));
    } catch {}
  }, [signals]);

  useEffect(() => {
    try {
      localStorage.setItem('mn_shah_trades', JSON.stringify(trades.slice(0, 50)));
    } catch {}
  }, [trades]);

  useEffect(() => {
    if (!ownerUser) return;
    refreshMarket(selectedAssetId);
    api.getMarkets().then((r) => {
      if (Array.isArray(r.markets) && r.markets.length) setMarkets(r.markets);
    }).catch(() => {});
    api.getAiStatus().then(setAiStatus).catch(() => {});
    api.getSignals().then((r) => {
      if (Array.isArray(r.signals) && r.signals.length) setSignals(r.signals);
    }).catch(() => {});
    api.getTrades().then((r) => {
      if (Array.isArray(r.trades) && r.trades.length) setTrades(r.trades);
    }).catch(() => {});
  }, [ownerUser, selectedAssetId, refreshMarket]);

  useEffect(() => {
    if (!ownerUser || !isMonitoring) return undefined;
    const id = setInterval(() => {
      if (!analyzingRef.current) refreshMarket(selectedAssetId);
    }, 20_000);
    return () => clearInterval(id);
  }, [ownerUser, isMonitoring, selectedAssetId, refreshMarket]);

  async function handleAnalyzeNow(commandOverride = 'MN SHAH mujhe signal do', assetOverride = null, forceSpeak = false) {
    if (isAnalyzing) return;
    const targetAsset = assetOverride || selectedAssetId;
    if (assetOverride && assetOverride !== selectedAssetId) setSelectedAssetId(assetOverride);
    setIsAnalyzing(true);
    try {
      const res = await api.analyzeNow(targetAsset, commandOverride);
      setSnapshot(res.snapshot);
      setReport(res.report);
      setActiveSignal(res.signal);
      setAiStatus(res.aiStatus);
      setSignals((prev) => [res.signal, ...prev.filter((s) => s.id !== res.signal.id)]);
      if (voiceAlertsEnabled || forceSpeak) {
        speakUrduText(res.signal.urduSpeech, {
          onStart: () => setIsSpeaking(true),
          onEnd: () => setIsSpeaking(false)
        });
      }
    } catch (err) {
      if (err.status === 401) setOwnerUser(null);
      else setMicNotice(err.message);
    } finally {
      setIsAnalyzing(false);
    }
  }

  async function handleSubmitCommand(rawText, fromVoice) {
    const parsed = parseVoiceCommand(rawText);
    if (parsed.type === 'REQUEST_SIGNAL') {
      await handleAnalyzeNow(rawText, parsed.asset, fromVoice);
      return;
    }
    if (parsed.type === 'SWITCH_ASSET' && parsed.asset) {
      setSelectedAssetId(parsed.asset);
      const speech = `${parsed.asset.replace('_', '/')} کا لائیو چارٹ لوڈ کیا جا رہا ہے۔`;
      setAssistantReply({ query: rawText, urduSpeech: speech, romanSummary: 'Loading live chart.' });
      if (voiceAlertsEnabled || fromVoice) speakUrdu(speech);
      return;
    }
    try {
      const res = await api.askAssistant(rawText, selectedAssetId);
      setAssistantReply({ query: rawText, urduSpeech: res.urduSpeech, romanSummary: res.romanSummary });
      if (res.aiStatus) setAiStatus(res.aiStatus);
      if (voiceAlertsEnabled || fromVoice) speakUrdu(res.urduSpeech);
    } catch (err) {
      setMicNotice(err.message);
    }
  }

  function handleStartMic() {
    setMicNotice('');
    setPartialTranscript('');
    if (!speechListenerRef.current) {
      speechListenerRef.current = createSpeechListener({
        onPartial: setPartialTranscript,
        onFinal: (txt) => handleSubmitCommand(txt, true),
        onError: setMicNotice,
        onStateChange: setIsListening
      });
    }
    speechListenerRef.current.start();
  }

  async function handleLogout() {
    stopSpeaking();
    await api.logout().catch(() => {});
    setOwnerUser(null);
  }

  if (authChecking) {
    return (
      <div className="app-shell" style={{ justifyContent: 'center', alignItems: 'center', padding: 24 }}>
        <div style={{ width: '100%', maxWidth: 360, display: 'flex', flexDirection: 'column', gap: 16, alignItems: 'center' }}>
          <div className="brand-title" style={{ fontSize: 26 }}>
            MN SHAH AI
          </div>
          <RobotFace
            isAnalyzing
            isSpeaking={false}
            isListening={false}
            direction={null}
            statusLabel="INITIALIZING SECURE SESSION..."
          />
        </div>
      </div>
    );
  }

  if (!ownerUser) {
    return <AuthGate onAuthenticated={(u) => setOwnerUser(u)} />;
  }

  return (
    <div className="app-shell">
      {activeTab === 'command' && (
        <CommandPage
          markets={markets}
          selectedAssetId={selectedAssetId}
          onSelectAsset={setSelectedAssetId}
          snapshot={snapshot}
          report={report}
          activeSignal={activeSignal}
          assistantReply={assistantReply}
          aiStatus={aiStatus}
          isAnalyzing={isAnalyzing}
          isRefreshing={isRefreshing}
          isMonitoring={isMonitoring}
          onToggleMonitoring={setIsMonitoring}
          voiceAlertsEnabled={voiceAlertsEnabled}
          onToggleVoiceAlerts={setVoiceAlertsEnabled}
          isSpeaking={isSpeaking}
          isListening={isListening}
          partialTranscript={partialTranscript}
          micNotice={micNotice}
          onAnalyzeNow={() => handleAnalyzeNow()}
          onReconnectFeed={() => refreshMarket(selectedAssetId)}
          onRecheckAi={async () => {
            setCheckingAi(true);
            const res = await api.recheckAi().catch(() => null);
            if (res?.status) setAiStatus(res.status);
            setCheckingAi(false);
          }}
          checkingAi={checkingAi}
          onStartMic={handleStartMic}
          onStopMic={() => speechListenerRef.current?.stop()}
          onSubmitCommand={handleSubmitCommand}
          onSpeakUrdu={speakUrdu}
          onStopSpeaking={() => {
            stopSpeaking();
            setIsSpeaking(false);
          }}
          onConfirmTrade={async (payload) => {
            const res = await api.createTrade(payload);
            setTrades((prev) => [res.trade, ...prev]);
          }}
          canInstallPwa={Boolean(installPromptEvent)}
          onInstallPwa={async () => {
            if (!installPromptEvent) return;
            installPromptEvent.prompt();
            await installPromptEvent.userChoice;
            setInstallPromptEvent(null);
          }}
        />
      )}

      {activeTab === 'market' && (
        <MarketPage
          markets={markets}
          selectedAssetId={selectedAssetId}
          onSelectAsset={setSelectedAssetId}
          snapshot={snapshot}
          report={report}
          onReconnect={() => refreshMarket(selectedAssetId)}
        />
      )}

      {activeTab === 'signals' && (
        <SignalsPage
          signals={signals}
          onDeleteSignal={async (id) => {
            await api.deleteSignal(id);
            setSignals((prev) => prev.filter((s) => s.id !== id));
          }}
          onSpeakUrdu={speakUrdu}
        />
      )}

      {activeTab === 'broker' && (
        <BrokerPage
          user={ownerUser}
          snapshot={snapshot}
          selectedAssetId={selectedAssetId}
          trades={trades}
          onConfirmTrade={async (payload) => {
            const res = await api.createTrade(payload);
            setTrades((prev) => [res.trade, ...prev]);
          }}
          onCancelTrade={async (id) => {
            await api.cancelTrade(id);
            setTrades((prev) => prev.map((t) => (t.id === id ? { ...t, status: 'CANCELLED' } : t)));
          }}
          onLogout={handleLogout}
        />
      )}

      <nav className="bottom-nav" aria-label="Primary Navigation">
        {[
          { id: 'command', label: 'Command', icon: '🤖' },
          { id: 'market', label: 'Market', icon: '📈' },
          { id: 'signals', label: 'Signals', icon: '🕒' },
          { id: 'broker', label: 'Broker', icon: '🛡️' }
        ].map((tab) => (
          <button
            key={tab.id}
            type="button"
            className={`nav-item ${activeTab === tab.id ? 'active' : ''}`}
            onClick={() => setActiveTab(tab.id)}
          >
            <span style={{ fontSize: 17 }}>{tab.icon}</span>
            <span>{tab.label}</span>
          </button>
        ))}
      </nav>
    </div>
  );
}
