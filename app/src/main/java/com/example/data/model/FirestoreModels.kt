package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Trader",
    val voiceAlertsEnabled: Boolean = true,
    val autoMonitorEnabled: Boolean = true,
    val preferredAsset: String = "EUR/USD",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class SignalRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val symbol: String = "EUR/USD",
    val direction: String = "WAIT",
    val confidence: Int = 0,
    val entryPrice: Double = 0.0,
    val rsi1m: Double = 50.0,
    val macdSignal: String = "",
    val emaTrend: String = "",
    val patternSummary: String = "",
    val urduSummary: String = "",
    val evidenceSummary: String = "",
    val dataSource: String = "",
    val candleExpiryEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TradeConfirmation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val signalId: String = "",
    val symbol: String = "EUR/USD",
    val direction: String = "UP",
    val stakeUsd: Double = 10.0,
    val brokerName: String = "Manual External Execution",
    val executionMode: String = "MANUAL_CONFIRMED",
    val status: String = "CONFIRMED",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
