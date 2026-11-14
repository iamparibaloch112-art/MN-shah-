package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.SignalRecord
import com.example.data.model.TradeConfirmation
import com.example.data.model.UserProfile
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class TradingRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun observeUserProfile(userId: String): Flow<UserProfile?> =
        db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }

    suspend fun ensureUserProfile(userId: String, displayName: String) {
        val docRef = db.collection("users").document(userId)
        try {
            val snap = docRef.get().await()
            if (!snap.exists()) {
                val safeName = displayName.trim().ifBlank { "MN Shah Trader" }.take(80)
                val payload = mapOf(
                    "userId" to userId,
                    "displayName" to safeName,
                    "voiceAlertsEnabled" to true,
                    "autoMonitorEnabled" to true,
                    "preferredAsset" to "EUR/USD",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun updatePreferences(
        userId: String,
        voiceAlertsEnabled: Boolean,
        autoMonitorEnabled: Boolean,
        preferredAsset: String
    ) {
        val docRef = db.collection("users").document(userId)
        try {
            val payload = mapOf(
                "voiceAlertsEnabled" to voiceAlertsEnabled,
                "autoMonitorEnabled" to autoMonitorEnabled,
                "preferredAsset" to preferredAsset,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(payload).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    fun observeSignals(userId: String): Flow<List<SignalRecord>> =
        db.collection("users").document(userId).collection("signals")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .snapshots()
            .map { querySnap ->
                querySnap.toObjects(
                    SignalRecord::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    suspend fun saveSignal(userId: String, record: SignalRecord): Result<String> {
        val docRef = db.collection("users").document(userId).collection("signals").document()
        val payload = mapOf(
            "userId" to userId,
            "symbol" to record.symbol,
            "direction" to record.direction,
            "confidence" to record.confidence.coerceIn(0, 100),
            "entryPrice" to record.entryPrice.coerceAtLeast(0.00001),
            "rsi1m" to record.rsi1m.coerceIn(0.0, 100.0),
            "macdSignal" to record.macdSignal.ifBlank { "Neutral" }.take(80),
            "emaTrend" to record.emaTrend.ifBlank { "Neutral" }.take(80),
            "patternSummary" to record.patternSummary.ifBlank { "Consolidation" }.take(200),
            "urduSummary" to record.urduSummary.ifBlank { "Market ka tajziya mukammal." }.take(800),
            "evidenceSummary" to record.evidenceSummary.ifBlank { "Insufficient data" }.take(1200),
            "dataSource" to record.dataSource.ifBlank { "Live Feed" }.take(120),
            "candleExpiryEpochMs" to record.candleExpiryEpochMs.coerceAtLeast(1L),
            "createdAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteSignal(userId: String, signalId: String): Result<Unit> {
        val docRef = db.collection("users").document(userId)
            .collection("signals").document(signalId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeTradeConfirmations(userId: String): Flow<List<TradeConfirmation>> =
        db.collection("users").document(userId).collection("trade_confirmations")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .snapshots()
            .map { querySnap ->
                querySnap.toObjects(
                    TradeConfirmation::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }

    suspend fun confirmManualTrade(
        userId: String,
        signalId: String?,
        symbol: String,
        direction: String,
        stakeUsd: Double,
        brokerName: String,
        notes: String
    ): Result<String> {
        val docRef = db.collection("users").document(userId)
            .collection("trade_confirmations").document()
        val payload = buildMap<String, Any> {
            put("userId", userId)
            if (!signalId.isNullOrBlank()) {
                put("signalId", signalId.take(128))
            }
            put("symbol", symbol)
            put("direction", direction)
            put("stakeUsd", stakeUsd.coerceIn(0.01, 1_000_000.0))
            put("brokerName", brokerName.ifBlank { "Manual External Execution" }.take(100))
            put("executionMode", "MANUAL_CONFIRMED")
            put("status", "CONFIRMED")
            if (notes.isNotBlank()) {
                put("notes", notes.take(500))
            }
            put("createdAt", FieldValue.serverTimestamp())
            put("updatedAt", FieldValue.serverTimestamp())
        }
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun cancelTradeConfirmation(
        userId: String,
        tradeId: String,
        notes: String
    ): Result<Unit> {
        val docRef = db.collection("users").document(userId)
            .collection("trade_confirmations").document(tradeId)
        val payload = mapOf(
            "status" to "CANCELLED",
            "notes" to notes.ifBlank { "Cancelled manually by trader" }.take(500),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }
}
