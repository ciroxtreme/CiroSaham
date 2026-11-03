package com.ciro.saham

data class PortfolioHolding(
    val symbol: String,
    val lot: Int,
    val avgBuyPrice: Double,
    val currentPrice: Double
) {
    val totalShares: Long get() = lot * 100L
    val totalInvested: Double get() = totalShares * avgBuyPrice
    val currentValue: Double get() = totalShares * currentPrice
    val unrealizedPnL: Double get() = currentValue - totalInvested
    val unrealizedPnLPct: Double get() = if (totalInvested > 0) (unrealizedPnL / totalInvested) * 100.0 else 0.0
}

data class TradeLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val symbol: String,
    val action: String, // "BUY" or "SELL"
    val lot: Int,
    val price: Double,
    val realizedPnL: Double = 0.0
)

data class CandleStick(
    val time: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long
)

data class OrderbookEntry(
    val price: Double,
    val lot: Long,
    val queue: Int
)

data class StockMarketDetail(
    val symbol: String,
    val companyName: String = "",
    val currentPrice: Double,
    val prevClose: Double,
    val high: Double,
    val low: Double,
    val changePct: Double,
    val bids: List<OrderbookEntry> = emptyList(),
    val offers: List<OrderbookEntry> = emptyList(),
    val candles: List<CandleStick> = emptyList(),
    val bandarStatus: String = "Neutral",
    val top3AccPct: Double = 0.0,
    val netForeignFlow: Double = 0.0,
    val buyerIntentList: List<String> = emptyList(),
    val sellerIntentList: List<String> = emptyList(),
    val tacticalSignal: String = "Wait & See"
)

data class StockMoverItem(
    val symbol: String,
    val name: String,
    val price: Double,
    val changeNominal: Double,
    val changePct: Double,
    val volume: Long = 0,
    val valueRupiah: Double = 0.0,
    val category: String = "Top Gainer" // "Top Gainer", "Top Loser", "Top Value", "Top Volume"
)
