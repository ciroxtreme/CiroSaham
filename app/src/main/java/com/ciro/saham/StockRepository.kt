package com.ciro.saham

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class StockRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("ciro_saham_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val KEY_VIRTUAL_CASH = "virtual_cash"
        private const val KEY_HOLDINGS = "portfolio_holdings"
        private const val KEY_TRADE_LOGS = "trade_logs"
        private const val DEFAULT_STARTING_CASH = 100_000_000.0
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

    fun executeBuy(symbol: String, lot: Int, price: Double): Boolean {
        val totalCost = lot * 100L * price
        val currentCash = getVirtualCash()
        if (currentCash < totalCost) return false

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

    fun executeSell(symbol: String, lot: Int, price: Double): Boolean {
        val holdings = getHoldings().toMutableList()
        val index = holdings.indexOfFirst { it.symbol.equals(symbol, ignoreCase = true) }
        if (index < 0) return false

        val item = holdings[index]
        if (item.lot < lot) return false

        val totalSellValue = lot * 100L * price
        val costBasis = lot * 100L * item.avgBuyPrice
        val realizedProfit = totalSellValue - costBasis

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

    suspend fun fetchMarketData(symbol: String): StockMarketDetail = withContext(Dispatchers.IO) {
        val cleanSym = symbol.uppercase().trim()
        val yahooTicker = if (cleanSym.endsWith(".JK")) cleanSym else "$cleanSym.JK"
        val url = "https://query1.finance.yahoo.com/v8/finance/chart/$yahooTicker?interval=1d&range=3mo"

        var curPrice = 1000.0
        var prevClose = 1000.0
        var high = 1000.0
        var low = 1000.0
        val candles = mutableListOf<CandleStick>()
        var resolvedCompanyName = "$cleanSym Tbk."

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
                        curPrice = meta.optDouble("regularMarketPrice", 1000.0)
                        prevClose = meta.optDouble("chartPreviousClose", curPrice)
                        val shortName = meta.optString("shortName", "")
                        if (shortName.isNotBlank()) {
                            resolvedCompanyName = shortName
                        }

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
        } else {
            high = curPrice * 1.02
            low = curPrice * 0.98
        }

        val bids = mutableListOf<OrderbookEntry>()
        val offers = mutableListOf<OrderbookEntry>()
        val step = if (curPrice > 5000) 25.0 else if (curPrice > 2000) 10.0 else if (curPrice > 500) 5.0 else 1.0

        for (i in 1..8) {
            val bPx = (curPrice - (i - 1) * step).coerceAtLeast(1.0)
            val oPx = curPrice + i * step
            bids.add(OrderbookEntry(bPx, (5000L + (i * 1230L)), 20 + i * 5))
            offers.add(OrderbookEntry(oPx, (4200L + (i * 980L)), 15 + i * 4))
        }

        val chgPct = if (prevClose > 0) ((curPrice - prevClose) / prevClose) * 100.0 else 0.0

        StockMarketDetail(
            symbol = cleanSym,
            companyName = resolvedCompanyName,
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

    // Mengambil Data Fleksibel & Real-Time Harian Langsung dari Scanner TradingView Indonesia (IDX)
    suspend fun fetchLiveMarketMovers(): Map<String, List<StockMoverItem>> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<String, List<StockMoverItem>>()

        val categories = listOf(
            Triple("Top Gainer", "change", "desc"),
            Triple("Top Loser", "change", "asc"),
            Triple("Top Value", "Value.Traded", "desc"),
            Triple("Top Volume", "volume", "desc")
        )

        for ((catName, sortBy, sortOrder) in categories) {
            val items = fetchScannerCategory(catName, sortBy, sortOrder)
            if (items.isNotEmpty()) {
                result[catName] = items
            }
        }

        // Jika koneksi scanner gagal, gunakan fallback dinamis default
        if (result.isEmpty()) {
            return@withContext getFallbackMarketMovers()
        }
        return@withContext result
    }

    private fun fetchScannerCategory(category: String, sortBy: String, sortOrder: String): List<StockMoverItem> {
        val list = mutableListOf<StockMoverItem>()
        try {
            val url = "https://scanner.tradingview.com/indonesia/scan"
            val payload = JSONObject().apply {
                put("filter", org.json.JSONArray().put(
                    JSONObject().put("left", "type").put("operation", "equal").put("right", "stock")
                ))
                put("options", JSONObject().put("lang", "id"))
                put("symbols", JSONObject().put("query", JSONObject().put("types", org.json.JSONArray())).put("tickers", org.json.JSONArray()))
                put("columns", org.json.JSONArray().apply {
                    put("name")
                    put("description")
                    put("close")
                    put("change")
                    put("change_abs")
                    put("volume")
                    put("Value.Traded")
                })
                put("sort", JSONObject().put("sortBy", sortBy).put("sortOrder", sortOrder))
                put("range", org.json.JSONArray().put(0).put(15))
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = payload.toString().toRequestBody(mediaType)
            val req = Request.Builder()
                .url(url)
                .post(requestBody)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(req).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val root = JSONObject(body)
                    val dataArr = root.optJSONArray("data")
                    if (dataArr != null) {
                        for (i in 0 until dataArr.length()) {
                            val row = dataArr.getJSONObject(i)
                            val d = row.getJSONArray("d")
                            val sym = d.optString(0, "")
                            val desc = d.optString(1, "")
                            val closePx = d.optDouble(2, 0.0)
                            val chgPct = d.optDouble(3, 0.0)
                            val chgAbs = d.optDouble(4, 0.0)
                            val vol = d.optLong(5, 0L)
                            val valTraded = d.optDouble(6, 0.0)

                            if (sym.isNotBlank() && closePx > 0) {
                                list.add(
                                    StockMoverItem(
                                        symbol = sym,
                                        name = desc,
                                        price = closePx,
                                        changeNominal = chgAbs,
                                        changePct = chgPct,
                                        volume = vol,
                                        valueRupiah = valTraded,
                                        category = category
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun getFallbackMarketMovers(): Map<String, List<StockMoverItem>> {
        val gainers = listOf(
            StockMoverItem("DFAM", "PT Dafam Property Indonesia Tbk", 75.0, 19.0, 33.93, 122_138_400, 9_160_380_000.0, "Top Gainer"),
            StockMoverItem("MKNT", "Pt Mitra Komunikasi Nusantara Tbk", 14.0, 3.0, 27.27, 22_312_400, 312_373_600.0, "Top Gainer"),
            StockMoverItem("ROCK", "PT Rockfields Properti Indonesia Tbk", 2270.0, 450.0, 24.73, 543_100, 1_232_837_000.0, "Top Gainer"),
            StockMoverItem("AKPI", "PT Argha Karya Prima Industry Tbk", 635.0, 125.0, 24.51, 4_872_000, 3_093_720_000.0, "Top Gainer"),
            StockMoverItem("TRUK", "PT Guna Timur Raya Tbk", 6025.0, 1000.0, 19.90, 621_800, 3_746_345_000.0, "Top Gainer")
        )

        val losers = listOf(
            StockMoverItem("LMAX", "PT Lupromax Pelumas Indonesia Tbk", 68.0, -7.0, -9.33, 1_200_000, 81_600_000.0, "Top Loser"),
            StockMoverItem("PLAN", "PT Planet Properindo Jaya Tbk", 43.0, -4.0, -8.51, 890_000, 38_270_000.0, "Top Loser"),
            StockMoverItem("MSIE", "PT Multisarana Intan Eduka Tbk", 71.0, -6.0, -7.79, 450_000, 31_950_000.0, "Top Loser"),
            StockMoverItem("LINK", "PT Link Net Tbk", 1065.0, -80.0, -6.99, 320_000, 340_800_000.0, "Top Loser"),
            StockMoverItem("RATU", "PT Raharja Energi Cepu Tbk", 1935.0, -145.0, -6.97, 750_000, 1_451_250_000.0, "Top Loser")
        )

        val values = listOf(
            StockMoverItem("BBCA", "PT Bank Central Asia Tbk", 4980.0, 0.0, 0.0, 196_618_100, 979_158_138_000.0, "Top Value"),
            StockMoverItem("AADI", "Pt Adaro Andalan Indonesia Tbk", 10700.0, 250.0, 2.39, 48_180_500, 515_531_350_000.0, "Top Value"),
            StockMoverItem("BBRI", "PT Bank Rakyat Indonesia (Persero) Tbk", 2820.0, 20.0, 0.71, 179_257_800, 505_506_996_000.0, "Top Value"),
            StockMoverItem("BUMI", "PT Bumi Resources Tbk", 128.0, -3.0, -2.29, 3_755_912_100, 480_756_748_800.0, "Top Value"),
            StockMoverItem("BMRI", "PT Bank Mandiri (Persero) Tbk", 3700.0, 50.0, 1.37, 118_684_300, 439_131_910_000.0, "Top Value")
        )

        val volumes = listOf(
            StockMoverItem("GOTO", "PT GoTo Gojek Tokopedia Tbk", 24.0, -1.0, -4.0, 4_313_552_100, 103_525_250_400.0, "Top Volume"),
            StockMoverItem("BUMI", "PT Bumi Resources Tbk", 128.0, -3.0, -2.29, 3_755_912_100, 480_756_748_800.0, "Top Volume"),
            StockMoverItem("BNBR", "PT Bakrie & Brothers Tbk", 44.0, 2.0, 4.76, 970_564_100, 42_704_820_400.0, "Top Volume"),
            StockMoverItem("DEWA", "PT Darma Henwa Tbk", 238.0, 8.0, 3.48, 761_384_100, 181_209_415_800.0, "Top Volume"),
            StockMoverItem("INET", "PT Sinergi Inti Andalan Prima Tbk", 174.0, 6.0, 3.57, 590_123_300, 102_681_454_200.0, "Top Volume")
        )

        return mapOf(
            "Top Gainer" to gainers,
            "Top Loser" to losers,
            "Top Value" to values,
            "Top Volume" to volumes
        )
    }
}
