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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.market.TradingAsset
import com.example.firebase.signOut
import com.example.ui.components.ManualTradeSheet
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
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun BrokerComplianceScreen(
    viewModel: MnShahViewModel,
    userDisplayName: String,
    userEmail: String?,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val selectedAsset by viewModel.selectedAsset.collectAsStateWithLifecycle()
    val snapshot by viewModel.marketSnapshot.collectAsStateWithLifecycle()
    val tradesState by viewModel.tradeConfirmations.collectAsStateWithLifecycle()
    val tradeMessage by viewModel.tradeActionMessage.collectAsStateWithLifecycle()

    var showManualSheet by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "REAL TRADING & BROKER COMPLIANCE",
                style = MaterialTheme.typography.titleMedium,
                color = NeonYellow
            )

            // Account Header + Sign Out
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CarbonSurface)
                    .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = userDisplayName.ifBlank { "Authenticated Trader" },
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    if (!userEmail.isNullOrBlank()) {
                        Text(
                            text = userEmail,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
                OutlinedButton(
                    onClick = {
                        signOut(
                            credentialManager = credentialManager,
                            scope = scope,
                            onSignOutComplete = onSignedOut
                        )
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BearishCrimson),
                    modifier = Modifier.testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sign Out", style = MaterialTheme.typography.labelMedium)
                }
            }

            // Authorized Broker Architecture & Policy Disclosure
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(CyberPanel)
                    .border(1.dp, ElectricAmber.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = ElectricAmber)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "OFFICIAL BROKER & SECURITY POLICY",
                        style = MaterialTheme.typography.labelLarge,
                        color = ElectricAmber
                    )
                }
                Text(
                    text = "• NO DEMO / SIMULATED TRADES: This assistant never places fake demo trades.\n" +
                        "• QUOTEX BLOCKED: Quotex has no official public retail trading API. MN Shah Bot never simulates Quotex connections, never uses unofficial private APIs, and never asks for broker passwords.\n" +
                        "• OFFICIAL AUTHORIZED INTEGRATIONS: Real order routing is only permitted via regulated brokers with official OAuth2 / Bearer token REST APIs (such as OANDA v20, IG Group Trading API, or Interactive Brokers Client Portal API) with credentials stored on a secure backend proxy.\n" +
                        "• NATIVE ANDROID BUILD: Built natively for Android with Firebase App Check attestation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
            }

            // Manual Trade Confirmation Button
            Button(
                onClick = { showManualSheet = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonYellow,
                    contentColor = ObsidianVoid
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("new_manual_trade_button")
            ) {
                Icon(Icons.Default.GppGood, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("RECORD MANUAL REAL-TRADE CONFIRMATION", style = MaterialTheme.typography.labelLarge)
            }

            if (tradeMessage != null) {
                Text(
                    text = tradeMessage.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = BullishEmerald
                )
            }

            // Manual Trade Audit Ledger
            Text(
                text = "MANUAL TRADE CONFIRMATION LEDGER",
                style = MaterialTheme.typography.labelSmall,
                color = CyberCyan
            )

            when (val state = tradesState) {
                UiState.Loading -> CircularProgressIndicator(color = NeonYellow)
                is UiState.Error -> Text(state.message, color = BearishCrimson)
                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        Text(
                            text = "No manual real-trade confirmations recorded yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(CarbonSurface)
                                .padding(14.dp)
                        )
                    } else {
                        state.data.forEach { trade ->
                            val isCancelled = trade.status == "CANCELLED"
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(CarbonSurface)
                                    .border(1.dp, SlateBorder, RoundedCornerShape(14.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${trade.symbol} • ${trade.direction} • $${"%.2f".format(trade.stakeUsd)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (trade.direction == "UP") BullishEmerald else BearishCrimson
                                    )
                                    Text(
                                        text = trade.status,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isCancelled) TextMuted else NeonYellow
                                    )
                                }
                                Text(
                                    text = "Channel: ${trade.brokerName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                if (trade.notes.isNotBlank()) {
                                    Text(
                                        text = "Notes: ${trade.notes}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary
                                    )
                                }
                                val ts = trade.createdAt?.toDate()?.let { dateFormat.format(it) } ?: "Syncing..."
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = ts, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                                    if (!isCancelled) {
                                        OutlinedButton(
                                            onClick = { viewModel.cancelTradeConfirmation(trade.id) },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricAmber)
                                        ) {
                                            Text("Mark Cancelled", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showManualSheet) {
        ManualTradeSheet(
            asset = snapshot?.asset ?: selectedAsset,
            initialDirection = "UP",
            entryPrice = snapshot?.currentPrice ?: 0.0,
            onDismiss = { showManualSheet = false },
            onConfirmManualTrade = { sym, dir, stake, broker, notes ->
                viewModel.confirmManualTrade(sym, dir, stake, broker, notes)
            }
        )
    }
}
