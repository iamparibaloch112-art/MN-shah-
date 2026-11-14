package com.example.data.ai

import android.content.Context
import android.util.Log
import com.example.data.market.FeedStatus
import com.example.data.market.MultiTimeframeMarketSnapshot
import com.example.domain.analysis.SignalDirection
import com.example.domain.analysis.TechnicalAnalysisReport
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class AiSignalBriefing(
    val direction: SignalDirection,
    val confidence: Int,
    val urduSpeech: String,
    val romanUrduSummary: String,
    val evidencePoints: List<String>,
    val riskNote: String,
    val providerUsed: ActiveAiProvider = ActiveAiProvider.GEMINI
)

class MnShahAiAnalyst(
    private val backupProvider: BackupAiProvider = OkHttpBackupAiProvider(),
    private val quotaTracker: AiQuotaTracker = AiQuotaTracker(
        store = InMemoryAiQuotaStore(),
        backupProviderConfigured = { backupProvider.isConfigured() },
        backupDailyLimit = backupProvider.dailyQuotaLimit()
    ),
    private val primarySignalCaller: (suspend (String) -> String)? = null,
    private val primaryChatCaller: (suspend (String) -> String)? = null
) {
    constructor(context: Context) : this(
        backupProvider = OkHttpBackupAiProvider(),
        quotaTracker = createTracker(context.applicationContext)
    )

    val connectionStatus: StateFlow<AiConnectionStatus> = quotaTracker.connectionStatus
    val usageStats: StateFlow<AiUsageStats> = quotaTracker.usageStats
    val quotaAlert: StateFlow<AiQuotaAlert?> = quotaTracker.quotaAlert

    private val signalSchema = Schema.obj(
        properties = mapOf(
            "direction" to Schema.enumeration(
                values = listOf("UP", "DOWN", "UNCERTAIN", "WAIT"),
                description = "Next 1-minute candle bias. Must be WAIT or UNCERTAIN if data is delayed/disconnected or indicators conflict."
            ),
            "confidence" to Schema.integer(
                description = "Realistic technical confidence percentage from 0 to 100."
            ),
            "urduSpeech" to Schema.string(
                description = "Natural Urdu script (اردو) male robotic assistant response explaining the signal and evidence clearly for Urdu TTS voice output."
            ),
            "romanUrduSummary" to Schema.string(
                description = "Concise Roman Urdu summary (2-3 sentences) matching the Urdu speech."
            ),
            "evidencePoints" to Schema.array(
                items = Schema.string(),
                description = "3 to 5 concise evidence bullets referencing the exact RSI, MACD, EMA, S/R, and candle pattern numbers provided."
            ),
            "riskNote" to Schema.string(
                description = "Honest market uncertainty or risk reminder without any guarantee of accuracy."
            )
        )
    )

    private val signalModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = signalSchema
                temperature = 0.25f
            }
        )
    }

    private val chatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.35f
            }
        )
    }

    suspend fun initializeQuotaState() {
        quotaTracker.syncInitialState()
    }

    suspend fun recheckAiConnection(): Result<AiConnectionStatus> {
        quotaTracker.resetCooldownForManualRecheck()
        val probePrompt = "Reply with exactly: ONLINE"
        val res = quotaTracker.executeWithSmartQuota(
            primaryCall = {
                withTimeout(20_000L) {
                    primaryChatCaller?.invoke(probePrompt)
                        ?: chatModel.generateContent(probePrompt).text
                        ?: throw IllegalStateException("Empty probe response")
                }
            },
            backupCall = if (backupProvider.isConfigured()) {
                {
                    backupProvider.generateTextCompletion(
                        systemPrompt = "Health check.",
                        userPrompt = probePrompt
                    )
                }
            } else {
                null
            }
        )
        return if (res.isSuccess) {
            Result.success(quotaTracker.connectionStatus.value)
        } else {
            Result.failure(res.exceptionOrNull() ?: IllegalStateException("AI check failed."))
        }
    }

    suspend fun generateSignalBriefing(
        snapshot: MultiTimeframeMarketSnapshot,
        report: TechnicalAnalysisReport,
        userCommand: String? = null
    ): Result<AiSignalBriefing> {
        val contextBlock = buildMarketContextPrompt(snapshot, report)
        val prompt = """
            You are MN SHAH, an advanced autonomous AI trading robot assistant with a calm, disciplined, futuristic male robotic persona.
            
            STRICT RULES:
            1. NEVER fabricate market prices, candles, or indicator values. Use ONLY the real measurements below.
            2. NEVER promise guaranteed accuracy or win rates.
            3. If the Quantitative Engine already concluded WAIT or UNCERTAIN, or if FeedStatus is Disconnected/Delayed, you MUST keep direction as WAIT or UNCERTAIN and explain why honestly.
            4. In `urduSpeech`, write natural, clear Urdu script (اردو) suitable for a male Urdu Text-to-Speech voice (2-4 sentences). Address the trader respectfully as a disciplined AI assistant.
            5. In `romanUrduSummary`, provide a clear Roman Urdu version of the same message.
            
            USER COMMAND: ${userCommand ?: "Analyze current market and provide next 1-minute candle assessment."}
            
            $contextBlock
        """.trimIndent()

        val backupJsonSystemPrompt = """
            You are MN SHAH, a disciplined AI trading assistant. Return valid JSON ONLY with keys:
            "direction" (one of "UP", "DOWN", "UNCERTAIN", "WAIT"),
            "confidence" (integer 0..90),
            "urduSpeech" (natural Urdu script اردو for TTS),
            "romanUrduSummary" (concise Roman Urdu summary),
            "evidencePoints" (array of 3-5 concise strings),
            "riskNote" (short risk disclaimer).
        """.trimIndent()

        return try {
            val quotaResult = quotaTracker.executeWithSmartQuota(
                primaryCall = {
                    withTimeout(45_000L) {
                        primarySignalCaller?.invoke(prompt)
                            ?: (signalModel.generateContent(prompt).text
                                ?: throw IllegalStateException("AI returned an empty response."))
                    }
                },
                backupCall = if (backupProvider.isConfigured()) {
                    {
                        backupProvider.generateJsonCompletion(
                            systemPrompt = backupJsonSystemPrompt,
                            userPrompt = prompt
                        )
                    }
                } else {
                    null
                }
            )

            if (quotaResult.isFailure) {
                val err = quotaResult.exceptionOrNull()
                    ?: IllegalStateException("AI signal briefing failed.")
                Log.e(TAG, "AI signal briefing failed after smart quota handling", err)
                return Result.failure(err)
            }

            val (rawJson, providerUsed) = quotaResult.getOrThrow()
            val briefing = parseSignalJson(
                rawJson = rawJson,
                snapshot = snapshot,
                report = report,
                providerUsed = providerUsed
            )
            Result.success(briefing)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI signal briefing timed out", e)
            Result.failure(Exception("AI analysis timed out. Please retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI signal briefing failed", e)
            Result.failure(e)
        }
    }

    suspend fun answerGeneralCommand(
        userQuery: String,
        snapshot: MultiTimeframeMarketSnapshot?,
        report: TechnicalAnalysisReport?
    ): Result<Pair<String, String>> {
        val marketSummary = if (snapshot != null && report != null) {
            buildMarketContextPrompt(snapshot, report)
        } else {
            "No live market snapshot loaded yet."
        }
        val prompt = """
            You are MN SHAH, a futuristic autonomous Urdu-speaking AI trading robot assistant.
            Answer the user's question concisely using ONLY the real market metrics below. Never invent prices or guarantee profits.
            Return TWO lines separated by `||`:
            Line 1: Natural Urdu script (اردو) for TTS voice output (2 sentences max).
            Line 2: Roman Urdu / English summary for screen display.
            
            User query: $userQuery
            
            $marketSummary
        """.trimIndent()

        return try {
            val quotaResult = quotaTracker.executeWithSmartQuota(
                primaryCall = {
                    withTimeout(40_000L) {
                        val txt = primaryChatCaller?.invoke(prompt)
                            ?: chatModel.generateContent(prompt).text
                        txt?.trim()?.takeIf { it.isNotBlank() }
                            ?: throw IllegalStateException("AI assistant returned an empty reply.")
                    }
                },
                backupCall = if (backupProvider.isConfigured()) {
                    {
                        backupProvider.generateTextCompletion(
                            systemPrompt = "You are MN SHAH, an Urdu trading assistant. Reply with Line1(Urdu) || Line2(Roman Urdu).",
                            userPrompt = prompt
                        )
                    }
                } else {
                    null
                }
            )

            if (quotaResult.isFailure) {
                val err = quotaResult.exceptionOrNull()
                    ?: IllegalStateException("AI assistant unavailable.")
                Log.e(TAG, "AI command query failed", err)
                return Result.failure(err)
            }

            val (text, _) = quotaResult.getOrThrow()
            val parts = text.split("||", limit = 2)
            val urdu = parts.first().trim()
            val roman = parts.getOrNull(1)?.trim()?.ifBlank { urdu } ?: urdu
            Result.success(urdu to roman)
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("Voice assistant timed out. Try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI command query failed", e)
            Result.failure(e)
        }
    }

    private fun parseSignalJson(
        rawJson: String,
        snapshot: MultiTimeframeMarketSnapshot,
        report: TechnicalAnalysisReport,
        providerUsed: ActiveAiProvider
    ): AiSignalBriefing {
        val cleaned = rawJson.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val json = JSONObject(cleaned)

        val aiDirection = runCatching {
            SignalDirection.valueOf(json.getString("direction").uppercase())
        }.getOrDefault(report.quantitativeDirection)

        // Safety guardrail: if quantitative gate says WAIT due to missing/delayed/conflicting feed, never override into UP/DOWN
        val finalDirection = if (
            report.quantitativeDirection == SignalDirection.WAIT &&
            snapshot.status !is FeedStatus.Live
        ) {
            SignalDirection.WAIT
        } else {
            aiDirection
        }

        val aiConfidence = json.optInt("confidence", report.confidenceEstimate).coerceIn(0, 90)
        val finalConfidence = if (finalDirection == SignalDirection.WAIT || finalDirection == SignalDirection.UNCERTAIN) {
            aiConfidence.coerceAtMost(49)
        } else {
            aiConfidence
        }

        val evidenceArr = json.optJSONArray("evidencePoints")
        val evidenceList = buildList {
            if (evidenceArr != null) {
                for (i in 0 until evidenceArr.length()) {
                    val item = evidenceArr.optString(i).trim()
                    if (item.isNotBlank()) add(item)
                }
            }
        }.ifEmpty { report.technicalEvidence }

        return AiSignalBriefing(
            direction = finalDirection,
            confidence = finalConfidence,
            urduSpeech = json.optString("urduSpeech").ifBlank {
                "تجزیہ مکمل ہو گیا ہے۔ براہِ کرم اسکرین پر شواہد دیکھیں۔"
            },
            romanUrduSummary = json.optString("romanUrduSummary").ifBlank {
                "Tajziya mukammal ho gaya hai. Screen par technical evidence check karein."
            },
            evidencePoints = evidenceList,
            riskNote = json.optString("riskNote").ifBlank {
                "Market conditions can shift rapidly. Manual verification required; no guaranteed outcome."
            },
            providerUsed = providerUsed
        )
    }

    private fun buildMarketContextPrompt(
        snapshot: MultiTimeframeMarketSnapshot,
        report: TechnicalAnalysisReport
    ): String {
        val dec = snapshot.asset.decimals
        val statusText = when (val s = snapshot.status) {
            FeedStatus.Connecting -> "CONNECTING"
            is FeedStatus.Live -> "LIVE (${s.source}, state=${s.marketState})"
            is FeedStatus.Delayed -> "DELAYED (${s.source}, delay=${s.delaySeconds}s, reason=${s.reason})"
            is FeedStatus.Disconnected -> "DISCONNECTED (${s.reason})"
        }
        return buildString {
            appendLine("ASSET: ${snapshot.asset.symbol}")
            appendLine("SPOT PRICE: ${"%.${dec}f".format(snapshot.currentPrice)}")
            appendLine("FEED STATUS: $statusText")
            appendLine("QUANTITATIVE ASSESSMENT: ${report.quantitativeDirection.name} (score=${report.totalScore}, confidence=${report.confidenceEstimate}%)")
            if (report.insufficiencyReason != null) {
                appendLine("INSUFFICIENCY NOTE: ${report.insufficiencyReason}")
            }
            report.m1?.let { m1 ->
                appendLine("1M: RSI14=${"%.1f".format(m1.rsi14)}, MACD=${m1.macd.statusLabel}, EMA_BIAS=${m1.emaBias}, EMA9=${"%.${dec}f".format(m1.ema9)}, EMA21=${"%.${dec}f".format(m1.ema21)}, Pattern=${m1.candlePattern}, Structure=${m1.marketStructure}, Support=${"%.${dec}f".format(m1.supportResistance.nearestSupport)}, Resistance=${"%.${dec}f".format(m1.supportResistance.nearestResistance)}")
            }
            report.m5?.let { m5 ->
                appendLine("5M: RSI14=${"%.1f".format(m5.rsi14)}, MACD=${m5.macd.statusLabel}, EMA_BIAS=${m5.emaBias}, Structure=${m5.marketStructure}")
            }
            report.m15?.let { m15 ->
                appendLine("15M: RSI14=${"%.1f".format(m15.rsi14)}, MACD=${m15.macd.statusLabel}, EMA_BIAS=${m15.emaBias}, Structure=${m15.marketStructure}")
            }
        }
    }

    private companion object {
        const val TAG = "MnShahAiAnalyst"

        fun createTracker(appContext: Context): AiQuotaTracker {
            val backup = OkHttpBackupAiProvider()
            return AiQuotaTracker(
                store = DataStoreAiQuotaStore(appContext),
                backupProviderConfigured = { backup.isConfigured() },
                backupDailyLimit = backup.dailyQuotaLimit()
            )
        }
    }
}
