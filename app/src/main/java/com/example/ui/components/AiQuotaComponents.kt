package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.ai.AiConnectionStatus
import com.example.data.ai.AiQuotaAlert
import com.example.data.ai.AiUsageStats
import com.example.ui.theme.BearishCrimson
import com.example.ui.theme.BearishCrimsonBg
import com.example.ui.theme.BullishEmerald
import com.example.ui.theme.BullishEmeraldBg
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberPanel
import com.example.ui.theme.ElectricAmber
import com.example.ui.theme.NeonYellow
import com.example.ui.theme.NeonYellowSoft
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WaitAmberBg

@Composable
fun AiConnectionStatusBadge(
    status: AiConnectionStatus,
    modifier: Modifier = Modifier
) {
    val (color, bg, label) = when (status) {
        AiConnectionStatus.ONLINE -> Triple(BullishEmerald, BullishEmeraldBg, "AI: ONLINE")
        AiConnectionStatus.OFFLINE -> Triple(BearishCrimson, BearishCrimsonBg, "AI: OFFLINE")
        AiConnectionStatus.QUOTA_EXCEEDED -> Triple(ElectricAmber, WaitAmberBg, "AI: QUOTA EXCEEDED")
    }

    Row(
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, color.copy(alpha = 0.65f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag("ai_connection_status_badge"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AiQuotaExhaustedBanner(
    alert: AiQuotaAlert,
    onSpeakUrdu: (String) -> Unit,
    onRecheckAi: () -> Unit,
    isChecking: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(WaitAmberBg, CyberPanel)
                )
            )
            .border(1.5.dp, ElectricAmber.copy(alpha = 0.75f), RoundedCornerShape(18.dp))
            .padding(14.dp)
            .testTag("ai_quota_exhausted_banner"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Quota Exhausted Alert",
                    tint = ElectricAmber
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = alert.urduTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonYellow
                    )
                    Text(
                        text = "${alert.errorCodeLabel} • Cooldown: ${alert.cooldownRemainingText}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricAmber
                    )
                }
            }

            IconButton(
                onClick = { onSpeakUrdu(alert.urduMessage) },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("speak_quota_urdu_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Speak Urdu Quota Notice",
                    tint = NeonYellow
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(ObsidianVoid.copy(alpha = 0.85f))
                .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = alert.urduMessage,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            Text(
                text = alert.romanUrduMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = alert.backupProviderNote,
                style = MaterialTheme.typography.labelSmall,
                color = CyberCyan,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onRecheckAi,
                enabled = !isChecking,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonYellow),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("quota_banner_recheck_button")
            ) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = NeonYellow,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Re-test AI", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun AiUsageDashboardCard(
    status: AiConnectionStatus,
    stats: AiUsageStats,
    isChecking: Boolean,
    onRecheckAi: () -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = true
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val nowMs = remember(stats) { System.currentTimeMillis() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CarbonSurface)
            .border(1.dp, SlateBorder, RoundedCornerShape(18.dp))
            .padding(14.dp)
            .testTag("ai_usage_dashboard"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (status == AiConnectionStatus.OFFLINE) {
                        Icons.Default.CloudOff
                    } else {
                        Icons.Default.Memory
                    },
                    contentDescription = "AI Telemetry",
                    tint = NeonYellow,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "AI USAGE & QUOTA MONITOR",
                        style = MaterialTheme.typography.labelLarge,
                        color = NeonYellow
                    )
                    Text(
                        text = "Provider: ${stats.activeProvider.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onRecheckAi,
                    enabled = !isChecking,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("recheck_ai_connection_button")
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = NeonYellow,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Check AI Connection",
                            tint = CyberCyan
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse AI Usage" else "Expand AI Usage",
                    tint = TextSecondary
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UsageMetricTile(
                        label = "REQUESTS",
                        value = stats.requestsToday.toString(),
                        accent = NeonYellow,
                        modifier = Modifier.weight(1f)
                    )
                    UsageMetricTile(
                        label = "SUCCESS",
                        value = stats.successfulResponsesToday.toString(),
                        accent = BullishEmerald,
                        modifier = Modifier.weight(1f)
                    )
                    UsageMetricTile(
                        label = "RETRIES",
                        value = stats.temporaryRetriesToday.toString(),
                        accent = CyberCyan,
                        modifier = Modifier.weight(1f)
                    )
                    UsageMetricTile(
                        label = "QUOTA HITS",
                        value = stats.quotaErrorsToday.toString(),
                        accent = if (stats.quotaErrorsToday > 0) ElectricAmber else TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyberPanel)
                        .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Optional Backup AI",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        val backupLabel = when {
                            !stats.backupConfigured -> "DISABLED (AUTO)"
                            stats.backupQuotaExhaustedToday || stats.backupCallsToday >= stats.backupQuotaLimit ->
                                "QUOTA FULL (${stats.backupCallsToday}/${stats.backupQuotaLimit})"
                            else -> "READY (${stats.backupCallsToday}/${stats.backupQuotaLimit})"
                        }
                        val backupColor = when {
                            !stats.backupConfigured -> TextMuted
                            stats.backupQuotaExhaustedToday || stats.backupCallsToday >= stats.backupQuotaLimit -> ElectricAmber
                            else -> BullishEmerald
                        }
                        Text(
                            text = backupLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = backupColor,
                            modifier = Modifier.testTag("backup_provider_status_text")
                        )
                    }

                    if (!stats.backupConfigured) {
                        Text(
                            text = "Running on Primary Gemini AI. Optional backup provider is disabled.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }

                    if (stats.isQuotaCooldownActive(nowMs)) {
                        Text(
                            text = "Daily Quota Cooldown Active: ${stats.formatCooldownRemaining(nowMs)} (auto-retry paused)",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricAmber
                        )
                    }

                    if (!stats.lastErrorSummary.isNullOrBlank()) {
                        Text(
                            text = "Last Event: ${stats.lastErrorSummary}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }

                    if (stats.dateUtc.isNotBlank()) {
                        Text(
                            text = "Quota Window (UTC): ${stats.dateUtc}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageMetricTile(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ObsidianVoid)
            .border(1.dp, SlateBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = accent,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted
        )
    }
}
