package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.ActiveAiProvider
import com.example.data.ai.AiConnectionStatus
import com.example.data.ai.AiQuotaAlert
import com.example.data.ai.AiUsageStats
import com.example.data.ai.DailyQuotaExhaustedException
import com.example.data.ai.MnShahAiAnalyst
import com.example.data.market.FeedStatus
import com.example.data.market.LiveMarketRepository
import com.example.data.market.MultiTimeframeMarketSnapshot
import com.example.data.market.Timeframe
import com.example.data.market.TradingAsset
import com.example.data.model.SignalRecord
import com.example.data.model.TradeConfirmation
import com.example.data.model.UserProfile
import com.example.data.repository.TradingRepository
import com.example.domain.analysis.SignalDirection
import com.example.domain.analysis.TechnicalAnalysisEngine
import com.example.domain.analysis.TechnicalAnalysisReport
import com.example.voice.MnShahVoiceEngine
import com.example.voice.ParsedVoiceCommand
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class ActiveSignalPresentation(
    val signalId: String?,
    val asset: TradingAsset,
    val direction: SignalDirection,
    val confidence: Int,
    val entryPrice: Double,
    val rsi1m: Double,
    val macdSignal: String,
    val emaTrend: String,
    val patternSummary: String,
    val urduSpeech: String,
    val romanUrduSummary: String,
    val evidencePoints: List<String>,
    val riskNote: String,
    val dataSource: String,
    val generatedAtEpochMs: Long,
    val candleExpiryEpochMs: Long,
    val aiBacked: Boolean,
    val providerLabel: String = ActiveAiProvider.GEMINI.displayName
)

data class AssistantChatTurn(
    val userQuery: String,
    val urduSpeech: String,
    val romanSummary: String,
    val timestampMs: Long
)

