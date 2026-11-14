package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ai.AiConnectionStatus
import com.example.data.market.FeedStatus
import com.example.data.market.TradingAsset
import com.example.domain.analysis.SignalDirection
import com.example.ui.components.AiConnectionStatusBadge
import com.example.ui.components.AiQuotaExhaustedBanner
import com.example.ui.components.AiUsageDashboardCard
import com.example.ui.components.AnimatedRobotFace
import com.example.ui.components.ManualTradeSheet
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
import com.example.ui.viewmodel.MnShahViewModel
import com.example.voice.MnShahVoiceEngine
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RobotDashboardScreen(
    viewModel: MnShahViewModel,
    voiceEngine: MnShahVoiceEngine,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedAsset by viewModel.selectedAsset.collectAsStateWithLifecycle()
    val snapshot by viewModel.marketSnapshot.collectAsStateWithLifecycle()
    val report by viewModel.technicalReport.collectAsStateWithLifecycle()
    val activeSignal by viewModel.activeSignal.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val isRefreshingFeed by viewModel.isRefreshingFeed.collectAsStateWithLifecycle()
    val isMonitoring by viewModel.isMonitoring.collectAsStateWithLifecycle()
    val voiceAlertsEnabled by viewModel.voiceAlertsEnabled.collectAsStateWithLifecycle()
    val lastCommandFeedback by viewModel.lastCommandFeedback.collectAsStateWithLifecycle()
    val assistantReply by viewModel.assistantReply.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val tradeActionMessage by viewModel.tradeActionMessage.collectAsStateWithLifecycle()
    val aiConnectionStatus by viewModel.aiConnectionStatus.collectAsStateWithLifecycle()
    val aiUsageStats by viewModel.aiUsageStats.collectAsStateWithLifecycle()
    val aiQuotaAlert by viewModel.aiQuotaAlert.collectAsStateWithLifecycle()
    val isCheckingAiConnection by viewModel.isCheckingAiConnection.collectAsStateWithLifecycle()

    val isSpeaking by voiceEngine.isSpeaking.collectAsStateWithLifecycle()
    val isListening by voiceEngine.isListening.collectAsStateWithLifecycle()
    val partialTranscript by voiceEngine.partialTranscript.collectAsStateWithLifecycle()
    val ttsStatus by voiceEngine.ttsStatus.collectAsStateWithLifecycle()
    val sttError by voiceEngine.sttError.collectAsStateWithLifecycle()

    var commandText by remember { mutableStateOf("") }
    var micNotice by remember { mutableStateOf<String?>(null) }
    var showManualTradeSheet by remember { mutableStateOf(false) }

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            micNotice = null
            voiceEngine.startListening(
                onFinalResult = { transcript ->
                    commandText = transcript
                    viewModel.submitVoiceOrTextCommand(
                        rawCommand = transcript,
                        fromVoice = true,
                        onSpeakUrdu = { urdu -> voiceEngine.speakUrdu(urdu) }
                    )
                },
                onError = { err -> micNotice = err }
            )
        } else {
            micNotice = "Microphone permission denied. Type \"MN SHAH mujhe signal do\" below instead."
        }
    }

    val feedStatus = snapshot?.status ?: FeedStatus.Connecting
    val robotStatusLabel = when {
        isListening -> "MIC ACTIVE • LISTENING"
        isSpeaking -> "SPEAKING URDU ANALYSIS"
        isAnalyzing -> "SCANNING 1M / 5M / 15M"
        feedStatus is FeedStatus.Live -> "FEED LIVE • MONITORING"
        feedStatus is FeedStatus.Delayed -> "FEED DELAYED (${feedStatus.delaySeconds}s)"
        feedStatus is FeedStatus.Disconnected -> "FEED DISCONNECTED"
        else -> "INITIALIZING FEED..."
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianVoid),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Status Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MN SHAH",
                        style = MaterialTheme.typography.headlineSmall,
                        color = NeonYellow
                    )
                    Text(
                        text = ttsStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (badgeColor, badgeLabel) = when (feedStatus) {
                        is FeedStatus.Live -> BullishEmerald to "LIVE"
                        is FeedStatus.Delayed -> ElectricAmber to "DELAYED"
                        is FeedStatus.Disconnected -> BearishCrimson to "OFFLINE"
                        FeedStatus.Connecting -> CyberCyan to "SYNCING"
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(CarbonSurface)
                            .border(1.dp, badgeColor.copy(alpha = 0.6f), RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(badgeColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = badgeColor
                        )
                    }
                    IconButton(
                        onClick = { viewModel.reconnectMarketFeed() },
                        modifier = Modifier.testTag("reconnect_feed_button")
                    ) {
                        if (isRefreshingFeed) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = NeonYellow,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reconnect Market Feed",
                                tint = NeonYellow
                            )
                        }
                    }
                }
            }

            // AI Connection Status & Active Engine Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AiConnectionStatusBadge(status = aiConnectionStatus)
                Text(
                    text = aiUsageStats.activeProvider.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (aiConnectionStatus == AiConnectionStatus.ONLINE) CyberCyan else ElectricAmber
                )
            }

            // Animated Futuristic Robot Face
            AnimatedRobotFace(
                isAnalyzing = isAnalyzing,
                isSpeaking = isSpeaking,
                isListening = isListening,
                direction = activeSignal?.direction ?: report?.quantitativeDirection,
                statusLabel = robotStatusLabel
            )

            // Asset Switcher & Live Price Strip
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
                        label = {
                            Text(
                                text = asset.symbol,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeonYellow,
                            selectedLabelColor = ObsidianVoid,
                            containerColor = CarbonSurface,
                            labelColor = TextPrimary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = SlateBorder,
                            selectedBorderColor = NeonYellow
                        ),
                        modifier = Modifier.testTag("asset_chip_${asset.name.lowercase()}")
                    )
                }
            }

            // Live Price & Data Source Telemetry Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CarbonSurface)
                    .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = selectedAsset.symbol,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary
                        )
                        val priceDisplay = snapshot?.currentPrice?.takeIf { it > 0.0 }?.let {
                            "%.${selectedAsset.decimals}f".format(it)
                        } ?: "---"
                        Text(
                            text = priceDisplay,
                            style = MaterialTheme.typography.displaySmall,
                            color = TextPrimary,
                            modifier = Modifier.testTag("live_spot_price")
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "1M CANDLE EXPIRY",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        CandleExpiryCountdownText(countdownFlow = viewModel.countdownSeconds)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                val sourceDesc = when (val s = feedStatus) {
                    is FeedStatus.Live -> "Source: ${s.source} • Tick: ${timeFormat.format(Date(s.lastTickEpochMs))}"
                    is FeedStatus.Delayed -> "DELAYED (${s.delaySeconds}s): ${s.reason}"
                    is FeedStatus.Disconnected -> "DISCONNECTED: ${s.reason}"
                    FeedStatus.Connecting -> "Connecting to live market data..."
                }
                Text(
                    text = sourceDesc,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (feedStatus is FeedStatus.Live) TextSecondary else ElectricAmber
                )
            }

            // Primary ANALYZE NOW Button + Monitoring & Voice Controls
            Button(
                onClick = {
                    viewModel.analyzeNow(
                        userCommand = "Manual ANALYZE NOW request",
                        forceSpeak = voiceAlertsEnabled,
                        onSpeakUrdu = { urdu -> voiceEngine.speakUrdu(urdu) }
                    )
                },
                enabled = !isAnalyzing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonYellow,
                    contentColor = ObsidianVoid
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("analyze_now_button")
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = ObsidianVoid,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("ANALYZING 1M / 5M / 15M...", style = MaterialTheme.typography.labelLarge)
                } else {
                    Icon(Icons.Default.Bolt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ANALYZE NOW", style = MaterialTheme.typography.labelLarge)
                }
            }

            // Monitoring & Urdu Voice Alert Toggles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CarbonSurface)
                    .border(1.dp, SlateBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto-Monitor", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = isMonitoring,
                        onCheckedChange = { viewModel.toggleMonitoring(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianVoid,
                            checkedTrackColor = NeonYellow
                        ),
                        modifier = Modifier.testTag("auto_monitor_switch")
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Urdu Voice", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Switch(
                        checked = voiceAlertsEnabled,
                        onCheckedChange = {
                            viewModel.toggleVoiceAlerts(it)
                            if (!it) voiceEngine.stopSpeaking()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianVoid,
                            checkedTrackColor = CyberCyan
                        ),
                        modifier = Modifier.testTag("voice_alerts_switch")
                    )
                }
            }

            // Error / Trade Feedback Banner
            AnimatedVisibility(visible = errorMessage != null || tradeActionMessage != null) {
                val msg = errorMessage ?: tradeActionMessage.orEmpty()
                val isErr = errorMessage != null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isErr) BearishCrimsonBg else BullishEmeraldBg)
                        .border(
                            1.dp,
                            if (isErr) BearishCrimson else BullishEmerald,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isErr) Icons.Default.Warning else Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (isErr) BearishCrimson else BullishEmerald
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Daily AI Quota Exhausted Urdu Notice Card
            val currentQuotaAlert = aiQuotaAlert
            AnimatedVisibility(visible = currentQuotaAlert != null) {
                if (currentQuotaAlert != null) {
                    AiQuotaExhaustedBanner(
                        alert = currentQuotaAlert,
                        onSpeakUrdu = { urdu -> voiceEngine.speakUrdu(urdu) },
                        onRecheckAi = { viewModel.recheckAiConnection() },
                        isChecking = isCheckingAiConnection
                    )
                }
            }

            // Active Signal Card
            val signal = activeSignal
            if (signal != null) {
                val (dirColor, dirBg) = when (signal.direction) {
                    SignalDirection.UP -> BullishEmerald to BullishEmeraldBg
                    SignalDirection.DOWN -> BearishCrimson to BearishCrimsonBg
                    SignalDirection.WAIT, SignalDirection.UNCERTAIN -> ElectricAmber to WaitAmberBg
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(CyberPanel)
                        .border(1.5.dp, dirColor.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${signal.asset.symbol} NEXT 1M ESTIMATE",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(dirBg)
                                    .border(1.dp, dirColor, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = signal.direction.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = dirColor,
                                    modifier = Modifier.testTag("signal_direction_badge")
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "CONFIDENCE ${signal.confidence}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = dirColor
                            )
                            Text(
                                text = "At ${timeFormat.format(Date(signal.generatedAtEpochMs))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                            SignalExpiryCountdownText(
                                expiryFormatted = timeFormat.format(Date(signal.candleExpiryEpochMs)),
                                countdownFlow = viewModel.countdownSeconds
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { signal.confidence / 100f },
                        color = dirColor,
                        trackColor = ObsidianVoid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                    )

                    // Urdu Analysis + Voice Replay
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(ObsidianVoid)
                            .border(1.dp, SlateBorder, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "MN SHAH URDU BRIEFING • ${signal.providerLabel.uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonYellow
                            )
                            IconButton(
                                onClick = {
                                    if (isSpeaking) voiceEngine.stopSpeaking()
                                    else voiceEngine.speakUrdu(signal.urduSpeech)
                                },
                                modifier = Modifier.testTag("replay_urdu_voice_button")
                            ) {
                                Icon(
                                    imageVector = if (isSpeaking) {
                                        Icons.AutoMirrored.Filled.VolumeOff
                                    } else {
                                        Icons.AutoMirrored.Filled.VolumeUp
                                    },
                                    contentDescription = if (isSpeaking) "Stop Urdu Speech" else "Speak Urdu Analysis",
                                    tint = NeonYellow
                                )
                            }
                        }
                        Text(
                            text = signal.urduSpeech,
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = signal.romanUrduSummary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }

                    // Evidence Section
                    Text(
                        text = "TECHNICAL EVIDENCE",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberCyan
                    )
                    signal.evidencePoints.forEach { bullet ->
                        Text(
                            text = "• $bullet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }

                    Text(
                        text = signal.riskNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricAmber
                    )

                    if (signal.direction == SignalDirection.UP || signal.direction == SignalDirection.DOWN) {
                        OutlinedButton(
                            onClick = { showManualTradeSheet = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonYellow),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("open_manual_trade_sheet_button")
                        ) {
                            Text(
                                text = "LOG MANUAL REAL TRADE (${signal.direction.name})",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            } else {
                // First-launch prompt card before user triggers ANALYZE NOW
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(CarbonSurface)
                        .border(1.dp, SlateBorder, RoundedCornerShape(18.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "READY FOR SIGNAL COMMAND",
                        style = MaterialTheme.typography.labelLarge,
                        color = NeonYellow
                    )
                    Text(
                        text = "Tap ANALYZE NOW or tap the microphone and say \"MN SHAH mujhe signal do\" to run multi-timeframe 1M/5M/15M analysis with spoken Urdu explanation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            // General Assistant Chat Reply if user asked a question
            val reply = assistantReply
            if (reply != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CarbonSurface)
                        .border(1.dp, CyberCyan.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Q: ${reply.userQuery}",
                        style = MaterialTheme.typography.labelSmall,
                        color = CyberCyan
                    )
                    Text(
                        text = reply.urduSpeech,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                    Text(
                        text = reply.romanSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }

            // Voice & Text Command Center
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CarbonSurface)
                    .border(1.dp, SlateBorder, RoundedCornerShape(18.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "VOICE & TEXT COMMAND CENTER",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonYellow
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = {
                            commandText = "MN SHAH mujhe signal do"
                            viewModel.submitVoiceOrTextCommand(
                                rawCommand = "MN SHAH mujhe signal do",
                                fromVoice = true,
                                onSpeakUrdu = { voiceEngine.speakUrdu(it) }
                            )
                        },
                        label = { Text("MN SHAH mujhe signal do") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = NeonYellowSoft,
                            labelColor = NeonYellow
                        ),
                        modifier = Modifier.testTag("quick_command_signal_chip")
                    )
                    AssistChip(
                        onClick = {
                            commandText = "XAU USD signal do"
                            viewModel.submitVoiceOrTextCommand(
                                rawCommand = "XAU USD signal do",
                                fromVoice = true,
                                onSpeakUrdu = { voiceEngine.speakUrdu(it) }
                            )
                        },
                        label = { Text("Gold XAU/USD signal") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = CyberPanel,
                            labelColor = TextPrimary
                        )
                    )
                }

                if (isListening && partialTranscript.isNotBlank()) {
                    Text(
                        text = "Mic: $partialTranscript",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyberCyan
                    )
                }
                val notice = micNotice ?: sttError
                if (notice != null) {
                    Text(
                        text = notice,
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricAmber
                    )
                }
                if (lastCommandFeedback != null) {
                    Text(
                        text = lastCommandFeedback.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (isListening) {
                                voiceEngine.stopListening()
                            } else {
                                val hasMic = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasMic) {
                                    micNotice = null
                                    voiceEngine.startListening(
                                        onFinalResult = { transcript ->
                                            commandText = transcript
                                            viewModel.submitVoiceOrTextCommand(
                                                rawCommand = transcript,
                                                fromVoice = true,
                                                onSpeakUrdu = { voiceEngine.speakUrdu(it) }
                                            )
                                        },
                                        onError = { err -> micNotice = err }
                                    )
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isListening) CyberCyan else NeonYellowSoft)
                            .testTag("mic_command_button")
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Voice Command Microphone",
                            tint = if (isListening) ObsidianVoid else NeonYellow
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedTextField(
                        value = commandText,
                        onValueChange = { commandText = it },
                        placeholder = { Text("MN SHAH mujhe signal do") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonYellow,
                            unfocusedBorderColor = SlateBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("command_text_input")
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = {
                            val cmd = commandText
                            commandText = ""
                            viewModel.submitVoiceOrTextCommand(
                                rawCommand = cmd,
                                fromVoice = false,
                                onSpeakUrdu = { voiceEngine.speakUrdu(it) }
                            )
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(NeonYellow)
                            .testTag("send_command_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Command",
                            tint = ObsidianVoid
                        )
                    }
                }
            }

            // AI Usage Monitoring Dashboard
            AiUsageDashboardCard(
                status = aiConnectionStatus,
                stats = aiUsageStats,
                isChecking = isCheckingAiConnection,
                onRecheckAi = { viewModel.recheckAiConnection() },
                initiallyExpanded = true
            )

            Text(
                text = "Disclaimer: Market intelligence uses live technical indicators + Gemini AI. Never guaranteed; manual human confirmation required.",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted
            )
        }
    }

    val currentSig = activeSignal
    if (showManualTradeSheet && currentSig != null) {
        ManualTradeSheet(
            asset = currentSig.asset,
            initialDirection = currentSig.direction.name,
            entryPrice = currentSig.entryPrice,
            onDismiss = { showManualTradeSheet = false },
            onConfirmManualTrade = { sym, dir, stake, broker, notes ->
                viewModel.confirmManualTrade(sym, dir, stake, broker, notes)
            }
        )
    }
}

@Composable
private fun CandleExpiryCountdownText(countdownFlow: StateFlow<Long>) {
    val countdownSeconds by countdownFlow.collectAsStateWithLifecycle()
    Text(
        text = "${countdownSeconds}s",
        style = MaterialTheme.typography.headlineSmall,
        color = if (countdownSeconds <= 10) ElectricAmber else NeonYellow,
        modifier = Modifier.testTag("candle_expiry_countdown")
    )
}

@Composable
private fun SignalExpiryCountdownText(
    expiryFormatted: String,
    countdownFlow: StateFlow<Long>
) {
    val countdownSeconds by countdownFlow.collectAsStateWithLifecycle()
    Text(
        text = "Expiry: $expiryFormatted (${countdownSeconds}s)",
        style = MaterialTheme.typography.labelSmall,
        color = NeonYellow
    )
}
