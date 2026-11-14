package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.SignalRecord
import com.example.ui.theme.BearishCrimson
import com.example.ui.theme.BullishEmerald
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CyberPanel
import com.example.ui.theme.ElectricAmber
import com.example.ui.theme.NeonYellow
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MnShahViewModel
import com.example.ui.viewmodel.UiState
import com.example.voice.MnShahVoiceEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SignalHistoryScreen(
    viewModel: MnShahViewModel,
    voiceEngine: MnShahVoiceEngine,
    modifier: Modifier = Modifier
) {
    val signalsState by viewModel.savedSignals.collectAsStateWithLifecycle()
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "SIGNAL AUDIT HISTORY",
                style = MaterialTheme.typography.titleMedium,
                color = NeonYellow
            )
            Text(
                text = "Synced securely to Cloud Firestore per user",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(12.dp))

            when (val state = signalsState) {
                UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NeonYellow)
                    }
                }
                is UiState.Error -> {
                    Text(
                        text = "Error: ${state.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = BearishCrimson
                    )
                }
                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(CarbonSurface)
                                .border(1.dp, SlateBorder, RoundedCornerShape(18.dp))
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timeline,
                                contentDescription = null,
                                tint = NeonYellow
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No signals logged yet",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap ANALYZE NOW or ask \"MN SHAH mujhe signal do\" on the Command tab.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.data, key = { it.id }) { item ->
                                SignalHistoryCard(
                                    record = item,
                                    dateFormat = dateFormat,
                                    onSpeak = { voiceEngine.speakUrdu(item.urduSummary) },
                                    onDelete = { viewModel.deleteSignalRecord(item.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SignalHistoryCard(
    record: SignalRecord,
    dateFormat: SimpleDateFormat,
    onSpeak: () -> Unit,
    onDelete: () -> Unit
) {
    val dirColor = when (record.direction) {
        "UP" -> BullishEmerald
        "DOWN" -> BearishCrimson
        else -> ElectricAmber
    }
    val createdText = record.createdAt?.toDate()?.let { dateFormat.format(it) } ?: "Pending sync"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CyberPanel)
            .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = record.symbol,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                Text(
                    text = "${record.direction} (${record.confidence}%)",
                    style = MaterialTheme.typography.labelLarge,
                    color = dirColor
                )
            }
            Row {
                IconButton(onClick = onSpeak) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Speak Urdu Signal",
                        tint = NeonYellow
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete Signal",
                        tint = TextMuted
                    )
                }
            }
        }

        Text(
            text = "Spot: ${record.entryPrice} • RSI(1M): ${"%.1f".format(record.rsi1m)} • ${record.patternSummary}",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Text(
            text = record.urduSummary,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary
        )
        Text(
            text = "Evidence: ${record.evidenceSummary}",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Text(
            text = "$createdText • Source: ${record.dataSource}",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
    }
}