class MnShahViewModel(
    private val tradingRepository: TradingRepository,
    private val marketRepository: LiveMarketRepository,
    private val aiAnalyst: MnShahAiAnalyst,
    private val currentUserId: String,
    private val displayName: String
) : ViewModel() {

    private val _selectedAsset = MutableStateFlow(TradingAsset.EUR_USD)
    val selectedAsset: StateFlow<TradingAsset> = _selectedAsset.asStateFlow()

    private val _selectedTimeframe = MutableStateFlow(Timeframe.M1)
    val selectedTimeframe: StateFlow<Timeframe> = _selectedTimeframe.asStateFlow()

    private val _marketSnapshot = MutableStateFlow<MultiTimeframeMarketSnapshot?>(null)
    val marketSnapshot: StateFlow<MultiTimeframeMarketSnapshot?> = _marketSnapshot.asStateFlow()

    private val _technicalReport = MutableStateFlow<TechnicalAnalysisReport?>(null)
    val technicalReport: StateFlow<TechnicalAnalysisReport?> = _technicalReport.asStateFlow()

    private val _activeSignal = MutableStateFlow<ActiveSignalPresentation?>(null)
    val activeSignal: StateFlow<ActiveSignalPresentation?> = _activeSignal.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _isRefreshingFeed = MutableStateFlow(false)
    val isRefreshingFeed: StateFlow<Boolean> = _isRefreshingFeed.asStateFlow()

    private val _isMonitoring = MutableStateFlow(true)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private val _voiceAlertsEnabled = MutableStateFlow(true)
    val voiceAlertsEnabled: StateFlow<Boolean> = _voiceAlertsEnabled.asStateFlow()

    private val _lastCommandFeedback = MutableStateFlow<String?>(null)
    val lastCommandFeedback: StateFlow<String?> = _lastCommandFeedback.asStateFlow()

    private val _assistantReply = MutableStateFlow<AssistantChatTurn?>(null)
    val assistantReply: StateFlow<AssistantChatTurn?> = _assistantReply.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _tradeActionMessage = MutableStateFlow<String?>(null)
    val tradeActionMessage: StateFlow<String?> = _tradeActionMessage.asStateFlow()

    private val _countdownSeconds = MutableStateFlow(0L)
    val countdownSeconds: StateFlow<Long> = _countdownSeconds.asStateFlow()

    private val _isCheckingAiConnection = MutableStateFlow(false)
    val isCheckingAiConnection: StateFlow<Boolean> = _isCheckingAiConnection.asStateFlow()

    val aiConnectionStatus: StateFlow<AiConnectionStatus> = aiAnalyst.connectionStatus
    val aiUsageStats: StateFlow<AiUsageStats> = aiAnalyst.usageStats
    val aiQuotaAlert: StateFlow<AiQuotaAlert?> = aiAnalyst.quotaAlert

    val userProfile: StateFlow<UserProfile?> = tradingRepository.observeUserProfile(currentUserId)
        .catch { error ->
            if (error is CancellationException && error.cause !is FirebaseFirestoreException) throw error
            Log.w(TAG, "Profile observation failed", error)
            emit(null)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = null
        )

    val savedSignals: StateFlow<UiState<List<SignalRecord>>> = tradingRepository.observeSignals(currentUserId)
        .map<List<SignalRecord>, UiState<List<SignalRecord>>> { UiState.Success(it) }
        .catch { error ->
            val fsErr = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (fsErr == null && error is CancellationException) throw error
            val root = fsErr ?: error
            Log.w(TAG, "Error observing signals", root)
            emit(UiState.Error(root.message ?: "Failed to load signal history"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val tradeConfirmations: StateFlow<UiState<List<TradeConfirmation>>> =
        tradingRepository.observeTradeConfirmations(currentUserId)
            .map<List<TradeConfirmation>, UiState<List<TradeConfirmation>>> { UiState.Success(it) }
            .catch { error ->
                val fsErr = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (fsErr == null && error is CancellationException) throw error
                val root = fsErr ?: error
                Log.w(TAG, "Error observing trade confirmations", root)
                emit(UiState.Error(root.message ?: "Failed to load trade audit log"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading
            )

    private var monitorJob: Job? = null
    private var profileSynced = false
    private val refreshMutex = Mutex()
    private var lastRefreshedAsset: TradingAsset? = null
    private var lastRefreshTimeMs: Long = 0L

    init {
        viewModelScope.launch {
            runCatching { aiAnalyst.initializeQuotaState() }
        }
        initializeUserAndMonitor()
        startExpiryTicker()
    }

    private fun initializeUserAndMonitor() {
        viewModelScope.launch {
            try {
                tradingRepository.ensureUserProfile(currentUserId, displayName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not ensure user profile", e)
            }
        }
        viewModelScope.launch {
            userProfile.collect { profile ->
                if (profile != null && !profileSynced) {
                    profileSynced = true
                    _voiceAlertsEnabled.value = profile.voiceAlertsEnabled
                    _isMonitoring.value = profile.autoMonitorEnabled
                    val preferred = TradingAsset.fromSymbol(profile.preferredAsset)
                    val assetChanged = preferred != _selectedAsset.value
                    _selectedAsset.value = preferred
                    if (assetChanged) {
                        refreshMarketSnapshot(force = true)
                    }
                    restartMonitoringLoop()
                }
            }
        }
        // Kick off initial market fetch immediately without waiting for profile roundtrip
        viewModelScope.launch {
            refreshMarketSnapshot()
            restartMonitoringLoop()
        }
    }

    private fun startExpiryTicker() {
        viewModelScope.launch {
            while (isActive) {
                val expiry = _activeSignal.value?.candleExpiryEpochMs
                    ?: _marketSnapshot.value?.candleExpiryEpochMs
                    ?: 0L
                val remaining = ((expiry - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
                if (_countdownSeconds.value != remaining) {
                    _countdownSeconds.value = remaining
                }
                delay(1000L)
            }
        }
    }

    private fun restartMonitoringLoop() {
        monitorJob?.cancel()
        if (!_isMonitoring.value) return
        monitorJob = viewModelScope.launch {
            while (isActive && _isMonitoring.value) {
                delay(20_000L)
                if (_isMonitoring.value && !_isAnalyzing.value) {
                    refreshMarketSnapshot()
                }
            }
        }
    }

    fun selectAsset(asset: TradingAsset) {
        if (_selectedAsset.value == asset) return
        _selectedAsset.value = asset
        _errorMessage.value = null
        viewModelScope.launch {
            refreshMarketSnapshot()
            runCatching {
                tradingRepository.updatePreferences(
                    userId = currentUserId,
                    voiceAlertsEnabled = _voiceAlertsEnabled.value,
                    autoMonitorEnabled = _isMonitoring.value,
                    preferredAsset = asset.symbol
                )
            }
        }
    }

    fun selectTimeframe(timeframe: Timeframe) {
        _selectedTimeframe.value = timeframe
    }

    fun toggleMonitoring(enabled: Boolean) {
        _isMonitoring.value = enabled
        restartMonitoringLoop()
        viewModelScope.launch {
            runCatching {
                tradingRepository.updatePreferences(
                    userId = currentUserId,
                    voiceAlertsEnabled = _voiceAlertsEnabled.value,
                    autoMonitorEnabled = enabled,
                    preferredAsset = _selectedAsset.value.symbol
                )
            }
        }
    }

    fun toggleVoiceAlerts(enabled: Boolean) {
        _voiceAlertsEnabled.value = enabled
        viewModelScope.launch {
            runCatching {
                tradingRepository.updatePreferences(
                    userId = currentUserId,
                    voiceAlertsEnabled = enabled,
                    autoMonitorEnabled = _isMonitoring.value,
                    preferredAsset = _selectedAsset.value.symbol
                )
            }
        }
    }

    fun reconnectMarketFeed() {
        _errorMessage.value = null
        viewModelScope.launch {
            refreshMarketSnapshot(force = true)
        }
    }

    fun recheckAiConnection() {
        if (_isCheckingAiConnection.value) return
        viewModelScope.launch {
            _isCheckingAiConnection.value = true
            _errorMessage.value = null
            try {
                val res = aiAnalyst.recheckAiConnection()
                if (res.isFailure) {
                    val ex = res.exceptionOrNull()
                    if (ex !is DailyQuotaExhaustedException) {
                        _errorMessage.value = ex?.localizedMessage ?: "AI connection check failed."
                    }
                }
            } finally {
                _isCheckingAiConnection.value = false
            }
        }
    }

    suspend fun refreshMarketSnapshot(force: Boolean = false): MultiTimeframeMarketSnapshot {
        return refreshMutex.withLock {
            val targetAsset = _selectedAsset.value
            val cached = _marketSnapshot.value
            val now = System.currentTimeMillis()
            if (!force &&
                cached != null &&
                lastRefreshedAsset == targetAsset &&
                (now - lastRefreshTimeMs) < 3_000L
            ) {
                return@withLock cached
            }

            _isRefreshingFeed.value = true
            try {
                val snapshot = marketRepository.fetchMultiTimeframeSnapshot(targetAsset)
                val report = withContext(Dispatchers.Default) {
                    TechnicalAnalysisEngine.analyzeSnapshot(snapshot)
                }
                _marketSnapshot.value = snapshot
                _technicalReport.value = report
                lastRefreshedAsset = targetAsset
                lastRefreshTimeMs = System.currentTimeMillis()
                snapshot
            } finally {
                _isRefreshingFeed.value = false
            }
        }
    }

    fun analyzeNow(
        userCommand: String? = null,
        forceSpeak: Boolean = false,
        onSpeakUrdu: (String) -> Unit
    ) {
        if (_isAnalyzing.value) return
        viewModelScope.launch {
            _isAnalyzing.value = true
            _errorMessage.value = null
            try {
                val snapshot = refreshMarketSnapshot(force = true)
                val report = _technicalReport.value ?: withContext(Dispatchers.Default) {
                    TechnicalAnalysisEngine.analyzeSnapshot(snapshot)
                }.also { _technicalReport.value = it }

                val sourceLabel = when (val s = snapshot.status) {
                    FeedStatus.Connecting -> "Connecting"
                    is FeedStatus.Live -> s.source
                    is FeedStatus.Delayed -> "${s.source} (Delayed ${s.delaySeconds}s)"
                    is FeedStatus.Disconnected -> "Disconnected"
                }

                val aiResult = aiAnalyst.generateSignalBriefing(snapshot, report, userCommand)
                val now = System.currentTimeMillis()
                val expiry = ((now / 60_000L) + 1L) * 60_000L

                val presentation = if (aiResult.isSuccess) {
                    val briefing = aiResult.getOrThrow()
                    ActiveSignalPresentation(
                        signalId = null,
                        asset = snapshot.asset,
                        direction = briefing.direction,
                        confidence = briefing.confidence,
                        entryPrice = snapshot.currentPrice,
                        rsi1m = report.m1?.rsi14 ?: 50.0,
                        macdSignal = report.m1?.macd?.statusLabel ?: "N/A",
                        emaTrend = report.m1?.emaBias?.name ?: "NEUTRAL",
                        patternSummary = report.m1?.candlePattern ?: "Insufficient Data",
                        urduSpeech = briefing.urduSpeech,
                        romanUrduSummary = briefing.romanUrduSummary,
                        evidencePoints = briefing.evidencePoints,
                        riskNote = briefing.riskNote,
                        dataSource = sourceLabel,
                        generatedAtEpochMs = now,
                        candleExpiryEpochMs = expiry,
                        aiBacked = true,
                        providerLabel = briefing.providerUsed.displayName
                    )
                } else {
                    val failure = aiResult.exceptionOrNull()
                    val isDailyQuota = failure is DailyQuotaExhaustedException
                    if (!isDailyQuota) {
                        val aiError = failure?.localizedMessage ?: "AI analysis unavailable."
                        _errorMessage.value = "AI Analyst offline ($aiError). Showing deterministic technical indicators only."
                    }
                    val fallbackUrdu = buildFallbackUrdu(snapshot.asset, report, isDailyQuota)
                    ActiveSignalPresentation(
                        signalId = null,
                        asset = snapshot.asset,
                        direction = report.quantitativeDirection,
                        confidence = report.confidenceEstimate,
                        entryPrice = snapshot.currentPrice,
                        rsi1m = report.m1?.rsi14 ?: 50.0,
                        macdSignal = report.m1?.macd?.statusLabel ?: "N/A",
                        emaTrend = report.m1?.emaBias?.name ?: "NEUTRAL",
                        patternSummary = report.m1?.candlePattern ?: "Insufficient Data",
                        urduSpeech = fallbackUrdu,
                        romanUrduSummary = if (isDailyQuota) {
                            "Daily AI quota mukammal hai. 1M/5M/15M technical indicators ke mutabiq signal ${report.quantitativeDirection.name} hai."
                        } else {
                            report.insufficiencyReason
                                ?: "Quantitative indicators computed across 1M, 5M, and 15M."
                        },
                        evidencePoints = report.technicalEvidence,
                        riskNote = "AI commentary unavailable. Quantitative indicator reading only; never guaranteed.",
                        dataSource = sourceLabel,
                        generatedAtEpochMs = now,
                        candleExpiryEpochMs = expiry,
                        aiBacked = false,
                        providerLabel = ActiveAiProvider.QUANTITATIVE_ONLY.displayName
                    )
                }

                var savedId: String? = null
                if (snapshot.currentPrice > 0.0) {
                    val record = SignalRecord(
                        userId = currentUserId,
                        symbol = presentation.asset.symbol,
                        direction = presentation.direction.name,
                        confidence = presentation.confidence,
                        entryPrice = presentation.entryPrice,
                        rsi1m = presentation.rsi1m,
                        macdSignal = presentation.macdSignal,
                        emaTrend = presentation.emaTrend,
                        patternSummary = presentation.patternSummary,
                        urduSummary = presentation.urduSpeech,
                        evidenceSummary = presentation.evidencePoints.joinToString(" | "),
                        dataSource = presentation.dataSource,
                        candleExpiryEpochMs = presentation.candleExpiryEpochMs
                    )
                    val saveRes = tradingRepository.saveSignal(currentUserId, record)
                    savedId = saveRes.getOrNull()
                }

                val finalPresentation = presentation.copy(signalId = savedId)
                _activeSignal.value = finalPresentation

                if (_voiceAlertsEnabled.value || forceSpeak) {
                    onSpeakUrdu(finalPresentation.urduSpeech)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "analyzeNow failed", e)
                _errorMessage.value = e.localizedMessage ?: "Market analysis failed."
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun submitVoiceOrTextCommand(
        rawCommand: String,
        fromVoice: Boolean = false,
        onSpeakUrdu: (String) -> Unit
    ) {
        val trimmed = rawCommand.trim()
        if (trimmed.isBlank()) return
        _lastCommandFeedback.value = "Command: \"$trimmed\""

        when (val parsed = MnShahVoiceEngine.parseCommand(trimmed)) {
            is ParsedVoiceCommand.RequestSignal -> {
                if (parsed.asset != null && parsed.asset != _selectedAsset.value) {
                    _selectedAsset.value = parsed.asset
                }
                analyzeNow(
                    userCommand = trimmed,
                    forceSpeak = true,
                    onSpeakUrdu = onSpeakUrdu
                )
            }
            is ParsedVoiceCommand.SwitchAsset -> {
                selectAsset(parsed.asset)
                val speech = "${parsed.asset.symbol} کا لائیو چارٹ لوڈ کیا جا رہا ہے۔"
                _assistantReply.value = AssistantChatTurn(
                    userQuery = trimmed,
                    urduSpeech = speech,
                    romanSummary = "${parsed.asset.symbol} ka live chart load kiya ja raha hai.",
                    timestampMs = System.currentTimeMillis()
                )
                if (_voiceAlertsEnabled.value || fromVoice) {
                    onSpeakUrdu(speech)
                }
            }
            is ParsedVoiceCommand.GeneralQuestion -> {
                viewModelScope.launch {
                    _isAnalyzing.value = true
                    _errorMessage.value = null
                    try {
                        val snap = _marketSnapshot.value ?: refreshMarketSnapshot()
                        val rep = _technicalReport.value ?: withContext(Dispatchers.Default) {
                            TechnicalAnalysisEngine.analyzeSnapshot(snap)
                        }
                        val result = aiAnalyst.answerGeneralCommand(trimmed, snap, rep)
                        if (result.isSuccess) {
                            val (urdu, roman) = result.getOrThrow()
                            _assistantReply.value = AssistantChatTurn(
                                userQuery = trimmed,
                                urduSpeech = urdu,
                                romanSummary = roman,
                                timestampMs = System.currentTimeMillis()
                            )
                            if (_voiceAlertsEnabled.value || fromVoice) {
                                onSpeakUrdu(urdu)
                            }
                        } else {
                            val err = result.exceptionOrNull()
                            if (err is DailyQuotaExhaustedException) {
                                _assistantReply.value = AssistantChatTurn(
                                    userQuery = trimmed,
                                    urduSpeech = err.alert.urduMessage,
                                    romanSummary = err.alert.romanUrduMessage,
                                    timestampMs = System.currentTimeMillis()
                                )
                                if (_voiceAlertsEnabled.value || fromVoice) {
                                    onSpeakUrdu(err.alert.urduMessage)
                                }
                            } else {
                                _errorMessage.value = err?.localizedMessage
                                    ?: "Assistant could not answer right now."
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } finally {
                        _isAnalyzing.value = false
                    }
                }
            }
        }
    }

    fun confirmManualTrade(
        symbol: String,
        direction: String,
        stakeUsd: Double,
        brokerName: String,
        notes: String
    ) {
        viewModelScope.launch {
            _tradeActionMessage.value = null
            val res = tradingRepository.confirmManualTrade(
                userId = currentUserId,
                signalId = _activeSignal.value?.signalId,
                symbol = symbol,
                direction = direction,
                stakeUsd = stakeUsd,
                brokerName = brokerName,
                notes = notes
            )
            _tradeActionMessage.value = if (res.isSuccess) {
                "Manual real-trade confirmation logged to secure audit ledger."
            } else {
                res.exceptionOrNull()?.localizedMessage ?: "Failed to record trade confirmation."
            }
        }
    }

    fun cancelTradeConfirmation(tradeId: String) {
        viewModelScope.launch {
            val res = tradingRepository.cancelTradeConfirmation(
                userId = currentUserId,
                tradeId = tradeId,
                notes = "Cancelled by trader"
            )
            if (res.isFailure) {
                _tradeActionMessage.value = res.exceptionOrNull()?.localizedMessage ?: "Cancel failed."
            }
        }
    }

    fun deleteSignalRecord(signalId: String) {
        viewModelScope.launch {
            tradingRepository.deleteSignal(currentUserId, signalId)
        }
    }

    fun clearTradeMessage() {
        _tradeActionMessage.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun buildFallbackUrdu(
        asset: TradingAsset,
        report: TechnicalAnalysisReport,
        quotaExhausted: Boolean = false
    ): String {
        val core = when (report.quantitativeDirection) {
            SignalDirection.UP ->
                "${asset.symbol} پر اگلی ایک منٹ کی کینڈل میں اوپر جانے کا امکان ہے، لیکن براہِ کرم شواہد کی تصدیق کریں۔"
            SignalDirection.DOWN ->
                "${asset.symbol} پر اگلی ایک منٹ کی کینڈل میں نیچے دباؤ نظر آ رہا ہے، احتیاط سے جائزہ لیں۔"
            SignalDirection.UNCERTAIN ->
                "${asset.symbol} کی صورتحال غیر واضح ہے، فی الحال انتظار کرنا بہتر ہے۔"
            SignalDirection.WAIT ->
                "${asset.symbol} پر شواہد ناکافی ہیں یا ڈیٹا تاخیر کا شکار ہے، ابھی انتظار کریں۔"
        }
        return if (quotaExhausted) {
            "اے آئی کا یومیہ کوٹہ مکمل ہے، لیکن لائیو ٹیکنیکل انڈیکیٹرز کے مطابق $core"
        } else {
            core
        }
    }

    override fun onCleared() {
        super.onCleared()
        monitorJob?.cancel()
    }

    private companion object {
        const val TAG = "MnShahViewModel"
    }
}
