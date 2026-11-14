package com.example.data.ai

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

interface AiQuotaStore {
    suspend fun load(): AiUsageStats
    suspend fun save(stats: AiUsageStats)
}

class InMemoryAiQuotaStore(
    initial: AiUsageStats = AiUsageStats()
) : AiQuotaStore {
    private var current = initial
    override suspend fun load(): AiUsageStats = current
    override suspend fun save(stats: AiUsageStats) {
        current = stats
    }
}

private val Context.aiQuotaDataStore by preferencesDataStore(name = "mn_shah_ai_quota_prefs")

class DataStoreAiQuotaStore(private val context: Context) : AiQuotaStore {
    override suspend fun load(): AiUsageStats {
        val prefs = context.aiQuotaDataStore.data.first()
        val provider = runCatching {
            ActiveAiProvider.valueOf(prefs[KEY_ACTIVE_PROVIDER] ?: ActiveAiProvider.GEMINI.name)
        }.getOrDefault(ActiveAiProvider.GEMINI)
        return AiUsageStats(
            dateUtc = prefs[KEY_DATE_UTC] ?: "",
            requestsToday = prefs[KEY_REQUESTS_TODAY] ?: 0,
            successfulResponsesToday = prefs[KEY_SUCCESS_TODAY] ?: 0,
            temporaryRetriesToday = prefs[KEY_RETRIES_TODAY] ?: 0,
            quotaErrorsToday = prefs[KEY_QUOTA_ERRORS_TODAY] ?: 0,
            backupCallsToday = prefs[KEY_BACKUP_CALLS_TODAY] ?: 0,
            backupQuotaExhaustedToday = prefs[KEY_BACKUP_EXHAUSTED] ?: false,
            cooldownUntilEpochMs = prefs[KEY_COOLDOWN_UNTIL_MS] ?: 0L,
            activeProvider = provider,
            lastErrorSummary = prefs[KEY_LAST_ERROR],
            lastUpdatedEpochMs = prefs[KEY_LAST_UPDATED_MS] ?: 0L
        )
    }

    override suspend fun save(stats: AiUsageStats) {
        context.aiQuotaDataStore.edit { prefs ->
            prefs[KEY_DATE_UTC] = stats.dateUtc
            prefs[KEY_REQUESTS_TODAY] = stats.requestsToday
            prefs[KEY_SUCCESS_TODAY] = stats.successfulResponsesToday
            prefs[KEY_RETRIES_TODAY] = stats.temporaryRetriesToday
            prefs[KEY_QUOTA_ERRORS_TODAY] = stats.quotaErrorsToday
            prefs[KEY_BACKUP_CALLS_TODAY] = stats.backupCallsToday
            prefs[KEY_BACKUP_EXHAUSTED] = stats.backupQuotaExhaustedToday
            prefs[KEY_COOLDOWN_UNTIL_MS] = stats.cooldownUntilEpochMs
            prefs[KEY_ACTIVE_PROVIDER] = stats.activeProvider.name
            if (stats.lastErrorSummary != null) {
                prefs[KEY_LAST_ERROR] = stats.lastErrorSummary
            } else {
                prefs.remove(KEY_LAST_ERROR)
            }
            prefs[KEY_LAST_UPDATED_MS] = stats.lastUpdatedEpochMs
        }
    }

    private companion object {
        val KEY_DATE_UTC = stringPreferencesKey("date_utc")
        val KEY_REQUESTS_TODAY = intPreferencesKey("requests_today")
        val KEY_SUCCESS_TODAY = intPreferencesKey("success_today")
        val KEY_RETRIES_TODAY = intPreferencesKey("retries_today")
        val KEY_QUOTA_ERRORS_TODAY = intPreferencesKey("quota_errors_today")
        val KEY_BACKUP_CALLS_TODAY = intPreferencesKey("backup_calls_today")
        val KEY_BACKUP_EXHAUSTED = booleanPreferencesKey("backup_exhausted")
        val KEY_COOLDOWN_UNTIL_MS = longPreferencesKey("cooldown_until_ms")
        val KEY_ACTIVE_PROVIDER = stringPreferencesKey("active_provider")
        val KEY_LAST_ERROR = stringPreferencesKey("last_error")
        val KEY_LAST_UPDATED_MS = longPreferencesKey("last_updated_ms")
    }
}

