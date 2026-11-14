package com.example.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.data.market.Candle
import com.example.data.market.FeedStatus
import com.example.data.market.TradingAsset
import com.example.domain.analysis.SignalDirection
import com.example.domain.analysis.TechnicalAnalysisEngine
import com.example.data.market.Timeframe
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithText
import com.example.data.ai.ActiveAiProvider
import com.example.data.ai.AiConnectionStatus
import com.example.data.ai.AiQuotaAlert
import com.example.data.ai.AiUsageStats
import com.example.ui.components.AiConnectionStatusBadge
import com.example.ui.components.AiQuotaExhaustedBanner
import com.example.ui.components.AiUsageDashboardCard
import com.example.ui.components.AnimatedRobotFace
import com.example.ui.components.CandlestickChart
import com.example.ui.components.ManualTradeSheet
import com.example.ui.theme.MnShahTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MnShahComponentsRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun candlestickChart_disconnectedState_displaysReconnectButton() {
        var reconnected = false
        composeTestRule.setContent {
            MnShahTheme {
                CandlestickChart(
                    asset = TradingAsset.EUR_USD,
                    candles = emptyList(),
                    indicators = null,
                    feedStatus = FeedStatus.Disconnected("Timeout", System.currentTimeMillis()),
                    onReconnect = { reconnected = true }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chart_reconnect_button").assertIsDisplayed().performClick()
        assertTrue(reconnected)
    }

    @Test
    fun candlestickChart_withLiveCandles_rendersWithoutCrash() {
        val now = System.currentTimeMillis()
        val candles = (0 until 35).map { i ->
            Candle(
                timestampMs = now - (35 - i) * 60_000L,
                open = 1.0800 + i * 0.0001,
                high = 1.0805 + i * 0.0001,
                low = 1.0798 + i * 0.0001,
                close = 1.0802 + i * 0.0001,
                volume = 50L
            )
        }
        val indicators = TechnicalAnalysisEngine.analyzeTimeframe(Timeframe.M1, candles)

        composeTestRule.setContent {
            MnShahTheme {
                CandlestickChart(
                    asset = TradingAsset.EUR_USD,
                    candles = candles,
                    indicators = indicators,
                    feedStatus = FeedStatus.Live("Yahoo Finance", "REGULAR", now),
                    onReconnect = {}
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun manualTradeSheet_requiresExplicitAuthorizationCheckboxBeforeConfirm() {
        var confirmed = false
        composeTestRule.setContent {
            MnShahTheme {
                ManualTradeSheet(
                    asset = TradingAsset.XAU_USD,
                    initialDirection = "UP",
                    entryPrice = 2680.50,
                    onDismiss = {},
                    onConfirmManualTrade = { _, _, _, _, _ -> confirmed = true }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("confirm_manual_trade_button").performScrollTo().assertIsNotEnabled()
        composeTestRule.onNodeWithTag("manual_trade_notes_input").performScrollTo().performTextInput("Verified S/R bounce")
        composeTestRule.onNodeWithTag("manual_trade_authorize_checkbox").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("confirm_manual_trade_button").performScrollTo().assertIsEnabled().performClick()
        assertTrue(confirmed)
    }

    @Test
    fun aiConnectionStatusBadge_rendersQuotaExceededLabel() {
        composeTestRule.setContent {
            MnShahTheme {
                AiConnectionStatusBadge(status = AiConnectionStatus.QUOTA_EXCEEDED)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ai_connection_status_badge")
            .assertIsDisplayed()
            .assertTextContains("AI: QUOTA EXCEEDED", substring = true)
    }

    @Test
    fun aiQuotaExhaustedBanner_displaysUrduNoticeAndCallbacks() {
        var spokenText = ""
        var rechecked = false
        val alert = AiQuotaAlert(
            urduTitle = "روزانہ اے آئی کوٹہ مکمل ہو چکا ہے",
            urduMessage = "جیمنائی اے آئی کا یومیہ کوٹہ عارضی طور پر ختم ہو گیا ہے۔",
            romanUrduMessage = "Gemini AI ka rozana quota mukammal ho gaya hai.",
            cooldownRemainingText = "8h 15m",
            errorCodeLabel = "HTTP 429 RESOURCE_EXHAUSTED",
            backupProviderNote = "Backup AI key configured nahi hai."
        )

        composeTestRule.setContent {
            MnShahTheme {
                AiQuotaExhaustedBanner(
                    alert = alert,
                    onSpeakUrdu = { spokenText = it },
                    onRecheckAi = { rechecked = true },
                    isChecking = false
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("ai_quota_exhausted_banner").assertIsDisplayed()
        composeTestRule.onNodeWithTag("speak_quota_urdu_button").performClick()
        assertEquals(alert.urduMessage, spokenText)
        composeTestRule.onNodeWithTag("quota_banner_recheck_button").performClick()
        assertTrue(rechecked)
    }

    @Test
    fun aiUsageDashboardCard_displaysMetricsAndTriggersRecheck() {
        var rechecked = false
        val stats = AiUsageStats(
            dateUtc = "2026-11-13",
            requestsToday = 14,
            successfulResponsesToday = 11,
            temporaryRetriesToday = 2,
            quotaErrorsToday = 1,
            backupCallsToday = 0,
            backupQuotaLimit = 50,
            backupConfigured = false,
            activeProvider = ActiveAiProvider.GEMINI,
            lastErrorSummary = "HTTP 429 RESOURCE_EXHAUSTED"
        )

        composeTestRule.setContent {
            MnShahTheme {
                AiUsageDashboardCard(
                    status = AiConnectionStatus.QUOTA_EXCEEDED,
                    stats = stats,
                    isChecking = false,
                    onRecheckAi = { rechecked = true },
                    initiallyExpanded = true
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("ai_usage_dashboard").assertIsDisplayed()
        composeTestRule.onNodeWithTag("backup_provider_status_text")
            .assertIsDisplayed()
            .assertTextContains("DISABLED (AUTO)", substring = true)
        composeTestRule.onNodeWithTag("recheck_ai_connection_button").performClick()
        assertTrue(rechecked)
    }

    @Test
    fun animatedRobotFace_idleState_rendersAndReachesIdleImmediately() {
        composeTestRule.setContent {
            MnShahTheme {
                AnimatedRobotFace(
                    isAnalyzing = false,
                    isSpeaking = false,
                    isListening = false,
                    direction = SignalDirection.UP,
                    statusLabel = "FEED LIVE • MONITORING"
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("FEED LIVE • MONITORING").assertIsDisplayed()
    }
}
