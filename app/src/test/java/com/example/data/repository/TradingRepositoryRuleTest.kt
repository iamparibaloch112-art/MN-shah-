package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.SignalRecord
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class TradingRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun saveAndObserveSignal_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser("alice_${UUID.randomUUID()}@test.com")
        val repo = TradingRepository(firestore)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserProfile(uid, "Alice Trader")
        }

        val record = SignalRecord(
            userId = uid,
            symbol = "EUR/USD",
            direction = "UP",
            confidence = 76,
            entryPrice = 1.0854,
            rsi1m = 33.2,
            macdSignal = "Bullish Crossover",
            emaTrend = "EMA9 > EMA21",
            patternSummary = "Bullish Engulfing",
            urduSummary = "EUR/USD par support se buy setup nazar aa raha hai.",
            evidenceSummary = "1m RSI bounce + MACD positive + 5m uptrend",
            dataSource = "Yahoo Finance Live Feed",
            candleExpiryEpochMs = System.currentTimeMillis() + 60_000L
        )

        val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.saveSignal(uid, record)
        }
        assertTrue(saveResult.isSuccess)
        val signalId = saveResult.getOrThrow()
        assertTrue(signalId.isNotBlank())

        val signals = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeSignals(uid).first { list -> list.any { it.id == signalId } }
        }
        assertTrue(signals.any { it.id == signalId && it.symbol == "EUR/USD" })
    }

    @Test
    fun confirmAndCancelManualTrade_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser("trader_${UUID.randomUUID()}@test.com")
        val repo = TradingRepository(firestore)

        val confirmResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.confirmManualTrade(
                userId = uid,
                signalId = "sig_test_1",
                symbol = "XAU/USD",
                direction = "DOWN",
                stakeUsd = 25.0,
                brokerName = "OANDA v20 (Authorized)",
                notes = "Verified resistance rejection"
            )
        }
        assertTrue(confirmResult.isSuccess)
        val tradeId = confirmResult.getOrThrow()

        val cancelResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.cancelTradeConfirmation(uid, tradeId, "Closed manually")
        }
        assertTrue(cancelResult.isSuccess)

        val trades = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeTradeConfirmations(uid).first { list -> list.any { it.id == tradeId } }
        }
        val stored = trades.first { it.id == tradeId }
        assertEquals("CANCELLED", stored.status)
    }

    @Test
    fun observeSignals_unauthenticatedCaller_failsWithPermissionDenied() = runBlocking {
        val targetUid = "some_user_uid"
        auth.signOut()
        val repo = TradingRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeSignals(targetUid).first()
            }
            fail("Expected PERMISSION_DENIED for unauthenticated caller")
        } catch (e: Exception) {
            val fsException = e.unwrapFirestoreException()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsException?.code)
        }
    }

    @Test
    fun observeSignals_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_cross_${UUID.randomUUID()}@test.com")
        val aliceRepo = TradingRepository(firestore)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.saveSignal(
                aliceUid,
                SignalRecord(
                    userId = aliceUid,
                    symbol = "GBP/USD",
                    direction = "WAIT",
                    confidence = 40,
                    entryPrice = 1.2712,
                    rsi1m = 50.1,
                    macdSignal = "Flat",
                    emaTrend = "Sideways",
                    patternSummary = "Doji Indecision",
                    urduSummary = "Market neutral hai, intezar behtar hai.",
                    evidenceSummary = "Mixed 1m/5m EMAs",
                    dataSource = "Yahoo Finance Live Feed",
                    candleExpiryEpochMs = System.currentTimeMillis() + 60_000L
                )
            ).getOrThrow()
        }

        signInTestUser("bob_cross_${UUID.randomUUID()}@test.com")
        val bobRepo = TradingRepository(firestore)
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeSignals(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED for cross-user signal read")
        } catch (e: Exception) {
            val fsException = e.unwrapFirestoreException()
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsException?.code)
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
