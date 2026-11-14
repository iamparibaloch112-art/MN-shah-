package com.example.domain.analysis

import com.example.data.market.Candle
import com.example.data.market.FeedStatus
import com.example.data.market.MultiTimeframeMarketSnapshot
import com.example.data.market.Timeframe
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class SignalDirection {
    UP, DOWN, UNCERTAIN, WAIT
}

enum class TrendBias {
    BULLISH, BEARISH, NEUTRAL
}

data class MacdResult(
    val macdLine: Double,
    val signalLine: Double,
    val histogram: Double,
    val statusLabel: String,
    val bias: TrendBias
)

data class SupportResistanceLevels(
    val nearestSupport: Double,
    val nearestResistance: Double,
    val distanceToSupportPct: Double,
    val distanceToResistancePct: Double
)

data class TimeframeIndicators(
    val timeframe: Timeframe,
    val candleCount: Int,
    val lastClose: Double,
    val rsi14: Double,
    val ema9: Double,
    val ema21: Double,
    val ema50: Double,
    val ema9Series: List<Double>,
    val ema21Series: List<Double>,
    val emaBias: TrendBias,
    val macd: MacdResult,
    val supportResistance: SupportResistanceLevels,
    val candlePattern: String,
    val marketStructure: String,
    val biasScore: Int
)

data class TechnicalAnalysisReport(
    val m1: TimeframeIndicators?,
    val m5: TimeframeIndicators?,
    val m15: TimeframeIndicators?,
    val quantitativeDirection: SignalDirection,
    val confidenceEstimate: Int,
    val totalScore: Int,
    val insufficiencyReason: String?,
    val technicalEvidence: List<String>
)

object TechnicalAnalysisEngine {

