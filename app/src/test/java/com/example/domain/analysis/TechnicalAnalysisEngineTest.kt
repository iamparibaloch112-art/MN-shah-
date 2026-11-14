package com.example.domain.analysis

import com.example.data.market.Candle
import com.example.data.market.FeedStatus
import com.example.data.market.MultiTimeframeMarketSnapshot
import com.example.data.market.TradingAsset
import com.example.voice.MnShahVoiceEngine
import com.example.voice.ParsedVoiceCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TechnicalAnalysisEngineTest {

    private fun buildTrendCandles(
        count: Int,
        startPrice: Double,
        step: Double
    ): List<Candle> {
        val now = System.currentTimeMillis()
        return (0 until count).map { i ->
            val o = startPrice + i * step
            val c = o + step * 0.8
            val h = maxOf(o, c) + kotlin.math.abs(step) * 0.25
            val l = minOf(o, c) - kotlin.math.abs(step) * 0.15
            Candle(
                timestampMs = now - (count - i) * 60_000L,
                open = o,
                high = h,
                low = l,
                close = c,
                volume = 100L
            )
        }
    }

    @Test
    fun analyzeSnapshot_disconnectedFeed_returnsWait() {
        val snapshot = MultiTimeframeMarketSnapshot(
            asset = TradingAsset.EUR_USD,
            currentPrice = 0.0,
            previousClose = null,
            candles1m = emptyList(),
            candles5m = emptyList(),
            candles15m = emptyList(),
            status = FeedStatus.Disconnected("Network offline", System.currentTimeMillis()),
            fetchedAtEpochMs = System.currentTimeMillis(),
            candleExpiryEpochMs = System.currentTimeMillis() + 60_000L
        )
        val report = TechnicalAnalysisEngine.analyzeSnapshot(snapshot)
        assertEquals(SignalDirection.WAIT, report.quantitativeDirection)
        assertEquals(0, report.confidenceEstimate)
    }

    @Test
    fun analyzeSnapshot_strongUptrendAcrossTimeframes_returnsUpWithEvidence() {
        val c1 = buildTrendCandles(40, 1.0800, 0.0002)
        val c5 = buildTrendCandles(40, 1.0760, 0.0003)
        val c15 = buildTrendCandles(40, 1.0700, 0.0004)

        val snapshot = MultiTimeframeMarketSnapshot(
            asset = TradingAsset.EUR_USD,
            currentPrice = c1.last().close,
            previousClose = 1.0790,
            candles1m = c1,
            candles5m = c5,
            candles15m = c15,
            status = FeedStatus.Live("Yahoo Finance", "REGULAR", System.currentTimeMillis()),
            fetchedAtEpochMs = System.currentTimeMillis(),
            candleExpiryEpochMs = System.currentTimeMillis() + 60_000L
        )

        val report = TechnicalAnalysisEngine.analyzeSnapshot(snapshot)
        assertEquals(SignalDirection.UP, report.quantitativeDirection)
        assertTrue(report.confidenceEstimate >= 55)
        assertTrue(report.technicalEvidence.isNotEmpty())
    }

    @Test
    fun analyzeSnapshot_strongDowntrendAcrossTimeframes_returnsDown() {
        val c1 = buildTrendCandles(40, 2750.0, -0.6)
        val c5 = buildTrendCandles(40, 2765.0, -0.8)
        val c15 = buildTrendCandles(40, 2780.0, -1.0)

        val snapshot = MultiTimeframeMarketSnapshot(
            asset = TradingAsset.XAU_USD,
            currentPrice = c1.last().close,
            previousClose = 2760.0,
            candles1m = c1,
            candles5m = c5,
            candles15m = c15,
            status = FeedStatus.Live("Yahoo Finance", "REGULAR", System.currentTimeMillis()),
            fetchedAtEpochMs = System.currentTimeMillis(),
            candleExpiryEpochMs = System.currentTimeMillis() + 60_000L
        )

        val report = TechnicalAnalysisEngine.analyzeSnapshot(snapshot)
        assertEquals(SignalDirection.DOWN, report.quantitativeDirection)
        assertTrue(report.confidenceEstimate >= 55)
    }

    @Test
    fun parseVoiceCommand_mnShahMujheSignalDo_requestsSignal() {
        val parsed = MnShahVoiceEngine.parseCommand("MN SHAH mujhe signal do")
        assertTrue(parsed is ParsedVoiceCommand.RequestSignal)
    }

    @Test
    fun parseVoiceCommand_goldSignalRequest_detectsXauUsd() {
        val parsed = MnShahVoiceEngine.parseCommand("MN SHAH gold ka signal do")
        assertTrue(parsed is ParsedVoiceCommand.RequestSignal)
        assertEquals(TradingAsset.XAU_USD, (parsed as ParsedVoiceCommand.RequestSignal).asset)
    }

    @Test
    fun parseVoiceCommand_newOtcMarkets_detectsNzdNgnAndAxie() {
        val nzd = MnShahVoiceEngine.parseCommand("NZD USD OTC signal do")
        assertTrue(nzd is ParsedVoiceCommand.RequestSignal)
        assertEquals(TradingAsset.NZD_USD, (nzd as ParsedVoiceCommand.RequestSignal).asset)

        val ngn = MnShahVoiceEngine.parseCommand("USD NGN OTC signal do")
        assertTrue(ngn is ParsedVoiceCommand.RequestSignal)
        assertEquals(TradingAsset.USD_NGN, (ngn as ParsedVoiceCommand.RequestSignal).asset)

        val axie = MnShahVoiceEngine.parseCommand("Axie Infinity OTC signal do")
        assertTrue(axie is ParsedVoiceCommand.RequestSignal)
        assertEquals(TradingAsset.AXS_USD, (axie as ParsedVoiceCommand.RequestSignal).asset)

        assertEquals(TradingAsset.NZD_USD, TradingAsset.fromSymbol("NZD/USD (OTC)"))
        assertEquals(TradingAsset.USD_NGN, TradingAsset.fromSymbol("USD/NGN (OTC)"))
        assertEquals(TradingAsset.AXS_USD, TradingAsset.fromSymbol("Axie Infinity (OTC)"))
    }
}
