package com.example.data.market

enum class TradingAsset(
    val symbol: String,
    val yahooTicker: String,
    val fallbackTicker: String?,
    val decimals: Int,
    val category: String
) {
    EUR_USD("EUR/USD", "EURUSD=X", null, 5, "FX Major"),
    GBP_USD("GBP/USD", "GBPUSD=X", null, 5, "FX Major"),
    USD_JPY("USD/JPY", "USDJPY=X", null, 3, "FX Major"),
    XAU_USD("XAU/USD", "GC=F", "XAUUSD=X", 2, "Gold Futures/Spot"),
    NZD_USD("NZD/USD (OTC)", "NZDUSD=X", null, 5, "FX Live / OTC"),
    USD_NGN("USD/NGN (OTC)", "USDNGN=X", "NGN=X", 2, "FX Live / OTC"),
    AXS_USD("Axie Infinity (OTC)", "AXS-USD", null, 3, "Crypto 24/7 (AXS/USD)");

    companion object {
        fun fromSymbol(symbol: String): TradingAsset {
            val normalized = symbol.trim()
            return entries.firstOrNull { it.symbol.equals(normalized, ignoreCase = true) }
                ?: when {
                    normalized.startsWith("NZD", ignoreCase = true) -> NZD_USD
                    normalized.contains("NGN", ignoreCase = true) -> USD_NGN
                    normalized.contains("AXS", ignoreCase = true) ||
                        normalized.contains("AXIE", ignoreCase = true) -> AXS_USD
                    else -> EUR_USD
                }
        }
    }
}

enum class Timeframe(
    val interval: String,
    val label: String,
    val range: String,
    val durationMs: Long
) {
    M1("1m", "1M", "1d", 60_000L),
    M5("5m", "5M", "5d", 300_000L),
    M15("15m", "15M", "5d", 900_000L)
}

data class Candle(
    val timestampMs: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long
) {
    val isBullish: Boolean get() = close >= open
}

sealed interface FeedStatus {
    data object Connecting : FeedStatus
    data class Live(
        val source: String,
        val marketState: String,
        val lastTickEpochMs: Long
    ) : FeedStatus

    data class Delayed(
        val source: String,
        val marketState: String,
        val delaySeconds: Long,
        val reason: String
    ) : FeedStatus

    data class Disconnected(
        val reason: String,
        val lastAttemptEpochMs: Long
    ) : FeedStatus
}

data class MultiTimeframeMarketSnapshot(
    val asset: TradingAsset,
    val currentPrice: Double,
    val previousClose: Double?,
    val candles1m: List<Candle>,
    val candles5m: List<Candle>,
    val candles15m: List<Candle>,
    val status: FeedStatus,
    val fetchedAtEpochMs: Long,
    val candleExpiryEpochMs: Long
) {
    fun candlesFor(timeframe: Timeframe): List<Candle> = when (timeframe) {
        Timeframe.M1 -> candles1m
        Timeframe.M5 -> candles5m
        Timeframe.M15 -> candles15m
    }
}
