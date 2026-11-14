package com.example.data.ai

enum class AiConnectionStatus(val label: String) {
    ONLINE("ONLINE"),
    OFFLINE("OFFLINE"),
    QUOTA_EXCEEDED("QUOTA EXCEEDED")
}

enum class AiErrorKind {
    DAILY_QUOTA_EXHAUSTED,
    TEMPORARY_RATE_LIMIT,
    TRANSIENT_NETWORK_OR_SERVER,
    OFFLINE_NETWORK,
    AUTH_OR_CONFIG,
    UNKNOWN
}

enum class ActiveAiProvider(val displayName: String) {
    GEMINI("Gemini (Firebase AI)"),
    BACKUP("Backup AI Provider"),
    QUANTITATIVE_ONLY("Quantitative Engine (Offline Fallback)")
}

data class AiUsageStats(
    val dateUtc: String = "",
    val requestsToday: Int = 0,
    val successfulResponsesToday: Int = 0,
    val temporaryRetriesToday: Int = 0,
    val quotaErrorsToday: Int = 0,
    val backupCallsToday: Int = 0,
    val backupQuotaLimit: Int = 50,
    val backupConfigured: Boolean = false,
    val backupQuotaExhaustedToday: Boolean = false,
    val cooldownUntilEpochMs: Long = 0L,
    val activeProvider: ActiveAiProvider = ActiveAiProvider.GEMINI,
    val lastErrorSummary: String? = null,
    val lastUpdatedEpochMs: Long = 0L
) {
    fun isQuotaCooldownActive(nowEpochMs: Long): Boolean = cooldownUntilEpochMs > nowEpochMs

    fun formatCooldownRemaining(nowEpochMs: Long): String {
        val diffMs = cooldownUntilEpochMs - nowEpochMs
        if (diffMs <= 0L) return "Reset Ready"
        val totalMinutes = (diffMs / 60_000L).coerceAtLeast(1L)
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return if (hours > 0L) "${hours}h ${minutes}m" else "${minutes}m"
    }
}

data class AiQuotaAlert(
    val urduTitle: String,
    val urduMessage: String,
    val romanUrduMessage: String,
    val cooldownRemainingText: String,
    val errorCodeLabel: String,
    val backupProviderNote: String
)

class DailyQuotaExhaustedException(
    val alert: AiQuotaAlert,
    cause: Throwable? = null
) : Exception(alert.romanUrduMessage, cause)
