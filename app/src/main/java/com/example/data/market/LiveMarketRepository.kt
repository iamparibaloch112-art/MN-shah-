package com.example.data.market

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class LiveMarketRepository(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()
) {
    suspend fun fetchMultiTimeframeSnapshot(asset: TradingAsset): MultiTimeframeMarketSnapshot =
        withContext(Dispatchers.IO) {
            val nowMs = System.currentTimeMillis()
            val nextMinuteBoundary = ((nowMs / 60_000L) + 1L) * 60_000L

            try {
                val (m1Result, m5Result, m15Result) = coroutineScope {
                    val d1 = async { fetchYahooChartWithFallback(asset, Timeframe.M1) }
                    val d5 = async { fetchYahooChartWithFallback(asset, Timeframe.M5) }
                    val d15 = async { fetchYahooChartWithFallback(asset, Timeframe.M15) }
                    Triple(d1.await(), d5.await(), d15.await())
                }

                val candles1m = m1Result.candles
                val candles5m = m5Result.candles
                val candles15m = m15Result.candles
                val latestPrice = m1Result.regularMarketPrice
                    ?: candles1m.lastOrNull()?.close
                    ?: m5Result.regularMarketPrice
                    ?: candles5m.lastOrNull()?.close

                if (latestPrice == null || latestPrice <= 0.0) {
                    return@withContext trySpotFallbackOrDisconnected(asset, nowMs, nextMinuteBoundary, "Empty candle stream from primary feed")
                }

                val lastCandleTime = candles1m.lastOrNull()?.timestampMs
                    ?: (m1Result.regularMarketTimeSec?.times(1000L))
                    ?: nowMs
                val ageSeconds = ((nowMs - lastCandleTime).coerceAtLeast(0L)) / 1000L
                val marketState = m1Result.marketState ?: "UNKNOWN"

                val status = when {
                    marketState.equals("CLOSED", ignoreCase = true) ||
                        marketState.equals("POSTPOST", ignoreCase = true) -> {
                        FeedStatus.Delayed(
                            source = "Yahoo Finance (${m1Result.tickerUsed})",
                            marketState = marketState,
                            delaySeconds = ageSeconds,
                            reason = "Market session is currently $marketState (last tick ${ageSeconds}s ago)"
                        )
                    }
                    ageSeconds > 180L -> {
                        FeedStatus.Delayed(
                            source = "Yahoo Finance (${m1Result.tickerUsed})",
                            marketState = marketState,
                            delaySeconds = ageSeconds,
                            reason = "1m candle feed delayed by ${ageSeconds}s"
                        )
                    }
                    else -> {
                        FeedStatus.Live(
                            source = "Yahoo Finance (${m1Result.tickerUsed})",
                            marketState = marketState,
                            lastTickEpochMs = lastCandleTime
                        )
                    }
                }

                MultiTimeframeMarketSnapshot(
                    asset = asset,
                    currentPrice = latestPrice,
                    previousClose = m1Result.previousClose ?: m5Result.previousClose,
                    candles1m = candles1m,
                    candles5m = candles5m,
                    candles15m = candles15m,
                    status = status,
                    fetchedAtEpochMs = nowMs,
                    candleExpiryEpochMs = nextMinuteBoundary
                )
            } catch (e: Exception) {
                trySpotFallbackOrDisconnected(
                    asset = asset,
                    nowMs = nowMs,
                    nextMinuteBoundary = nextMinuteBoundary,
                    primaryError = e.localizedMessage ?: "Network unavailable"
                )
            }
        }

    private suspend fun fetchYahooChartWithFallback(
        asset: TradingAsset,
        timeframe: Timeframe
    ): ChartFetchResult {
        val primary = try {
            fetchYahooChart(asset.yahooTicker, timeframe)
        } catch (primaryErr: Exception) {
            val fallback = asset.fallbackTicker
            if (fallback != null) {
                return fetchYahooChart(fallback, timeframe)
            } else {
                throw primaryErr
            }
        }
        val fallback = asset.fallbackTicker
        if (primary.candles.size < 15 && fallback != null) {
            val fallbackResult = runCatching { fetchYahooChart(fallback, timeframe) }.getOrNull()
            if (fallbackResult != null && fallbackResult.candles.size > primary.candles.size) {
                return fallbackResult
            }
        }
        return primary
    }

    private fun fetchYahooChart(ticker: String, timeframe: Timeframe): ChartFetchResult {
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/$ticker" +
            "?interval=${timeframe.interval}&range=${timeframe.range}&includePrePost=true"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) MNShahBot/1.0")
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Market feed HTTP ${response.code} for $ticker")
            }
            val body = response.body?.string() ?: throw IOException("Empty response from $ticker")
            val root = JSONObject(body)
            val chart = root.getJSONObject("chart")
            if (!chart.isNull("error")) {
                val err = chart.getJSONObject("error")
                throw IOException(err.optString("description", "Yahoo chart API error"))
            }
            val results = chart.getJSONArray("result")
            if (results.length() == 0) {
                throw IOException("No chart results for $ticker")
            }

            val first = results.getJSONObject(0)
            val meta = first.getJSONObject("meta")
            val regularMarketPrice = meta.optDouble("regularMarketPrice").takeIf { !it.isNaN() && it > 0.0 }
            val previousClose = meta.optDouble("chartPreviousClose").takeIf { !it.isNaN() && it > 0.0 }
            val marketState = meta.optString("marketState", "REGULAR")
            val regularMarketTimeSec = meta.optLong("regularMarketTime", 0L).takeIf { it > 0L }

            val timestamps = first.optJSONArray("timestamp")
            val indicators = first.optJSONObject("indicators")
            val quoteArr = indicators?.optJSONArray("quote")
            val quote = if (quoteArr != null && quoteArr.length() > 0) quoteArr.getJSONObject(0) else null

            val opens = quote?.optJSONArray("open")
            val highs = quote?.optJSONArray("high")
            val lows = quote?.optJSONArray("low")
            val closes = quote?.optJSONArray("close")
            val volumes = quote?.optJSONArray("volume")

            val candles = mutableListOf<Candle>()
            if (timestamps != null && opens != null && highs != null && lows != null && closes != null) {
                for (i in 0 until timestamps.length()) {
                    if (opens.isNull(i) || highs.isNull(i) || lows.isNull(i) || closes.isNull(i)) continue
                    val o = opens.optDouble(i)
                    val h = highs.optDouble(i)
                    val l = lows.optDouble(i)
                    val c = closes.optDouble(i)
                    if (o.isNaN() || h.isNaN() || l.isNaN() || c.isNaN() || o <= 0.0 || c <= 0.0) continue
                    val v = if (volumes != null && !volumes.isNull(i)) volumes.optLong(i, 0L) else 0L
                    val tsMs = timestamps.getLong(i) * 1000L
                    candles.add(Candle(tsMs, o, h, l, c, v))
                }
            }

            return ChartFetchResult(
                tickerUsed = ticker,
                regularMarketPrice = regularMarketPrice,
                previousClose = previousClose,
                marketState = marketState,
                regularMarketTimeSec = regularMarketTimeSec,
                candles = candles.takeLast(120)
            )
        }
    }

    private fun trySpotFallbackOrDisconnected(
        asset: TradingAsset,
        nowMs: Long,
        nextMinuteBoundary: Long,
        primaryError: String
    ): MultiTimeframeMarketSnapshot {
        if (asset == TradingAsset.AXS_USD) {
            try {
                val request = Request.Builder()
                    .url("https://api.coingecko.com/api/v3/simple/price?ids=axie-infinity&vs_currencies=usd&include_last_updated_at=true")
                    .header("Accept", "application/json")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = JSONObject(response.body?.string().orEmpty())
                        val axsObj = json.optJSONObject("axie-infinity")
                        val spot = axsObj?.optDouble("usd", 0.0) ?: 0.0
                        if (spot > 0.0) {
                            val updatedUnix = (axsObj?.optLong("last_updated_at", nowMs / 1000L) ?: (nowMs / 1000L)) * 1000L
                            val delaySec = ((nowMs - updatedUnix).coerceAtLeast(0L)) / 1000L
                            return MultiTimeframeMarketSnapshot(
                                asset = asset,
                                currentPrice = spot,
                                previousClose = null,
                                candles1m = emptyList(),
                                candles5m = emptyList(),
                                candles15m = emptyList(),
                                status = FeedStatus.Delayed(
                                    source = "CoinGecko Spot (AXS/USD)",
                                    marketState = "SPOT_ONLY",
                                    delaySeconds = delaySec,
                                    reason = "Primary candlestick feed offline ($primaryError). Real crypto spot only; OHLC candles unavailable."
                                ),
                                fetchedAtEpochMs = nowMs,
                                candleExpiryEpochMs = nextMinuteBoundary
                            )
                        }
                    }
                }
            } catch (_: Exception) {
                // Fall through to Disconnected state
            }
        } else if (asset != TradingAsset.XAU_USD) {
            try {
                val request = Request.Builder()
                    .url("https://open.er-api.com/v6/latest/USD")
                    .header("Accept", "application/json")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = JSONObject(response.body?.string().orEmpty())
                        if (json.optString("result") == "success") {
                            val rates = json.getJSONObject("rates")
                            val spot = when (asset) {
                                TradingAsset.EUR_USD -> {
                                    val eurPerUsd = rates.optDouble("EUR", 0.0)
                                    if (eurPerUsd > 0.0) 1.0 / eurPerUsd else 0.0
                                }
                                TradingAsset.GBP_USD -> {
                                    val gbpPerUsd = rates.optDouble("GBP", 0.0)
                                    if (gbpPerUsd > 0.0) 1.0 / gbpPerUsd else 0.0
                                }
                                TradingAsset.USD_JPY -> rates.optDouble("JPY", 0.0)
                                TradingAsset.NZD_USD -> {
                                    val nzdPerUsd = rates.optDouble("NZD", 0.0)
                                    if (nzdPerUsd > 0.0) 1.0 / nzdPerUsd else 0.0
                                }
                                TradingAsset.USD_NGN -> rates.optDouble("NGN", 0.0)
                                TradingAsset.XAU_USD, TradingAsset.AXS_USD -> 0.0
                            }
                            if (spot > 0.0) {
                                val updatedUnix = json.optLong("time_last_update_unix", nowMs / 1000L) * 1000L
                                val delaySec = ((nowMs - updatedUnix).coerceAtLeast(0L)) / 1000L
                                return MultiTimeframeMarketSnapshot(
                                    asset = asset,
                                    currentPrice = spot,
                                    previousClose = null,
                                    candles1m = emptyList(),
                                    candles5m = emptyList(),
                                    candles15m = emptyList(),
                                    status = FeedStatus.Delayed(
                                        source = "Open Exchange Rates (Spot Only)",
                                        marketState = "SPOT_ONLY",
                                        delaySeconds = delaySec,
                                        reason = "Primary candlestick feed offline ($primaryError). Spot rate only; OHLC candles unavailable."
                                    ),
                                    fetchedAtEpochMs = nowMs,
                                    candleExpiryEpochMs = nextMinuteBoundary
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Fall through to Disconnected state
            }
        }

        return MultiTimeframeMarketSnapshot(
            asset = asset,
            currentPrice = 0.0,
            previousClose = null,
            candles1m = emptyList(),
            candles5m = emptyList(),
            candles15m = emptyList(),
            status = FeedStatus.Disconnected(
                reason = primaryError,
                lastAttemptEpochMs = nowMs
            ),
            fetchedAtEpochMs = nowMs,
            candleExpiryEpochMs = nextMinuteBoundary
        )
    }

    private data class ChartFetchResult(
        val tickerUsed: String,
        val regularMarketPrice: Double?,
        val previousClose: Double?,
        val marketState: String?,
        val regularMarketTimeSec: Long?,
        val candles: List<Candle>
    )
}