    fun analyzeSnapshot(snapshot: MultiTimeframeMarketSnapshot): TechnicalAnalysisReport {
        val m1 = analyzeTimeframe(Timeframe.M1, snapshot.candles1m)
        val m5 = analyzeTimeframe(Timeframe.M5, snapshot.candles5m)
        val m15 = analyzeTimeframe(Timeframe.M15, snapshot.candles15m)

        val evidence = mutableListOf<String>()
        if (snapshot.status is FeedStatus.Disconnected) {
            return TechnicalAnalysisReport(
                m1 = m1,
                m5 = m5,
                m15 = m15,
                quantitativeDirection = SignalDirection.WAIT,
                confidenceEstimate = 0,
                totalScore = 0,
                insufficiencyReason = "Live market feed is disconnected: ${snapshot.status.reason}",
                technicalEvidence = listOf("Feed disconnected — waiting for live candle reconnection.")
            )
        }

        if (m1 == null || m1.candleCount < 26) {
            return TechnicalAnalysisReport(
                m1 = m1,
                m5 = m5,
                m15 = m15,
                quantitativeDirection = SignalDirection.WAIT,
                confidenceEstimate = 15,
                totalScore = 0,
                insufficiencyReason = "Insufficient 1-minute candle history (${m1?.candleCount ?: 0}/26 required).",
                technicalEvidence = listOf("Need at least 26 live 1m candles for reliable RSI, MACD & EMA calculation.")
            )
        }

        val feedDelayedBeyondWindow = snapshot.status is FeedStatus.Delayed && snapshot.status.delaySeconds > 600L
        if (feedDelayedBeyondWindow) {
            val delayed = snapshot.status as FeedStatus.Delayed
            evidence.add("Feed delayed by ${delayed.delaySeconds}s (${delayed.marketState}). Next 1m prediction suspended.")
            return TechnicalAnalysisReport(
                m1 = m1,
                m5 = m5,
                m15 = m15,
                quantitativeDirection = SignalDirection.WAIT,
                confidenceEstimate = 20,
                totalScore = 0,
                insufficiencyReason = "Market feed delayed/closed (${delayed.marketState}). Waiting for live ticks.",
                technicalEvidence = evidence
            )
        }

        // Weighted multi-timeframe confluence: 1m (weight 2), 5m (weight 2), 15m (weight 1)
        val score1m = m1.biasScore * 2
        val score5m = (m5?.biasScore ?: 0) * 2
        val score15m = (m15?.biasScore ?: 0) * 1
        val totalScore = score1m + score5m + score15m

        evidence.add("1M RSI(14): ${"%.1f".format(m1.rsi14)} | MACD: ${m1.macd.statusLabel} | EMA: ${m1.emaBias.name}")
        evidence.add("1M Pattern: ${m1.candlePattern} | Structure: ${m1.marketStructure}")
        if (m5 != null) {
            evidence.add("5M Trend: ${m5.emaBias.name} (RSI ${"%.1f".format(m5.rsi14)}, MACD ${m5.macd.statusLabel})")
        }
        if (m15 != null) {
            evidence.add("15M Context: ${m15.emaBias.name} (${m15.marketStructure})")
        }
        val dec = snapshot.asset.decimals
        evidence.add(
            "Key Levels (1M): S=${"%.${dec}f".format(m1.supportResistance.nearestSupport)} | " +
                "R=${"%.${dec}f".format(m1.supportResistance.nearestResistance)}"
        )

        val timeframeConflict = m5 != null && ((m1.biasScore > 0 && m5.biasScore < -1) || (m1.biasScore < 0 && m5.biasScore > 1))
        val neutralRsi = m1.rsi14 in 46.5..53.5
        val dojiIndecision = m1.candlePattern.contains("Doji", ignoreCase = true)

        val direction: SignalDirection
        var reason: String? = null
        val confidence: Int

        when {
            abs(totalScore) < 4 || (timeframeConflict && abs(totalScore) < 7) -> {
                direction = SignalDirection.WAIT
                reason = if (timeframeConflict) {
                    "1M and 5M timeframe momentum conflict — waiting for cleaner alignment."
                } else {
                    "Confluence score ($totalScore) too weak for next 1m candle call."
                }
                confidence = (35 + abs(totalScore) * 3).coerceIn(20, 49)
            }
            dojiIndecision && neutralRsi && abs(totalScore) < 7 -> {
                direction = SignalDirection.UNCERTAIN
                reason = "Indecision candle (Doji) with neutral RSI (${ "%.1f".format(m1.rsi14) })."
                confidence = 48
            }
            totalScore >= 4 -> {
                direction = SignalDirection.UP
                confidence = (54 + (totalScore * 2.8).toInt()).coerceIn(55, 86)
            }
            else -> {
                direction = SignalDirection.DOWN
                confidence = (54 + (abs(totalScore) * 2.8).toInt()).coerceIn(55, 86)
            }
        }

        return TechnicalAnalysisReport(
            m1 = m1,
            m5 = m5,
            m15 = m15,
            quantitativeDirection = direction,
            confidenceEstimate = confidence,
            totalScore = totalScore,
            insufficiencyReason = reason,
            technicalEvidence = evidence
        )
    }

