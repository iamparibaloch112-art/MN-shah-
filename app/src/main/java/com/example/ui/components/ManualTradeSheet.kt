package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.market.TradingAsset
import com.example.ui.theme.BearishCrimson
import com.example.ui.theme.BullishEmerald
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CyberPanel
import com.example.ui.theme.ElectricAmber
import com.example.ui.theme.NeonYellow
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualTradeSheet(
    asset: TradingAsset,
    initialDirection: String,
    entryPrice: Double,
    onDismiss: () -> Unit,
    onConfirmManualTrade: (
        symbol: String,
        direction: String,
        stakeUsd: Double,
        brokerName: String,
        notes: String
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val brokers = remember {
        listOf(
            "OANDA v20 (Authorized)",
            "IG Markets API (Authorized)",
            "Interactive Brokers (Authorized)",
            "Manual External Execution"
        )
    }
    var selectedDirection by remember(initialDirection) {
        mutableStateOf(if (initialDirection == "DOWN") "DOWN" else "UP")
    }
    var stakeText by remember { mutableStateOf("25.0") }
    var selectedBroker by remember { mutableStateOf(brokers.first()) }
    var notes by remember { mutableStateOf("") }
    var explicitAuthorizationChecked by remember { mutableStateOf(false) }

    val parsedStake = stakeText.toDoubleOrNull()
    val stakeValid = parsedStake != null && parsedStake > 0.0 && parsedStake <= 1_000_000.0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CarbonSurface,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Manual Real Trade Authorization",
                    tint = NeonYellow
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "MANUAL REAL-TRADE CONFIRMATION",
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonYellow
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Strict Compliance: MN Shah Bot never places demo trades, never uses unofficial Quotex APIs, and never executes trades automatically without your explicit authorization.",
                style = MaterialTheme.typography.bodyMedium,
                color = ElectricAmber,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CyberPanel)
                    .border(1.dp, ElectricAmber.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "${asset.symbol}  •  Spot: ${"%.${asset.decimals}f".format(entryPrice)}",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text("Direction", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("UP", "DOWN").forEach { dir ->
                    val isUp = dir == "UP"
                    val selected = selectedDirection == dir
                    FilterChip(
                        selected = selected,
                        onClick = { selectedDirection = dir },
                        label = {
                            Text(
                                text = dir,
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = if (isUp) BullishEmerald else BearishCrimson,
                            selectedLabelColor = ObsidianVoid
                        ),
                        modifier = Modifier.testTag("manual_trade_dir_${dir.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = stakeText,
                onValueChange = { stakeText = it },
                label = { Text("Authorized Stake / Risk (USD)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = !stakeValid,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonYellow,
                    unfocusedBorderColor = SlateBorder,
                    focusedLabelColor = NeonYellow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("manual_trade_stake_input")
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text("Authorized Execution Channel", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            brokers.forEach { broker ->
                val isSelected = selectedBroker == broker
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) CyberPanel else ObsidianVoid)
                        .border(
                            1.dp,
                            if (isSelected) NeonYellow else SlateBorder,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { selectedBroker = broker }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = broker,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) NeonYellow else TextPrimary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Execution Notes (optional)") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonYellow,
                    unfocusedBorderColor = SlateBorder,
                    focusedLabelColor = NeonYellow
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("manual_trade_notes_input")
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CyberPanel)
                    .clickable { explicitAuthorizationChecked = !explicitAuthorizationChecked }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = explicitAuthorizationChecked,
                    onCheckedChange = { explicitAuthorizationChecked = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = NeonYellow,
                        checkmarkColor = ObsidianVoid
                    ),
                    modifier = Modifier.testTag("manual_trade_authorize_checkbox")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "I explicitly authorize logging this manual real-money trade decision on my chosen regulated broker.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val stake = parsedStake ?: return@Button
                    onConfirmManualTrade(
                        asset.symbol,
                        selectedDirection,
                        stake,
                        selectedBroker,
                        notes
                    )
                    onDismiss()
                },
                enabled = explicitAuthorizationChecked && stakeValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonYellow,
                    contentColor = ObsidianVoid
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("confirm_manual_trade_button")
            ) {
                Text(
                    text = "CONFIRM MANUAL TRADE LOG",
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
