package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.market.Candle
import com.example.data.market.FeedStatus
import com.example.data.market.TradingAsset
import com.example.domain.analysis.TimeframeIndicators
import com.example.ui.theme.BearishCrimson
import com.example.ui.theme.BullishEmerald
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.NeonYellow
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun CandlestickChart(
    asset: TradingAsset,
    candles: List<Candle>,
    indicators: TimeframeIndicators?,
    feedStatus: FeedStatus,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visibleCandles = remember(candles) { candles.takeLast(55) }
    var selectedCandle by remember(visibleCandles) { mutableStateOf<Candle?>(null) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CarbonSurface)
            .border(1.dp, SlateBorder, RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        val inspected = selectedCandle ?: visibleCandles.lastOrNull()
        if (inspected != null) {
            val dec = asset.decimals
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${timeFormat.format(Date(inspected.timestampMs))}  " +
                        "O:${"%.${dec}f".format(inspected.open)}  " +
                        "H:${"%.${dec}f".format(inspected.high)}  " +
                        "L:${"%.${dec}f".format(inspected.low)}  " +
                        "C:${"%.${dec}f".format(inspected.close)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (inspected.isBullish) BullishEmerald else BearishCrimson
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianVoid),
            contentAlignment = Alignment.Center
        ) {
            if (visibleCandles.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SignalWifiOff,
                        contentDescription = "Feed Disconnected",
                        tint = BearishCrimson
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (feedStatus) {
                            FeedStatus.Connecting -> "Connecting to live ${asset.symbol} candlestick stream..."
                            is FeedStatus.Delayed -> feedStatus.reason
                            is FeedStatus.Disconnected -> "Disconnected: ${feedStatus.reason}"
                            is FeedStatus.Live -> "Waiting for candle data..."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMuted
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onReconnect,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonYellow,
                            contentColor = ObsidianVoid
                        ),
                        modifier = Modifier.testTag("chart_reconnect_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.padding(4.dp))
                        Text("RECONNECT", style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else {
                val ema9Visible = remember(indicators, visibleCandles.size) {
                    indicators?.ema9Series?.takeLast(visibleCandles.size) ?: emptyList()
                }
                val ema21Visible = remember(indicators, visibleCandles.size) {
                    indicators?.ema21Series?.takeLast(visibleCandles.size) ?: emptyList()
                }
                val (lowBound, priceRange) = remember(visibleCandles) {
                    val minPrice = visibleCandles.minOf { it.low }
                    val maxPrice = visibleCandles.maxOf { it.high }
                    val pad = (maxPrice - minPrice).coerceAtLeast(1e-6) * 0.08
                    val low = minPrice - pad
                    val high = maxPrice + pad
                    low to (high - low).coerceAtLeast(1e-6)
                }
                val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f) }
                val ema9Stroke = remember { Stroke(width = 2.2f) }
                val ema21Stroke = remember { Stroke(width = 2.0f) }
                val ema9Path = remember { Path() }
                val ema21Path = remember { Path() }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                        .pointerInput(visibleCandles) {
                            detectTapGestures { offset ->
                                val step = size.width.toFloat() / visibleCandles.size.coerceAtLeast(1)
                                val idx = (offset.x / step).roundToInt()
                                    .coerceIn(0, visibleCandles.lastIndex)
                                selectedCandle = visibleCandles[idx]
                            }
                        }
                ) {
                    fun priceToY(price: Double): Float {
                        val norm = ((price - lowBound) / priceRange).toFloat()
                        return size.height * (1f - norm)
                    }

                    // Horizontal subtle grid lines
                    val gridColor = SlateBorder.copy(alpha = 0.45f)
                    for (g in 1..3) {
                        val y = size.height * (g / 4f)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1f
                        )
                    }

                    // Support & Resistance dashed lines
                    indicators?.supportResistance?.let { sr ->
                        val supY = priceToY(sr.nearestSupport)
                        val resY = priceToY(sr.nearestResistance)
                        if (supY in 0f..size.height) {
                            drawLine(
                                color = BullishEmerald.copy(alpha = 0.55f),
                                start = Offset(0f, supY),
                                end = Offset(size.width, supY),
                                strokeWidth = 1.5f,
                                pathEffect = dashEffect
                            )
                        }
                        if (resY in 0f..size.height) {
                            drawLine(
                                color = BearishCrimson.copy(alpha = 0.55f),
                                start = Offset(0f, resY),
                                end = Offset(size.width, resY),
                                strokeWidth = 1.5f,
                                pathEffect = dashEffect
                            )
                        }
                    }

                    val step = size.width / visibleCandles.size.toFloat()
                    val bodyWidth = (step * 0.62f).coerceIn(3f, 18f)

                    visibleCandles.forEachIndexed { index, candle ->
                        val centerX = index * step + step / 2f
                        val candleColor = if (candle.isBullish) BullishEmerald else BearishCrimson

                        val yHigh = priceToY(candle.high)
                        val yLow = priceToY(candle.low)
                        val yOpen = priceToY(candle.open)
                        val yClose = priceToY(candle.close)

                        drawLine(
                            color = candleColor,
                            start = Offset(centerX, yHigh),
                            end = Offset(centerX, yLow),
                            strokeWidth = 1.6f
                        )

                        val top = min(yOpen, yClose)
                        val bottom = max(yOpen, yClose)
                        val bodyH = (bottom - top).coerceAtLeast(2.2f)
                        drawRect(
                            color = candleColor,
                            topLeft = Offset(centerX - bodyWidth / 2f, top),
                            size = Size(bodyWidth, bodyH)
                        )

                        if (selectedCandle == candle) {
                            drawLine(
                                color = NeonYellow.copy(alpha = 0.55f),
                                start = Offset(centerX, 0f),
                                end = Offset(centerX, size.height),
                                strokeWidth = 1.2f
                            )
                        }
                    }

                    // EMA 9 (Neon Yellow) & EMA 21 (Cyber Cyan)
                    if (ema9Visible.size == visibleCandles.size && ema9Visible.size > 1) {
                        ema9Path.reset()
                        ema9Visible.forEachIndexed { i, v ->
                            val x = i * step + step / 2f
                            val y = priceToY(v)
                            if (i == 0) ema9Path.moveTo(x, y) else ema9Path.lineTo(x, y)
                        }
                        drawPath(ema9Path, color = NeonYellow, style = ema9Stroke)
                    }
                    if (ema21Visible.size == visibleCandles.size && ema21Visible.size > 1) {
                        ema21Path.reset()
                        ema21Visible.forEachIndexed { i, v ->
                            val x = i * step + step / 2f
                            val y = priceToY(v)
                            if (i == 0) ema21Path.moveTo(x, y) else ema21Path.lineTo(x, y)
                        }
                        drawPath(ema21Path, color = CyberCyan, style = ema21Stroke)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("— EMA 9", style = MaterialTheme.typography.labelSmall, color = NeonYellow)
            Text("— EMA 21", style = MaterialTheme.typography.labelSmall, color = CyberCyan)
            Text("-- Support", style = MaterialTheme.typography.labelSmall, color = BullishEmerald)
            Text("-- Resistance", style = MaterialTheme.typography.labelSmall, color = BearishCrimson)
        }
    }
}
