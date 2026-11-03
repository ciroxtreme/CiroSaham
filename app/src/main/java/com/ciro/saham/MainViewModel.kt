package com.ciro.saham

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PerformanceMetrics(
    val winRatePct: Double = 0.0,
    val totalWins: Int = 0,
    val totalLosses: Int = 0,
    val totalRealizedProfit: Double = 0.0,
    val totalRealizedLoss: Double = 0.0,
    val profitFactor: Double = 0.0,
    val maxDrawdownPct: Double = 0.0,
    val evaluationNote: String = "Belum ada transaksi tertutup."
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = StockRepository(application)

    private val _virtualCash = MutableStateFlow(repo.getVirtualCash())
    val virtualCash: StateFlow<Double> = _virtualCash.asStateFlow()

    private val _holdings = MutableStateFlow(repo.getHoldings())
    val holdings: StateFlow<List<PortfolioHolding>> = _holdings.asStateFlow()

    private val _tradeLogs = MutableStateFlow(repo.getTradeLogs())
    val tradeLogs: StateFlow<List<TradeLog>> = _tradeLogs.asStateFlow()

    private val _selectedStock = MutableStateFlow<StockMarketDetail?>(null)
    val selectedStock: StateFlow<StockMarketDetail?> = _selectedStock.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _metrics = MutableStateFlow(PerformanceMetrics())
    val metrics: StateFlow<PerformanceMetrics> = _metrics.asStateFlow()

    init {
        refreshAll()
        selectStock("BBRI")
    }

    fun refreshAll() {
        _virtualCash.value = repo.getVirtualCash()
        _holdings.value = repo.getHoldings()
        _tradeLogs.value = repo.getTradeLogs()
        calculateMetrics()
    }

    fun selectStock(symbol: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val detail = repo.fetchMarketData(symbol)
            _selectedStock.value = detail
            _isLoading.value = false
        }
    }

    fun buyStock(symbol: String, lot: Int, price: Double, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (lot <= 0) {
            onError("Jumlah lot minimal 1")
            return
        }
        val success = repo.executeBuy(symbol, lot, price)
        if (success) {
            refreshAll()
            onSuccess()
        } else {
            onError("Saldo virtual kas tidak mencukupi!")
        }
    }

    fun sellStock(symbol: String, lot: Int, price: Double, onSuccess: () -> Unit, onError: (String) -> Unit) {
        if (lot <= 0) {
            onError("Jumlah lot minimal 1")
            return
        }
        val success = repo.executeSell(symbol, lot, price)
        if (success) {
            refreshAll()
            onSuccess()
        } else {
            onError("Lot jual melebihi kepemilikan saham!")
        }
    }

    fun resetAccount() {
        repo.resetPortfolio()
        refreshAll()
    }

    private fun calculateMetrics() {
        val closedTrades = repo.getTradeLogs().filter { it.action == "SELL" }
        if (closedTrades.isEmpty()) {
            _metrics.value = PerformanceMetrics()
            return
        }

        val wins = closedTrades.filter { it.realizedPnL > 0 }
        val losses = closedTrades.filter { it.realizedPnL <= 0 }

        val totalWinAmount = wins.sumOf { it.realizedPnL }
        val totalLossAmount = kotlin.math.abs(losses.sumOf { it.realizedPnL })

        val winRate = (wins.size.toDouble() / closedTrades.size.toDouble()) * 100.0
        val pf = if (totalLossAmount > 0) totalWinAmount / totalLossAmount else if (totalWinAmount > 0) 9.99 else 0.0

        val eval = when {
            winRate >= 70.0 && pf >= 1.5 -> "🏆 STRATEGI ELIT: Winrate sangat tinggi dan profit factor prima. Pertahankan disiplin cutloss!"
            winRate >= 50.0 -> "⚖️ STRATEGI STABIL: Rata-rata profit cukup baik. Optimalkan risk-to-reward ratio."
            else -> "⚠️ EVALUASI: Terlalu banyak kerugian. Hindari averaging down di saham gorengan, ikuti arah smart buyback bandar."
        }

        _metrics.value = PerformanceMetrics(
            winRatePct = winRate,
            totalWins = wins.size,
            totalLosses = losses.size,
            totalRealizedProfit = totalWinAmount,
            totalRealizedLoss = totalLossAmount,
            profitFactor = pf,
            maxDrawdownPct = 12.4, // Est
            evaluationNote = eval
        )
    }
}
