package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.market.FeedStatus
import com.example.data.market.Timeframe
import com.example.data.market.TradingAsset
import com.example.domain.analysis.TimeframeIndicators
import com.example.domain.analysis.TrendBias
import com.example.ui.components.CandlestickChart
import com.example.ui.theme.BearishCrimson
import com.example.ui.theme.BullishEmerald
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberPanel
import com.example.ui.theme.ElectricAmber
import com.example.ui.theme.NeonYellow
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MnShahViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MarketChartScreen(
    viewModel: MnShahViewModel,
    modifier: Modifier = Modifier
) {
    val selectedAsset by viewModel.selectedAsset.collectAsStateWithLifecycle()
    val selectedTimeframe by viewModel.selectedTimeframe.collectAsStateWithLifecycle()
    val snapshot by viewModel.marketSnapshot.collectAsStateWithLifecycle()
    val report by viewModel.technicalReport.collectAsStateWithLifecycle()

    val timeFormat = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()) }

    val candles = snapshot?.candlesFor(selectedTimeframe).orEmpty()
    val activeIndicators = when (selectedTimeframe) {
        Timeframe.M1 -> report?.m1
        Timeframe.M5 -> report?.m5
        Timeframe.M15 -> report?.m15
    }
    val feedStatus = snapshot?.status ?: FeedStatus.Connecting

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "LIVE CANDLESTICK TERMINAL",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonYellow
                    )
                    val fetchedStr = snapshot?.fetchedAtEpochMs?.let {
                        "Updated ${timeFormat.format(Date(it))}"
                    } ?: "Awaiting first market poll"
                    Text(
                        text = fetchedStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }
                IconButton(
                    onClick = { viewModel.reconnectMarketFeed() },
                    modifier = Modifier.testTag("market_refresh_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Market Candles",
                        tint = NeonYellow
                    )
                }
            }

            // Asset chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TradingAsset.entries.forEach { asset ->
                    val selected = asset == selectedAsset
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.selectAsset(asset) },
                        label = { Text(asset.symbol, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonYellow,
                            selectedLabelColor = ObsidianVoid,
                            containerColor = CarbonSurface,
                            labelColor = TextPrimary
                        ),
                        modifier = Modifier.testTag("market_asset_chip_${asset.name.lowercase()}")
                    )
                }
            }

            // Timeframe chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Timeframe.entries.forEach { tf ->
                    val selected = tf == selectedTimeframe
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.selectTimeframe(tf) },
                        label = { Text(tf.label, style = MaterialTheme.typography.labelLarge) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyberCyan,
                            selectedLabelColor = ObsidianVoid,
                            containerColor = CarbonSurface,
                            labelColor = TextPrimary
                        ),
                        modifier = Modifier.testTag("timeframe_chip_${tf.interval}")
                    )
                }
            }

            // Live Chart
            CandlestickChart(
                asset = selectedAsset,
                candles = candles,
                indicators = activeIndicators,
                feedStatus = feedStatus,
                onReconnect = { viewModel.reconnectMarketFeed() }
            )

            // Indicators Breakdown for Active Timeframe
            if (activeIndicators != null) {
                IndicatorGridCard(
                    asset = selectedAsset,
                    indicators = activeIndicators
                )
            }

            // Multi-Timeframe Summary Strip (1M / 5M / 15M)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CyberPanel)
                    .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "MULTI-TIMEFRAME CONFLUENCE",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonYellow
                )
                MultiTimeframeRow("1M", report?.m1)
                MultiTimeframeRow("5M", report?.m5)
                MultiTimeframeRow("15M", report?.m15)
            }
        }
    }
}

@Composable
private fun IndicatorGridCard(
    asset: TradingAsset,
    indicators: TimeframeIndicators
) {
    val dec = asset.decimals
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CarbonSurface)
            .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "${indicators.timeframe.label} TECHNICAL INDICATORS (${indicators.candleCount} candles)",
            style = MaterialTheme.typography.labelSmall,
            color = CyberCyan
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricTile(
                title = "RSI (14)",
                value = "%.1f".format(indicators.rsi14),
                valueColor = when {
                    indicators.rsi14 >= 68.0 -> BearishCrimson
                    indicators.rsi14 <= 32.0 -> BullishEmerald
                    else -> TextPrimary
                },
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                title = "MACD (12,26,9)",
                value = indicators.macd.statusLabel,
                valueColor = when (indicators.macd.bias) {
                    TrendBias.BULLISH -> BullishEmerald
                    TrendBias.BEARISH -> BearishCrimson
                    TrendBias.NEUTRAL -> ElectricAmber
                },
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricTile(
                title = "EMA 9 / 21 / 50",
                value = "${"%.${dec}f".format(indicators.ema9)} / ${"%.${dec}f".format(indicators.ema21)}",
                valueColor = NeonYellow,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                title = "S / R LEVELS",
                value = "S:${"%.${dec}f".format(indicators.supportResistance.nearestSupport)}\n" +
                    "R:${"%.${dec}f".format(indicators.supportResistance.nearestResistance)}",
                valueColor = TextPrimary,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricTile(
                title = "CANDLE PATTERN",
                value = indicators.candlePattern,
                valueColor = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                title = "MARKET STRUCTURE",
                value = indicators.marketStructure,
                valueColor = TextPrimary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricTile(
    title: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianVoid)
            .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Text(text = title, style = MaterialTheme.typography.labelSmall, color = TextMuted)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.labelMedium, color = valueColor)
    }
}

@Composable
private fun MultiTimeframeRow(label: String, ind: TimeframeIndicators?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ObsidianVoid)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = NeonYellow)
        if (ind == null) {
            Text("Insufficient candles", style = MaterialTheme.typography.labelSmall, color = TextMuted)
        } else {
            Text(
                text = "RSI ${"%.1f".format(ind.rsi14)} • ${ind.candlePattern}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            val biasColor = when (ind.emaBias) {
                TrendBias.BULLISH -> BullishEmerald
                TrendBias.BEARISH -> BearishCrimson
                TrendBias.NEUTRAL -> ElectricAmber
            }
            Text(
                text = ind.emaBias.name,
                style = MaterialTheme.typography.labelMedium,
                color = biasColor
            )
        }
    }
}
