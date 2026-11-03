package com.ciro.saham

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class StockRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("ciro_saham_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Key Constants
    companion object {
        private const val KEY_VIRTUAL_CASH = "virtual_cash"
        private const val KEY_HOLDINGS = "portfolio_holdings"
        private const val KEY_TRADE_LOGS = "trade_logs"
        private const val DEFAULT_STARTING_CASH = 100_000_000.0 // 100 Juta
    }

    fun getVirtualCash(): Double {
        return prefs.getString(KEY_VIRTUAL_CASH, DEFAULT_STARTING_CASH.toString())?.toDoubleOrNull() ?: DEFAULT_STARTING_CASH
    }

    fun setVirtualCash(amount: Double) {
        prefs.edit().putString(KEY_VIRTUAL_CASH, amount.toString()).apply()
    }

    fun getHoldings(): List<PortfolioHolding> {
        val json = prefs.getString(KEY_HOLDINGS, null) ?: return emptyList()
        val type = object : TypeToken<List<PortfolioHolding>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun saveHoldings(list: List<PortfolioHolding>) {
        val json = gson.toJson(list)
        prefs.edit().putString(KEY_HOLDINGS, json).apply()
    }

    fun getTradeLogs(): List<TradeLog> {
        val json = prefs.getString(KEY_TRADE_LOGS, null) ?: return emptyList()
        val type = object : TypeToken<List<TradeLog>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun addTradeLog(log: TradeLog) {
        val current = getTradeLogs().toMutableList()
        current.add(0, log)
        prefs.edit().putString(KEY_TRADE_LOGS, gson.toJson(current)).apply()
    }

    fun resetPortfolio() {
        prefs.edit()
            .putString(KEY_VIRTUAL_CASH, DEFAULT_STARTING_CASH.toString())
            .remove(KEY_HOLDINGS)
            .remove(KEY_TRADE_LOGS)
            .apply()
    }

    // Eksekusi Beli Saham Virtual
    fun executeBuy(symbol: String, lot: Int, price: Double): Boolean {
        val totalCost = lot * 100L * price
        val currentCash = getVirtualCash()
        if (currentCash < totalCost) return false // Saldo kurang

        setVirtualCash(currentCash - totalCost)

        val holdings = getHoldings().toMutableList()
        val existingIndex = holdings.indexOfFirst { it.symbol.equals(symbol, ignoreCase = true) }

        if (existingIndex >= 0) {
            val old = holdings[existingIndex]
            val totalOldShares = old.totalShares
            val totalNewShares = lot * 100L
            val combinedShares = totalOldShares + totalNewShares
            val combinedAvgPrice = ((totalOldShares * old.avgBuyPrice) + (totalNewShares * price)) / combinedShares

            holdings[existingIndex] = old.copy(
                lot = (combinedShares / 100).toInt(),
                avgBuyPrice = combinedAvgPrice,
                currentPrice = price
            )
        } else {
            holdings.add(
                PortfolioHolding(
                    symbol = symbol.uppercase(),
                    lot = lot,
                    avgBuyPrice = price,
                    currentPrice = price
                )
            )
        }

        saveHoldings(holdings)
        addTradeLog(TradeLog(symbol = symbol.uppercase(), action = "BUY", lot = lot, price = price))
        return true
    }

    // Eksekusi Jual Saham Virtual
    fun executeSell(symbol: String, lot: Int, price: Double): Boolean {
        val holdings = getHoldings().toMutableList()
        val index = holdings.indexOfFirst { it.symbol.equals(symbol, ignoreCase = true) }
        if (index < 0) return false

        val item = holdings[index]
        if (item.lot < lot) return false // Lot jual melebihi kepemilikan

        val totalSellValue = lot * 100L * price
        val costBasis = lot * 100L * item.avgBuyPrice
        val realizedProfit = totalSellValue - costBasis

        // Update Kas
        setVirtualCash(getVirtualCash() + totalSellValue)

        if (item.lot == lot) {
            holdings.removeAt(index)
        } else {
            holdings[index] = item.copy(lot = item.lot - lot, currentPrice = price)
        }

        saveHoldings(holdings)
        addTradeLog(
            TradeLog(
                symbol = symbol.uppercase(),
                action = "SELL",
                lot = lot,
                price = price,
                realizedPnL = realizedProfit
            )
        )
        return true
    }

    // Ambil Data Live Market & Chart Candle (Yahoo Finance / Stock Provider)
    suspend fun fetchMarketData(symbol: String): StockMarketDetail = withContext(Dispatchers.IO) {
        val cleanSym = symbol.uppercase().trim()
        val yahooTicker = if (cleanSym.endsWith(".JK")) cleanSym else "$cleanSym.JK"
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/$yahooTicker?interval=1d&range=3mo"

        var curPrice = 2800.0
        var prevClose = 2800.0
        var high = 2850.0
        var low = 2780.0
        val candles = mutableListOf<CandleStick>()

        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val root = JSONObject(body)
                    val chart = root.getJSONObject("chart")
                    val resultArr = chart.getJSONArray("result")
                    if (resultArr.length() > 0) {
                        val result = resultArr.getJSONObject(0)
                        val meta = result.getJSONObject("meta")
                        curPrice = meta.optDouble("regularMarketPrice", 2800.0)
                        prevClose = meta.optDouble("chartPreviousClose", curPrice)

                        val timestamps = result.optJSONArray("timestamp")
                        val indicators = result.getJSONObject("indicators")
                        val quote = indicators.getJSONArray("quote").getJSONObject(0)
                        val opens = quote.optJSONArray("open")
                        val highs = quote.optJSONArray("high")
                        val lows = quote.optJSONArray("low")
                        val closes = quote.optJSONArray("close")
                        val volumes = quote.optJSONArray("volume")

                        if (timestamps != null && closes != null) {
                            for (i in 0 until timestamps.length()) {
                                val t = timestamps.optLong(i)
                                val c = closes.optDouble(i, 0.0)
                                val o = opens?.optDouble(i, c) ?: c
                                val h = highs?.optDouble(i, c) ?: c
                                val l = lows?.optDouble(i, c) ?: c
                                val v = volumes?.optLong(i, 0L) ?: 0L
                                if (c > 0) {
                                    candles.add(CandleStick(t, o, h, l, c, v))
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (candles.isNotEmpty()) {
            high = candles.maxOf { it.high }
            low = candles.minOf { it.low }
        }

        // Generate Realistis Orderbook 10 Ladder
        val bids = mutableListOf<OrderbookEntry>()
        val offers = mutableListOf<OrderbookEntry>()
        val step = if (curPrice > 5000) 25.0 else if (curPrice > 2000) 10.0 else 5.0

        for (i in 1..8) {
            val bPx = curPrice - (i - 1) * step
            val oPx = curPrice + i * step
            bids.add(OrderbookEntry(bPx, (5000L + (i * 1230L)), 20 + i * 5))
            offers.add(OrderbookEntry(oPx, (4200L + (i * 980L)), 15 + i * 4))
        }

        val chgPct = if (prevClose > 0) ((curPrice - prevClose) / prevClose) * 100.0 else 0.0

        StockMarketDetail(
            symbol = cleanSym,
            currentPrice = curPrice,
            prevClose = prevClose,
            high = high,
            low = low,
            changePct = chgPct,
            bids = bids,
            offers = offers,
            candles = candles,
            bandarStatus = if (chgPct >= 0) "Accumulation" else "Distribution",
            top3AccPct = 34.5,
            netForeignFlow = if (chgPct >= 0) 45.2 else -18.7,
            buyerIntentList = listOf(
                "👑 [BK] J.P. Morgan ➔ 💎 SMART BUYBACK (Serok Bawah)",
                "👑 [ZP] Maybank ➔ 🟢 FRESH ENTRY (Modal Segar)",
                "👑 [CC] Mandiri Sekuritas ➔ 🩹 AVERAGING DOWN (Nambal Nyangkut)"
            ),
            sellerIntentList = listOf(
                "🔻 [XL] Stockbit ➔ ✂️ CUT LOSS (Ritel Buang Barang)",
                "🔻 [AK] UBS ➔ 🏦 DISTRIBUSI BARANG KUNO (Paus Sepuh)"
            ),
            tacticalSignal = if (chgPct >= 0) "🚀 ACCUMULATION / ENTRY ZONE" else "⚠️ CAUTION / WAIT & SEE"
        )
    }
}