object AiErrorClassifier {
    fun classify(throwable: Throwable): AiErrorKind {
        val chain = generateSequence(throwable) { it.cause }.toList()
        val combinedText = chain.joinToString(" | ") { t ->
            "${t::class.java.simpleName} ${t.message.orEmpty()}"
        }.lowercase(Locale.ROOT)

        val isQuotaClass = chain.any { t ->
            t::class.java.simpleName.contains("QuotaExceeded", ignoreCase = true)
        }
        val hasResourceExhausted = combinedText.contains("resource_exhausted") ||
            combinedText.contains("resource exhausted")
        val hasDailyOrHardQuota = combinedText.contains("daily") ||
            combinedText.contains("perday") ||
            combinedText.contains("per_day") ||
            combinedText.contains("generaterequestsperday") ||
            combinedText.contains("exceeded your current quota") ||
            combinedText.contains("quota exceeded") ||
            combinedText.contains("quota exhausted") ||
            combinedText.contains("limit: 0") ||
            combinedText.contains("billing")
        val hasShortRateLimitCue = combinedText.contains("per minute") ||
            combinedText.contains("per_minute") ||
            combinedText.contains("rpm") ||
            combinedText.contains("tpm")

        if (isQuotaClass || hasDailyOrHardQuota || (hasResourceExhausted && !hasShortRateLimitCue)) {
            return AiErrorKind.DAILY_QUOTA_EXHAUSTED
        }

        if (combinedText.contains("429") ||
            combinedText.contains("rate limit") ||
            combinedText.contains("too many requests") ||
            hasResourceExhausted
        ) {
            return AiErrorKind.TEMPORARY_RATE_LIMIT
        }

        val isOfflineException = chain.any { t ->
            t is UnknownHostException || t is ConnectException || t is NoRouteToHostException
        }
        if (isOfflineException ||
            combinedText.contains("unable to resolve host") ||
            combinedText.contains("network is unreachable") ||
            combinedText.contains("failed to connect") ||
            combinedText.contains("no internet")
        ) {
            return AiErrorKind.OFFLINE_NETWORK
        }

        val isTimeoutOrTransientIo = chain.any { t ->
            t is TimeoutCancellationException || t is SocketTimeoutException || t is InterruptedIOException
        }
        if (isTimeoutOrTransientIo ||
            combinedText.contains("500") ||
            combinedText.contains("502") ||
            combinedText.contains("503") ||
            combinedText.contains("504") ||
            combinedText.contains("unavailable") ||
            combinedText.contains("overloaded") ||
            combinedText.contains("timed out") ||
            combinedText.contains("timeout") ||
            combinedText.contains("internal error")
        ) {
            return AiErrorKind.TRANSIENT_NETWORK_OR_SERVER
        }

        if (combinedText.contains("401") ||
            combinedText.contains("403") ||
            combinedText.contains("app check") ||
            combinedText.contains("permission_denied") ||
            combinedText.contains("unauthorized") ||
            combinedText.contains("api key")
        ) {
            return AiErrorKind.AUTH_OR_CONFIG
        }

        return AiErrorKind.UNKNOWN
    }
}

