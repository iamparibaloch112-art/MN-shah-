package com.example.data.ai

import com.example.data.market.Candle
import com.example.data.market.FeedStatus
import com.example.data.market.MultiTimeframeMarketSnapshot
import com.example.data.market.Timeframe
import com.example.data.market.TradingAsset
import com.example.domain.analysis.TechnicalAnalysisEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.UnknownHostException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiQuotaTrackerTest {

    @Test
    fun classify_429ResourceExhausted_detectsDailyQuotaExhausted() {
        val err = RuntimeException("HTTP 429 RESOURCE_EXHAUSTED: GenerateRequestsPerDay exceeded")
        assertEquals(AiErrorKind.DAILY_QUOTA_EXHAUSTED, AiErrorClassifier.classify(err))

        val plainResourceExhausted = IllegalStateException("RESOURCE_EXHAUSTED (429)")
        assertEquals(AiErrorKind.DAILY_QUOTA_EXHAUSTED, AiErrorClassifier.classify(plainResourceExhausted))

        val rpmRateLimit = IOException("HTTP 429 rate limit exceeded per minute (RPM)")
        assertEquals(AiErrorKind.TEMPORARY_RATE_LIMIT, AiErrorClassifier.classify(rpmRateLimit))

        val server503 = IOException("HTTP 503 Service Unavailable")
        assertEquals(AiErrorKind.TRANSIENT_NETWORK_OR_SERVER, AiErrorClassifier.classify(server503))

        val offline = UnknownHostException("Unable to resolve host generativelanguage.googleapis.com")
        assertEquals(AiErrorKind.OFFLINE_NETWORK, AiErrorClassifier.classify(offline))
    }

    @Test
    fun dailyQuotaExhausted_neverRepeatedlyRetries_andTripsCircuitBreaker() = runTest {
        var nowMs = 1_700_000_000_000L
        val recordedDelays = mutableListOf<Long>()
        val tracker = AiQuotaTracker(
            store = InMemoryAiQuotaStore(),
            backupProviderConfigured = { false },
            clockMs = { nowMs },
            delayFn = { recordedDelays.add(it) }
        )

        var primaryAttempts = 0
        val firstResult = tracker.executeWithSmartQuota<String>(
            primaryCall = {
                primaryAttempts++
                throw RuntimeException("429 RESOURCE_EXHAUSTED: daily quota exceeded")
            }
        )

        assertTrue(firstResult.isFailure)
        assertTrue(firstResult.exceptionOrNull() is DailyQuotaExhaustedException)
        assertEquals(1, primaryAttempts)
        assertTrue("Daily quota exhaustion must never retry", recordedDelays.isEmpty())
        assertEquals(AiConnectionStatus.QUOTA_EXCEEDED, tracker.connectionStatus.value)
        assertNotNull(tracker.quotaAlert.value)
        assertTrue(tracker.quotaAlert.value!!.urduMessage.contains("کوٹہ"))
        assertEquals(1, tracker.usageStats.value.quotaErrorsToday)

        // Second call within cooldown window must short-circuit with ZERO primary calls
        nowMs += 60_000L
        val secondResult = tracker.executeWithSmartQuota<String>(
            primaryCall = {
                primaryAttempts++
                "Should not be reached"
            }
        )
        assertTrue(secondResult.isFailure)
        assertEquals(1, primaryAttempts)
        assertTrue(recordedDelays.isEmpty())
    }

    @Test
    fun temporaryErrors_retryWithExponentialBackoff_andRecoverOnline() = runTest {
        val recordedDelays = mutableListOf<Long>()
        val tracker = AiQuotaTracker(
            store = InMemoryAiQuotaStore(),
            backupProviderConfigured = { false },
            clockMs = { 1_700_000_000_000L },
            delayFn = { recordedDelays.add(it) },
            maxTransientAttempts = 3,
            baseBackoffMs = 1_000L
        )

        var attempts = 0
        val res = tracker.executeWithSmartQuota(
            primaryCall = {
                attempts++
                if (attempts < 3) {
                    throw IOException("HTTP 503 Service Unavailable")
                }
                "OK"
            }
        )

        assertTrue(res.isSuccess)
        assertEquals("OK" to ActiveAiProvider.GEMINI, res.getOrThrow())
        assertEquals(3, attempts)
        assertEquals(listOf(1_000L, 2_000L), recordedDelays)
        assertEquals(2, tracker.usageStats.value.temporaryRetriesToday)
        assertEquals(1, tracker.usageStats.value.successfulResponsesToday)
        assertEquals(AiConnectionStatus.ONLINE, tracker.connectionStatus.value)
        assertNull(tracker.quotaAlert.value)
    }

    @Test
    fun offlineNetworkError_setsOfflineStatusWithoutQuotaAlert() = runTest {
        val recordedDelays = mutableListOf<Long>()
        val tracker = AiQuotaTracker(
            store = InMemoryAiQuotaStore(),
            backupProviderConfigured = { false },
            clockMs = { 1_700_000_000_000L },
            delayFn = { recordedDelays.add(it) }
        )

        val res = tracker.executeWithSmartQuota<String>(
            primaryCall = {
                throw UnknownHostException("No route to host")
            }
        )

        assertTrue(res.isFailure)
        assertTrue(recordedDelays.isEmpty())
        assertEquals(AiConnectionStatus.OFFLINE, tracker.connectionStatus.value)
        assertNull(tracker.quotaAlert.value)
    }

    @Test
    fun backupProvider_invokedOnlyWhenValidKeyAndQuotaAvailable() = runTest {
        // Case 1: unconfigured backup key -> backup must NEVER be invoked
        var backupCalls = 0
        val unconfiguredTracker = AiQuotaTracker(
            store = InMemoryAiQuotaStore(),
            backupProviderConfigured = { false },
            backupDailyLimit = 2,
            clockMs = { 1_700_000_000_000L },
            delayFn = {}
        )
        val unconfiguredRes = unconfiguredTracker.executeWithSmartQuota(
            primaryCall = { throw RuntimeException("HTTP 429 RESOURCE_EXHAUSTED") },
            backupCall = {
                backupCalls++
                "Backup reply"
            }
        )
        assertTrue(unconfiguredRes.isFailure)
        assertEquals(0, backupCalls)

        // Case 2: configured backup with daily limit = 1 -> 1st succeeds via backup, 2nd stops at quota limit
        val configuredTracker = AiQuotaTracker(
            store = InMemoryAiQuotaStore(),
            backupProviderConfigured = { true },
            backupDailyLimit = 1,
            clockMs = { 1_700_000_000_000L },
            delayFn = {}
        )
        val firstBackup = configuredTracker.executeWithSmartQuota(
            primaryCall = { throw RuntimeException("HTTP 429 RESOURCE_EXHAUSTED") },
            backupCall = {
                backupCalls++
                "Backup signal"
            }
        )
        assertTrue(firstBackup.isSuccess)
        assertEquals("Backup signal" to ActiveAiProvider.BACKUP, firstBackup.getOrThrow())
        assertEquals(1, backupCalls)
        assertEquals(1, configuredTracker.usageStats.value.backupCallsToday)

        val secondBackup = configuredTracker.executeWithSmartQuota(
            primaryCall = { "Ignored due to Gemini cooldown" },
            backupCall = {
                backupCalls++
                "Should not run when backup quota limit reached"
            }
        )
        assertTrue(secondBackup.isFailure)
        assertEquals(1, backupCalls)
        assertEquals(AiConnectionStatus.QUOTA_EXCEEDED, configuredTracker.connectionStatus.value)
    }

    @Test
    fun utcDateRollover_resetsDailyCountersAndClearsCooldown() = runTest {
        var nowMs = 1_700_000_000_000L // 2023-11-14 UTC
        val tracker = AiQuotaTracker(
            store = InMemoryAiQuotaStore(),
            backupProviderConfigured = { false },
            clockMs = { nowMs },
            delayFn = {}
        )

        tracker.executeWithSmartQuota<String>(
            primaryCall = { throw RuntimeException("429 RESOURCE_EXHAUSTED") }
        )
        assertEquals(AiConnectionStatus.QUOTA_EXCEEDED, tracker.connectionStatus.value)
        assertEquals(1, tracker.usageStats.value.requestsToday)

        // Advance +25 hours to next UTC day
        nowMs += 25 * 60 * 60 * 1000L
        val nextDayRes = tracker.executeWithSmartQuota(primaryCall = { "Fresh Day" })
        assertTrue(nextDayRes.isSuccess)
        assertEquals(1, tracker.usageStats.value.requestsToday)
        assertEquals(0, tracker.usageStats.value.quotaErrorsToday)
        assertEquals(AiConnectionStatus.ONLINE, tracker.connectionStatus.value)
    }

    @Test
    fun okHttpBackupProvider_rejectsPlaceholderOrBlankKeys() {
        val placeholderProvider = OkHttpBackupAiProvider(
            apiKey = "YOUR_BACKUP_AI_API_KEY",
            baseUrl = "https://api.openai.com/v1/chat/completions",
            modelName = "gpt-4o-mini"
        )
        assertFalse(placeholderProvider.isConfigured())

        val blankProvider = OkHttpBackupAiProvider(
            apiKey = "",
            baseUrl = "https://api.openai.com/v1/chat/completions",
            modelName = "gpt-4o-mini"
        )
        assertFalse(blankProvider.isConfigured())

        val insecureUrlProvider = OkHttpBackupAiProvider(
            apiKey = "sk-valid-test-token-123456",
            baseUrl = "http://insecure.example.com",
            modelName = "gpt-4o-mini"
        )
        assertFalse(insecureUrlProvider.isConfigured())

        val defaultProvider = OkHttpBackupAiProvider()
        assertFalse(defaultProvider.isConfigured())

        val validProvider = OkHttpBackupAiProvider(
            apiKey = "sk-valid-test-token-123456",
            baseUrl = "https://api.openai.com/v1/chat/completions",
            modelName = "gpt-4o-mini"
        )
        assertTrue(validProvider.isConfigured())
    }

    @Test
    fun mnShahAiAnalyst_handles429ResourceExhausted_withoutRetries_andUsesBackupWhenConfigured() = runTest {
        val now = 1_700_000_000_000L
        val candles = (0 until 30).map { i ->
            Candle(
                timestampMs = now - (30 - i) * 60_000L,
                open = 1.0800 + i * 0.0001,
                high = 1.0803 + i * 0.0001,
                low = 1.0797 + i * 0.0001,
                close = 1.0802 + i * 0.0001,
                volume = 100L
            )
        }
        val snapshot = MultiTimeframeMarketSnapshot(
            asset = TradingAsset.EUR_USD,
            currentPrice = 1.0832,
            previousClose = 1.0800,
            candles1m = candles,
            candles5m = candles,
            candles15m = candles,
            status = FeedStatus.Live("Yahoo Finance", "REGULAR", now),
            fetchedAtEpochMs = now,
            candleExpiryEpochMs = now + 45_000L
        )
        val report = TechnicalAnalysisEngine.analyzeSnapshot(snapshot)

        val fakeBackup = object : BackupAiProvider {
            override fun isConfigured(): Boolean = true
            override fun dailyQuotaLimit(): Int = 10
            override suspend fun generateJsonCompletion(systemPrompt: String, userPrompt: String): String {
                return """
                    {
                      "direction": "UP",
                      "confidence": 68,
                      "urduSpeech": "بیک اپ اے آئی کے مطابق یورو یو ایس ڈی میں اوپر کا رجحان ہے۔",
                      "romanUrduSummary": "Backup AI ke mutabiq EUR/USD mein UP trend hai.",
                      "evidencePoints": ["RSI bullish", "EMA 9 above EMA 21", "MACD positive"],
                      "riskNote": "No guaranteed accuracy."
                    }
                """.trimIndent()
            }

            override suspend fun generateTextCompletion(systemPrompt: String, userPrompt: String): String =
                "آن لائن || Online"
        }

        val tracker = AiQuotaTracker(
            store = InMemoryAiQuotaStore(),
            backupProviderConfigured = { fakeBackup.isConfigured() },
            backupDailyLimit = fakeBackup.dailyQuotaLimit(),
            clockMs = { now },
            delayFn = {}
        )
        var geminiCalls = 0
        val analyst = MnShahAiAnalyst(
            backupProvider = fakeBackup,
            quotaTracker = tracker,
            primarySignalCaller = {
                geminiCalls++
                throw RuntimeException("HTTP 429 RESOURCE_EXHAUSTED")
            }
        )

        val briefingRes = analyst.generateSignalBriefing(snapshot, report, "MN SHAH mujhe signal do")
        assertTrue(briefingRes.isSuccess)
        val briefing = briefingRes.getOrThrow()
        assertEquals(1, geminiCalls)
        assertEquals(ActiveAiProvider.BACKUP, briefing.providerUsed)
        assertEquals(1, analyst.usageStats.value.backupCallsToday)
    }
}