    fun analyzeTimeframe(timeframe: Timeframe, candles: List<Candle>): TimeframeIndicators? {
        if (candles.size < 15) return null
        val closes = candles.map { it.close }
        val rsi = calculateRsi(closes, 14)
        val ema9Series = calculateEmaSeries(closes, 9)
        val ema21Series = calculateEmaSeries(closes, 21)
        val ema50Series = calculateEmaSeries(closes, min(50, closes.size))

        val ema9 = ema9Series.last()
        val ema21 = ema21Series.last()
        val ema50 = ema50Series.last()
        val lastClose = closes.last()

        val emaBias = when {
            lastClose > ema9 && ema9 > ema21 && ema21 >= ema50 -> TrendBias.BULLISH
            lastClose < ema9 && ema9 < ema21 && ema21 <= ema50 -> TrendBias.BEARISH
            ema9 > ema21 -> TrendBias.BULLISH
            ema9 < ema21 -> TrendBias.BEARISH
            else -> TrendBias.NEUTRAL
        }

        val macd = calculateMacd(closes)
        val sr = detectSupportResistance(candles)
        val pattern = detectCandlePattern(candles)
        val structure = detectMarketStructure(candles)

        var score = 0
        when (emaBias) {
            TrendBias.BULLISH -> score += 2
            TrendBias.BEARISH -> score -= 2
            TrendBias.NEUTRAL -> Unit
        }
        when (macd.bias) {
            TrendBias.BULLISH -> score += 1
            TrendBias.BEARISH -> score -= 1
            TrendBias.NEUTRAL -> Unit
        }
        if (macd.statusLabel.contains("Bullish Crossover")) score += 1
        if (macd.statusLabel.contains("Bearish Crossover")) score -= 1

        when {
            rsi <= 32.0 -> score += 2 // Oversold bounce potential
            rsi in 53.0..68.0 -> score += 1 // Bullish momentum without exhaustion
            rsi >= 68.0 -> score -= 2 // Overbought exhaustion risk
            rsi in 32.0..47.0 -> score -= 1 // Bearish momentum
        }

        when {
            pattern.contains("Bullish") || pattern.contains("Hammer") -> score += 1
            pattern.contains("Bearish") || pattern.contains("Shooting Star") -> score -= 1
        }
        when {
            structure.contains("HH/HL") -> score += 1
            structure.contains("LH/LL") -> score -= 1
        }

        return TimeframeIndicators(
            timeframe = timeframe,
            candleCount = candles.size,
            lastClose = lastClose,
            rsi14 = rsi,
            ema9 = ema9,
            ema21 = ema21,
            ema50 = ema50,
            ema9Series = ema9Series,
            ema21Series = ema21Series,
            emaBias = emaBias,
            macd = macd,
            supportResistance = sr,
            candlePattern = pattern,
            marketStructure = structure,
            biasScore = score
        )
    }