class AiQuotaTracker(
    private val store: AiQuotaStore = InMemoryAiQuotaStore(),
    private val backupProviderConfigured: () -> Boolean = { false },
    private val backupDailyLimit: Int = 50,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val delayFn: suspend (Long) -> Unit = { ms -> delay(ms) },
    private val maxTransientAttempts: Int = 3,
    private val baseBackoffMs: Long = 1_000L
) {
    private val mutex = Mutex()
    private var initialized = false

    private val _connectionStatus = MutableStateFlow(AiConnectionStatus.ONLINE)
    val connectionStatus: StateFlow<AiConnectionStatus> = _connectionStatus.asStateFlow()

    private val _usageStats = MutableStateFlow(
        AiUsageStats(
            backupConfigured = backupProviderConfigured(),
            backupQuotaLimit = backupDailyLimit
        )
    )
    val usageStats: StateFlow<AiUsageStats> = _usageStats.asStateFlow()

    private val _quotaAlert = MutableStateFlow<AiQuotaAlert?>(null)
    val quotaAlert: StateFlow<AiQuotaAlert?> = _quotaAlert.asStateFlow()

    suspend fun syncInitialState() {
        mutex.withLock {
            ensureInitializedLocked(clockMs())
        }
    }

    suspend fun resetCooldownForManualRecheck() {
        mutex.withLock {
            val now = clockMs()
            val current = ensureInitializedLocked(now)
            val updated = current.copy(
                cooldownUntilEpochMs = 0L,
                lastErrorSummary = null,
                activeProvider = ActiveAiProvider.GEMINI,
                lastUpdatedEpochMs = now
            )
            persistLocked(updated)
            _connectionStatus.value = AiConnectionStatus.ONLINE
            _quotaAlert.value = null
        }
    }

    suspend fun <T> executeWithSmartQuota(
        primaryCall: suspend () -> T,
        backupCall: (suspend () -> T)? = null
    ): Result<Pair<T, ActiveAiProvider>> {
        val nowStart = clockMs()
        val initialStats = mutex.withLock {
            val base = ensureInitializedLocked(nowStart)
            base
        }

        // Circuit breaker: NEVER repeatedly retry or call Gemini when daily quota is exhausted
        if (initialStats.isQuotaCooldownActive(nowStart)) {
            val backupAttempt = tryBackupIfEligible(
                currentStats = initialStats,
                backupCall = backupCall,
                errorSummaryForPrimary = initialStats.lastErrorSummary ?: "HTTP 429 RESOURCE_EXHAUSTED"
            )
            if (backupAttempt != null) return backupAttempt

            val alert = buildUrduQuotaAlert(
                stats = _usageStats.value,
                nowEpochMs = nowStart,
                errorCodeLabel = initialStats.lastErrorSummary ?: "HTTP 429 RESOURCE_EXHAUSTED"
            )
            mutex.withLock {
                _connectionStatus.value = AiConnectionStatus.QUOTA_EXCEEDED
                _quotaAlert.value = alert
            }
            return Result.failure(DailyQuotaExhaustedException(alert))
        }

        mutex.withLock {
            val cur = ensureInitializedLocked(nowStart)
            persistLocked(
                cur.copy(
                    requestsToday = cur.requestsToday + 1,
                    lastUpdatedEpochMs = nowStart
                )
            )
        }

        var lastError: Throwable? = null
        for (attempt in 1..maxTransientAttempts) {
            try {
                val value = primaryCall()
                val nowSuccess = clockMs()
                mutex.withLock {
                    val cur = ensureInitializedLocked(nowSuccess)
                    val updated = cur.copy(
                        successfulResponsesToday = cur.successfulResponsesToday + 1,
                        cooldownUntilEpochMs = 0L,
                        activeProvider = ActiveAiProvider.GEMINI,
                        lastErrorSummary = null,
                        lastUpdatedEpochMs = nowSuccess
                    )
                    persistLocked(updated)
                    _connectionStatus.value = AiConnectionStatus.ONLINE
                    _quotaAlert.value = null
                }
                return Result.success(value to ActiveAiProvider.GEMINI)
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                lastError = e
                val kind = AiErrorKind.TRANSIENT_NETWORK_OR_SERVER
                if (!handleRetryOrFail(attempt, e, kind)) break
            } catch (e: Exception) {
                lastError = e
                val kind = AiErrorClassifier.classify(e)
                if (kind == AiErrorKind.DAILY_QUOTA_EXHAUSTED) {
                    val nowQuota = clockMs()
                    val cooldownUntil = nextUtcMidnightEpochMs(nowQuota)
                    val codeLabel = "HTTP 429 RESOURCE_EXHAUSTED"
                    val updatedStats = mutex.withLock {
                        val cur = ensureInitializedLocked(nowQuota)
                        val updated = cur.copy(
                            quotaErrorsToday = cur.quotaErrorsToday + 1,
                            cooldownUntilEpochMs = cooldownUntil,
                            activeProvider = ActiveAiProvider.QUANTITATIVE_ONLY,
                            lastErrorSummary = codeLabel,
                            lastUpdatedEpochMs = nowQuota
                        )
                        persistLocked(updated)
                        updated
                    }

                    // Try optional backup provider ONLY if valid API key and quota exist
                    val backupResult = tryBackupIfEligible(
                        currentStats = updatedStats,
                        backupCall = backupCall,
                        errorSummaryForPrimary = codeLabel
                    )
                    if (backupResult != null) return backupResult

                    val alert = buildUrduQuotaAlert(updatedStats, nowQuota, codeLabel)
                    mutex.withLock {
                        _connectionStatus.value = AiConnectionStatus.QUOTA_EXCEEDED
                        _quotaAlert.value = alert
                    }
                    return Result.failure(DailyQuotaExhaustedException(alert, e))
                }

                if (!handleRetryOrFail(attempt, e, kind)) {
                    break
                }
            }
        }

        // Transient retries exhausted or non-retryable failure (e.g., offline / auth / persistent RPM limit)
        val failure = lastError ?: IllegalStateException("AI request failed.")
        val finalKind = AiErrorClassifier.classify(failure)
        val nowEnd = clockMs()
        val statsBeforeBackup = mutex.withLock {
            val cur = ensureInitializedLocked(nowEnd)
            val errSummary = when (finalKind) {
                AiErrorKind.TEMPORARY_RATE_LIMIT -> "HTTP 429 Rate Limit"
                AiErrorKind.OFFLINE_NETWORK -> "Network Offline"
                AiErrorKind.TRANSIENT_NETWORK_OR_SERVER -> "Service Timeout / 5xx"
                AiErrorKind.AUTH_OR_CONFIG -> "App Check / Auth Error"
                else -> failure.localizedMessage?.take(64) ?: "AI Unavailable"
            }
            val updated = cur.copy(
                quotaErrorsToday = cur.quotaErrorsToday + if (finalKind == AiErrorKind.TEMPORARY_RATE_LIMIT) 1 else 0,
                activeProvider = ActiveAiProvider.QUANTITATIVE_ONLY,
                lastErrorSummary = errSummary,
                lastUpdatedEpochMs = nowEnd
            )
            persistLocked(updated)
            updated
        }

        val backupResult = tryBackupIfEligible(
            currentStats = statsBeforeBackup,
            backupCall = backupCall,
            errorSummaryForPrimary = statsBeforeBackup.lastErrorSummary ?: "Primary AI Unavailable"
        )
        if (backupResult != null) return backupResult

        mutex.withLock {
            _connectionStatus.value = if (finalKind == AiErrorKind.TEMPORARY_RATE_LIMIT) {
                AiConnectionStatus.QUOTA_EXCEEDED
            } else {
                AiConnectionStatus.OFFLINE
            }
            if (finalKind == AiErrorKind.TEMPORARY_RATE_LIMIT) {
                _quotaAlert.value = buildUrduQuotaAlert(
                    stats = _usageStats.value,
                    nowEpochMs = nowEnd,
                    errorCodeLabel = "HTTP 429 Temporary Rate Limit"
                )
            }
        }
        return Result.failure(failure)
    }

    private suspend fun handleRetryOrFail(
        attempt: Int,
        throwable: Throwable,
        kind: AiErrorKind
    ): Boolean {
        val retryable = kind == AiErrorKind.TEMPORARY_RATE_LIMIT ||
            kind == AiErrorKind.TRANSIENT_NETWORK_OR_SERVER
        if (!retryable || attempt >= maxTransientAttempts) {
            return false
        }
        val backoffMs = baseBackoffMs * (1L shl (attempt - 1))
        val nowRetry = clockMs()
        mutex.withLock {
            val cur = ensureInitializedLocked(nowRetry)
            persistLocked(
                cur.copy(
                    temporaryRetriesToday = cur.temporaryRetriesToday + 1,
                    lastErrorSummary = "Retry $attempt/${maxTransientAttempts - 1} (${kind.name})",
                    lastUpdatedEpochMs = nowRetry
                )
            )
        }
        delayFn(backoffMs)
        return true
    }

    private suspend fun <T> tryBackupIfEligible(
        currentStats: AiUsageStats,
        backupCall: (suspend () -> T)?,
        errorSummaryForPrimary: String
    ): Result<Pair<T, ActiveAiProvider>>? {
        if (backupCall == null) return null
        if (!backupProviderConfigured()) return null
        if (currentStats.backupQuotaExhaustedToday) return null
        if (currentStats.backupCallsToday >= backupDailyLimit) return null

        return try {
            val backupResult = backupCall()
            val nowBackup = clockMs()
            mutex.withLock {
                val cur = ensureInitializedLocked(nowBackup)
                val updated = cur.copy(
                    requestsToday = if (cur.isQuotaCooldownActive(nowBackup)) cur.requestsToday + 1 else cur.requestsToday,
                    successfulResponsesToday = cur.successfulResponsesToday + 1,
                    backupCallsToday = cur.backupCallsToday + 1,
                    activeProvider = ActiveAiProvider.BACKUP,
                    lastErrorSummary = "Primary: $errorSummaryForPrimary -> Backup Active",
                    lastUpdatedEpochMs = nowBackup
                )
                persistLocked(updated)
                _connectionStatus.value = AiConnectionStatus.ONLINE
                _quotaAlert.value = null
            }
            Result.success(backupResult to ActiveAiProvider.BACKUP)
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
            null
        } catch (e: Exception) {
            val backupKind = AiErrorClassifier.classify(e)
            val nowFail = clockMs()
            mutex.withLock {
                val cur = ensureInitializedLocked(nowFail)
                val backupExhausted = cur.backupQuotaExhaustedToday ||
                    backupKind == AiErrorKind.DAILY_QUOTA_EXHAUSTED ||
                    backupKind == AiErrorKind.TEMPORARY_RATE_LIMIT
                persistLocked(
                    cur.copy(
                        backupQuotaExhaustedToday = backupExhausted,
                        activeProvider = ActiveAiProvider.QUANTITATIVE_ONLY,
                        lastErrorSummary = "$errorSummaryForPrimary (Backup failed)",
                        lastUpdatedEpochMs = nowFail
                    )
                )
            }
            null
        }
    }

    private suspend fun ensureInitializedLocked(nowEpochMs: Long): AiUsageStats {
        val loaded = if (!initialized) {
            initialized = true
            store.load()
        } else {
            _usageStats.value
        }

        val todayUtc = formatUtcDate(nowEpochMs)
        val normalized = if (loaded.dateUtc != todayUtc) {
            AiUsageStats(
                dateUtc = todayUtc,
                requestsToday = 0,
                successfulResponsesToday = 0,
                temporaryRetriesToday = 0,
                quotaErrorsToday = 0,
                backupCallsToday = 0,
                backupQuotaLimit = backupDailyLimit,
                backupConfigured = backupProviderConfigured(),
                backupQuotaExhaustedToday = false,
                cooldownUntilEpochMs = 0L,
                activeProvider = ActiveAiProvider.GEMINI,
                lastErrorSummary = null,
                lastUpdatedEpochMs = nowEpochMs
            ).also { store.save(it) }
        } else {
            loaded.copy(
                backupConfigured = backupProviderConfigured(),
                backupQuotaLimit = backupDailyLimit
            )
        }

        _usageStats.value = normalized
        if (normalized.isQuotaCooldownActive(nowEpochMs)) {
            _connectionStatus.value = AiConnectionStatus.QUOTA_EXCEEDED
            _quotaAlert.value = buildUrduQuotaAlert(
                stats = normalized,
                nowEpochMs = nowEpochMs,
                errorCodeLabel = normalized.lastErrorSummary ?: "HTTP 429 RESOURCE_EXHAUSTED"
            )
        }
        return normalized
    }

    private suspend fun persistLocked(stats: AiUsageStats) {
        val withConfig = stats.copy(
            backupConfigured = backupProviderConfigured(),
            backupQuotaLimit = backupDailyLimit
        )
        _usageStats.value = withConfig
        store.save(withConfig)
    }

    private fun buildUrduQuotaAlert(
        stats: AiUsageStats,
        nowEpochMs: Long,
        errorCodeLabel: String
    ): AiQuotaAlert {
        val remaining = stats.formatCooldownRemaining(nowEpochMs)
        val backupNote = when {
            !stats.backupConfigured -> "Backup AI auto-disabled (Primary Gemini + Live Indicators active)."
            stats.backupQuotaExhaustedToday || stats.backupCallsToday >= stats.backupQuotaLimit ->
                "Backup AI provider ka daily quota bhi mukammal ho chuka hai."
            else -> "Backup AI provider available hai."
        }
        return AiQuotaAlert(
            urduTitle = "روزانہ اے آئی کوٹہ مکمل ہو چکا ہے",
            urduMessage = "جیمنائی اے آئی کا یومیہ کوٹہ عارضی طور پر ختم ہو گیا ہے۔ سسٹم خودکار دوبارہ کوشش نہیں کرے گا تاکہ اضافی ناکام کالز نہ ہوں۔ جب تک کوٹہ بحال نہ ہو، ایم این شاہ کے لائیو 1M، 5M اور 15M ٹیکنیکل انڈیکیٹرز بدستور کام کر رہے ہیں۔",
            romanUrduMessage = "Gemini AI ka rozana quota mukammal ho gaya hai. System auto-retry nahi karega; live 1M/5M/15M technical signals aur Urdu voice fallback jaari hain.",
            cooldownRemainingText = remaining,
            errorCodeLabel = errorCodeLabel,
            backupProviderNote = backupNote
        )
    }

    private fun formatUtcDate(epochMs: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return sdf.format(Date(epochMs))
    }

    private fun nextUtcMidnightEpochMs(epochMs: Long): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = epochMs
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}
