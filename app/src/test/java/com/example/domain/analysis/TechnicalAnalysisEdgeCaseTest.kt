package com.example.domain.analysis

import com.example.data.market.Candle
import com.example.data.market.FeedStatus
import com.example.data.market.MultiTimeframeMarketSnapshot
import com.example.data.market.TradingAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TechnicalAnalysisEdgeCaseTest {

    @Test
    fun analyzeSnapshot_delayedBeyondTenMinutes_returnsWait() {
        val now = System.currentTimeMillis()
        val candles = (0 until 35).map { i ->
            Candle(
                timestampMs = now - (40 - i) * 60_000L,
                open = 1.2700 + i * 0.0001,
                high = 1.2705 + i * 0.0001,
                low = 1.2698 + i * 0.0001,
                close = 1.2702 + i * 0.0001,
                volume = 100L
            )
        }
        val snapshot = MultiTimeframeMarketSnapshot(
            asset = TradingAsset.GBP_USD,
            currentPrice = candles.last().close,
            previousClose = 1.2700,
            candles1m = candles,
            candles5m = candles,
            candles15m = candles,
            status = FeedStatus.Delayed("Yahoo Finance", "CLOSED", 900L, "Weekend close"),
            fetchedAtEpochMs = now,
            candleExpiryEpochMs = now + 60_000L
        )

        val report = TechnicalAnalysisEngine.analyzeSnapshot(snapshot)
        assertEquals(SignalDirection.WAIT, report.quantitativeDirection)
        assertTrue(report.insufficiencyReason?.contains("delayed", ignoreCase = true) == true)
    }

    @Test
    fun detectCandlePattern_dojiBar_detectedAccurately() {
        val now = System.currentTimeMillis()
        val candles = listOf(
            Candle(now - 120_000L, 1.0800, 1.0810, 1.0795, 1.0808, 10L),
            Candle(now - 60_000L, 1.0808, 1.0830, 1.0780, 1.0809, 10L)
        )
        val pattern = TechnicalAnalysisEngine.detectCandlePattern(candles)
        assertTrue(pattern.contains("Doji", ignoreCase = true))
    }
}