    fun calculateRsi(closes: List<Double>, period: Int = 14): Double {
        if (closes.size <= period) return 50.0
        var gainSum = 0.0
        var lossSum = 0.0
        for (i in 1..period) {
            val change = closes[i] - closes[i - 1]
            if (change >= 0) gainSum += change else lossSum += abs(change)
        }
        var avgGain = gainSum / period
        var avgLoss = lossSum / period

        for (i in (period + 1) until closes.size) {
            val change = closes[i] - closes[i - 1]
            val gain = if (change > 0) change else 0.0
            val loss = if (change < 0) abs(change) else 0.0
            avgGain = ((avgGain * (period - 1)) + gain) / period
            avgLoss = ((avgLoss * (period - 1)) + loss) / period
        }

        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    fun calculateEmaSeries(values: List<Double>, period: Int): List<Double> {
        if (values.isEmpty()) return emptyList()
        val effectivePeriod = period.coerceAtLeast(1)
        val k = 2.0 / (effectivePeriod + 1.0)
        val result = ArrayList<Double>(values.size)
        var ema = values.first()
        result.add(ema)
        for (i in 1 until values.size) {
            ema = values[i] * k + ema * (1.0 - k)
            result.add(ema)
        }
        return result
    }

    fun calculateMacd(closes: List<Double>): MacdResult {
        if (closes.size < 26) {
            return MacdResult(0.0, 0.0, 0.0, "Insufficient Data", TrendBias.NEUTRAL)
        }
        val ema12 = calculateEmaSeries(closes, 12)
        val ema26 = calculateEmaSeries(closes, 26)
        val macdSeries = closes.indices.map { ema12[it] - ema26[it] }
        val signalSeries = calculateEmaSeries(macdSeries, 9)

        val lastMacd = macdSeries.last()
        val lastSignal = signalSeries.last()
        val prevMacd = macdSeries[macdSeries.size - 2]
        val prevSignal = signalSeries[signalSeries.size - 2]
        val hist = lastMacd - lastSignal

        val label = when {
            prevMacd <= prevSignal && lastMacd > lastSignal -> "Bullish Crossover"
            prevMacd >= prevSignal && lastMacd < lastSignal -> "Bearish Crossover"
            hist > 0 -> "Bullish Momentum"
            hist < 0 -> "Bearish Momentum"
            else -> "Flat / Neutral"
        }
        val bias = when {
            hist > 0 -> TrendBias.BULLISH
            hist < 0 -> TrendBias.BEARISH
            else -> TrendBias.NEUTRAL
        }
        return MacdResult(lastMacd, lastSignal, hist, label, bias)
    }

    fun detectSupportResistance(candles: List<Candle>): SupportResistanceLevels {
        val window = candles.takeLast(40)
        val current = window.last().close
        val low = window.minOf { it.low }
        val high = window.maxOf { it.high }

        val swingLows = mutableListOf<Double>()
        val swingHighs = mutableListOf<Double>()
        for (i in 1 until window.size - 1) {
            if (window[i].low < window[i - 1].low && window[i].low < window[i + 1].low) {
                swingLows.add(window[i].low)
            }
            if (window[i].high > window[i - 1].high && window[i].high > window[i + 1].high) {
                swingHighs.add(window[i].high)
            }
        }

        val nearestSupport = swingLows.filter { it <= current }.maxOrNull() ?: low
        val nearestResistance = swingHighs.filter { it >= current }.minOrNull() ?: high

        val distSup = if (current > 0) ((current - nearestSupport) / current) * 100.0 else 0.0
        val distRes = if (current > 0) ((nearestResistance - current) / current) * 100.0 else 0.0
        return SupportResistanceLevels(nearestSupport, nearestResistance, distSup, distRes)
    }

    fun detectCandlePattern(candles: List<Candle>): String {
        if (candles.size < 2) return "Single Bar"
        val prev = candles[candles.size - 2]
        val curr = candles.last()

        val body = abs(curr.close - curr.open)
        val range = (curr.high - curr.low).coerceAtLeast(1e-9)
        val upperWick = curr.high - max(curr.open, curr.close)
        val lowerWick = min(curr.open, curr.close) - curr.low
        val prevBody = abs(prev.close - prev.open)

        return when {
            body / range < 0.12 -> "Doji (Indecision)"
            !prev.isBullish && curr.isBullish && curr.close > prev.open && curr.open <= prev.close && body > prevBody ->
                "Bullish Engulfing"
            prev.isBullish && !curr.isBullish && curr.close < prev.open && curr.open >= prev.close && body > prevBody ->
                "Bearish Engulfing"
            lowerWick > body * 2.0 && upperWick < body * 0.6 ->
                "Hammer / Bullish Rejection"
            upperWick > body * 2.0 && lowerWick < body * 0.6 ->
                "Shooting Star / Bearish Rejection"
            curr.isBullish && body / range > 0.65 ->
                "Bullish Momentum Bar"
            !curr.isBullish && body / range > 0.65 ->
                "Bearish Momentum Bar"
            else -> if (curr.isBullish) "Mild Bullish Bar" else "Mild Bearish Bar"
        }
    }

    fun detectMarketStructure(candles: List<Candle>): String {
        val recent = candles.takeLast(18)
        if (recent.size < 12) return "Consolidation"
        val firstHalf = recent.subList(0, recent.size / 2)
        val secondHalf = recent.subList(recent.size / 2, recent.size)

        val h1 = firstHalf.maxOf { it.high }
        val l1 = firstHalf.minOf { it.low }
        val h2 = secondHalf.maxOf { it.high }
        val l2 = secondHalf.minOf { it.low }

        return when {
            h2 > h1 && l2 > l1 -> "HH/HL Uptrend"
            h2 < h1 && l2 < l1 -> "LH/LL Downtrend"
            else -> "Range / Sideways"
        }
    }
}
