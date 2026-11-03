package com.ciro.saham

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.NumberFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CiroSahamApp()
        }
    }
}

// Format Rupiah Helper
fun formatRupiah(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    format.maximumFractionDigits = 0
    return format.format(amount).replace("Rp", "Rp ")
}

val DarkBg = Color(0xFF0B0E14)
val CardBg = Color(0xFF151B26)
val EmeraldGreen = Color(0xFF10B981)
val CoralRed = Color(0xFFEF4444)
val TextMuted = Color(0xFF8B949E)
val BorderColor = Color(0xFF212936)

@Composable
fun CiroSahamApp(viewModel: MainViewModel = viewModel()) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Dashboard", "Portofolio", "Pasar", "Analisis", "Radar")

    Scaffold(
        containerColor = DarkBg,
        bottomBar = {
            NavigationBar(
                containerColor = CardBg,
                tonalElevation = 8.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        label = { Text(title, fontSize = 11.sp) },
                        icon = {
                            when (index) {
                                0 -> Icon(Icons.Default.Dashboard, contentDescription = title)
                                1 -> Icon(Icons.Default.WorkHistory, contentDescription = title)
                                2 -> Icon(Icons.Default.CandlestickChart, contentDescription = title)
                                3 -> Icon(Icons.Default.Analytics, contentDescription = title)
                                else -> Icon(Icons.Default.Radar, contentDescription = title)
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = EmeraldGreen,
                            selectedTextColor = EmeraldGreen,
                            unselectedIconColor = TextMuted,
                            unselectedTextColor = TextMuted,
                            indicatorColor = Color(0xFF1F2937)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> DashboardScreen(viewModel, onNavigateToMarket = { selectedTab = 2 })
                1 -> PortfolioScreen(viewModel, onTrade = { sym ->
                    viewModel.selectStock(sym)
                    selectedTab = 2
                })
                2 -> MarketScreen(viewModel)
                3 -> AnalyticsScreen(viewModel)
                4 -> RadarScreen(viewModel, onPickStock = { sym ->
                    viewModel.selectStock(sym)
                    selectedTab = 2
                })
            }
        }
    }
}

// ================= 1. DASHBOARD SCREEN =================
@Composable
fun DashboardScreen(viewModel: MainViewModel, onNavigateToMarket: () -> Unit) {
    val cash by viewModel.virtualCash.collectAsState()
    val holdings by viewModel.holdings.collectAsState()
    val totalInvested = holdings.sumOf { it.totalInvested }
    val totalStockValue = holdings.sumOf { it.currentValue }
    val totalEquity = cash + totalStockValue
    val totalPnL = totalStockValue - totalInvested
    val totalPnLPct = if (totalInvested > 0) (totalPnL / totalInvested) * 100.0 else 0.0

    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Portofolio Virtual?") },
            text = { Text("Seluruh riwayat trading dan kepemilikan saham akan dihapus. Saldo kas akan kembali menjadi Rp 100.000.000.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetAccount()
                    showResetDialog = false
                }) {
                    Text("Ya, Reset", color = CoralRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("CiroSaham Terminal", fontSize = 14.sp, color = TextMuted)
                    Text("Portofolio Virtual", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                IconButton(onClick = { showResetDialog = true }) {
                    Icon(Icons.Default.RestartAlt, contentDescription = "Reset", tint = Color(0xFFF59E0B))
                }
            }
        }

        // Card Total Equity
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("TOTAL NILAI ASET (EQUITY)", fontSize = 12.sp, color = TextMuted, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(formatRupiah(totalEquity), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Sisa Kas Menganggur", fontSize = 11.sp, color = TextMuted)
                            Text(formatRupiah(cash), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF60A5FA))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Total Floating PnL", fontSize = 11.sp, color = TextMuted)
                            Text(
                                "${if (totalPnL >= 0) "+" else ""}${formatRupiah(totalPnL)} (${String.format("%.2f", totalPnLPct)}%)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (totalPnL >= 0) EmeraldGreen else CoralRed
                            )
                        }
                    }
                }
            }
        }

        // Equity Curve Chart
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("GRAFIK PERTUMBUHAN MODAL", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Text("Simulasi 30 Hari", fontSize = 11.sp, color = EmeraldGreen)
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // Canvas Kurva Naik Turun Portofolio
                    Canvas(modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)) {
                        val points = listOf(100.0, 99.2, 101.5, 100.8, 103.2, 102.7, 105.0, 104.2, 106.8, 108.5)
                        val maxP = points.maxOrNull() ?: 110.0
                        val minP = points.minOrNull() ?: 98.0
                        val w = size.width
                        val h = size.height

                        val path = Path()
                        points.forEachIndexed { i, p ->
                            val x = (i.toFloat() / (points.size - 1)) * w
                            val y = h - (((p - minP) / (maxP - minP)).toFloat() * h)
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }

                        drawPath(path, color = EmeraldGreen, style = Stroke(width = 4f))
                    }
                }
            }
        }

        // Quick Actions & Watchlist Ringkas
        item {
            Text("SAHAM POPULER", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("BBRI", "ANTM", "BBCA", "BREN", "BUMI").forEach { sym ->
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                viewModel.selectStock(sym)
                                onNavigateToMarket()
                            },
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(sym, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

// ================= 2. PORTFOLIO SCREEN =================
@Composable
fun PortfolioScreen(viewModel: MainViewModel, onTrade: (String) -> Unit) {
    val holdings by viewModel.holdings.collectAsState()
    val logs by viewModel.tradeLogs.collectAsState()
    var subTab by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Manajemen Portofolio", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(12.dp))

        TabRow(
            selectedTabIndex = subTab,
            containerColor = CardBg,
            contentColor = EmeraldGreen
        ) {
            Tab(selected = subTab == 0, onClick = { subTab = 0 }, text = { Text("Saham Aktif (${holdings.size})") })
            Tab(selected = subTab == 1, onClick = { subTab = 1 }, text = { Text("Riwayat Transaksi (${logs.size})") })
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (subTab == 0) {
            if (holdings.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Belum ada saham yang dibeli. Buka tab Pasar untuk mulai trading!", color = TextMuted, fontSize = 13.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(holdings) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(item.symbol, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text("${item.lot} Lot (${item.totalShares} lbr)", fontSize = 12.sp, color = TextMuted)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            "${if (item.unrealizedPnL >= 0) "+" else ""}${formatRupiah(item.unrealizedPnL)}",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (item.unrealizedPnL >= 0) EmeraldGreen else CoralRed
                                        )
                                        Text(
                                            "${if (item.unrealizedPnLPct >= 0) "+" else ""}${String.format("%.2f", item.unrealizedPnLPct)}%",
                                            fontSize = 12.sp,
                                            color = if (item.unrealizedPnLPct >= 0) EmeraldGreen else CoralRed
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Divider(color = BorderColor)
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Avg: Rp ${item.avgBuyPrice.toInt()}", fontSize = 12.sp, color = TextMuted)
                                        Text("Now: Rp ${item.currentPrice.toInt()}", fontSize = 12.sp, color = Color.White)
                                    }
                                    Button(
                                        onClick = { onTrade(item.symbol) },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F2937)),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                    ) {
                                        Text("Kelola / Jual", fontSize = 12.sp, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (logs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Belum ada riwayat transaksi.", color = TextMuted, fontSize = 13.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(logs) { log ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                if (log.action == "BUY") Color(0x2210B981) else Color(0x22EF4444),
                                                RoundedCornerShape(8.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(log.action, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (log.action == "BUY") EmeraldGreen else CoralRed)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(log.symbol, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Text("${log.lot} Lot @ Rp ${log.price.toInt()}", fontSize = 11.sp, color = TextMuted)
                                    }
                                }
                                if (log.action == "SELL") {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            "${if (log.realizedPnL >= 0) "+" else ""}${formatRupiah(log.realizedPnL)}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (log.realizedPnL >= 0) EmeraldGreen else CoralRed
                                        )
                                        Text("Realized", fontSize = 10.sp, color = TextMuted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ================= 3. MARKET DETAIL SCREEN =================
@Composable
fun MarketScreen(viewModel: MainViewModel) {
    val stockDetail by viewModel.selectedStock.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current

    var searchSymbol by remember { mutableStateOf("") }
    var orderType by remember { mutableStateOf("BUY") } // BUY or SELL
    var lotInput by remember { mutableStateOf("10") }
    var showTradeSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Search Input
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchSymbol,
                onValueChange = { searchSymbol = it },
                placeholder = { Text("Cari Saham (misal: BBCA, BBRI)", fontSize = 12.sp, color = TextMuted) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmeraldGreen,
                    unfocusedBorderColor = BorderColor,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            Button(
                onClick = {
                    if (searchSymbol.isNotBlank()) {
                        viewModel.selectStock(searchSymbol)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
            ) {
                Text("Cari")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EmeraldGreen)
            }
        } else if (stockDetail != null) {
            val d = stockDetail!!
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Harga
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(d.symbol, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("PT Bank Rakyat Indonesia (Persero) Tbk", fontSize = 11.sp, color = TextMuted)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Rp ${d.currentPrice.toInt()}", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                "${if (d.changePct >= 0) "+" else ""}${String.format("%.2f", d.changePct)}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (d.changePct >= 0) EmeraldGreen else CoralRed
                            )
                        }
                    }
                }

                // Candlestick Chart View
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("GRAFIK CANDLESTICK 3 BULAN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                            Spacer(modifier = Modifier.height(12.dp))

                            Canvas(modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)) {
                                if (d.candles.isNotEmpty()) {
                                    val candleList = d.candles.takeLast(25)
                                    val minPx = candleList.minOf { it.low }
                                    val maxPx = candleList.maxOf { it.high }
                                    val range = (maxPx - minPx).coerceAtLeast(1.0)
                                    val w = size.width / candleList.size

                                    candleList.forEachIndexed { i, c ->
                                        val x = i * w + (w / 2)
                                        val isUp = c.close >= c.open
                                        val color = if (isUp) EmeraldGreen else CoralRed

                                        val highY = size.height - (((c.high - minPx) / range).toFloat() * size.height)
                                        val lowY = size.height - (((c.low - minPx) / range).toFloat() * size.height)
                                        val openY = size.height - (((c.open - minPx) / range).toFloat() * size.height)
                                        val closeY = size.height - (((c.close - minPx) / range).toFloat() * size.height)

                                        // Wick
                                        drawLine(color, Offset(x, highY), Offset(x, lowY), strokeWidth = 2f)
                                        // Body
                                        val topBody = minOf(openY, closeY)
                                        val botBody = maxOf(openY, closeY)
                                        drawRect(
                                            color,
                                            topLeft = Offset(x - (w * 0.35f), topBody),
                                            size = Size(w * 0.7f, (botBody - topBody).coerceAtLeast(3f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 10-Level Order Book Ladder
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("LEVEL 2 ORDER BOOK DEPTH", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("BID (Lot)", modifier = Modifier.weight(1f), fontSize = 11.sp, color = EmeraldGreen, fontWeight = FontWeight.Bold)
                                Text("OFFER (Lot)", modifier = Modifier.weight(1f), fontSize = 11.sp, color = CoralRed, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            d.bids.zip(d.offers).forEach { (b, o) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${b.price.toInt()}", fontSize = 12.sp, color = EmeraldGreen, fontWeight = FontWeight.SemiBold)
                                        Text("${b.lot}", fontSize = 12.sp, color = Color.White)
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${o.lot}", fontSize = 12.sp, color = Color.White)
                                        Text("${o.price.toInt()}", fontSize = 12.sp, color = CoralRed, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Tombol Eksekusi Order
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                orderType = "BUY"
                                showTradeSheet = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("BELI VIRTUAL", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                orderType = "SELL"
                                showTradeSheet = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CoralRed),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("JUAL VIRTUAL", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showTradeSheet && stockDetail != null) {
        val sym = stockDetail!!.symbol
        val price = stockDetail!!.currentPrice
        val lot = lotInput.toIntOrNull() ?: 1
        val totalRp = lot * 100L * price

        AlertDialog(
            onDismissRequest = { showTradeSheet = false },
            title = { Text("$orderType $sym @ Rp ${price.toInt()}") },
            text = {
                Column {
                    Text("Masukkan jumlah lot:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = lotInput,
                        onValueChange = { lotInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Total: ${formatRupiah(totalRp)}", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (orderType == "BUY") {
                            viewModel.buyStock(sym, lot, price,
                                onSuccess = {
                                    Toast.makeText(context, "Sukses Beli $lot Lot $sym!", Toast.LENGTH_SHORT).show()
                                    showTradeSheet = false
                                },
                                onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                            )
                        } else {
                            viewModel.sellStock(sym, lot, price,
                                onSuccess = {
                                    Toast.makeText(context, "Sukses Jual $lot Lot $sym!", Toast.LENGTH_SHORT).show()
                                    showTradeSheet = false
                                },
                                onError = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (orderType == "BUY") EmeraldGreen else CoralRed)
                ) {
                    Text("Konfirmasi $orderType")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTradeSheet = false }) { Text("Batal") }
            }
        )
    }
}

// ================= 4. ANALYTICS SCREEN =================
@Composable
fun AnalyticsScreen(viewModel: MainViewModel) {
    val metrics by viewModel.metrics.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Analisis Performa Trading", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Statistik & Evaluasi Strategi", fontSize = 12.sp, color = TextMuted)
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("WIN RATE", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        Text("${String.format("%.1f", metrics.winRatePct)}%", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                        Text("${metrics.totalWins} Win / ${metrics.totalLosses} Loss", fontSize = 11.sp, color = TextMuted)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("PROFIT FACTOR", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        Text(String.format("%.2f", metrics.profitFactor), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF60A5FA))
                        Text("Rasio Cuan / Rugi", fontSize = 11.sp, color = TextMuted)
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("TOTAL REALIZED PnL", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Akumulasi Profit (+):", fontSize = 12.sp, color = TextMuted)
                        Text(formatRupiah(metrics.totalRealizedProfit), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Akumulasi Kerugian (-):", fontSize = 12.sp, color = TextMuted)
                        Text(formatRupiah(metrics.totalRealizedLoss), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CoralRed)
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("DIAGNOSIS & EVALUASI STRATEGI", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(metrics.evaluationNote, fontSize = 13.sp, color = Color.White, lineHeight = 20.sp)
                }
            }
        }
    }
}

// ================= 5. RADAR SCREEN =================
@Composable
fun RadarScreen(viewModel: MainViewModel, onPickStock: (String) -> Unit) {
    val stockDetail by viewModel.selectedStock.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Radar Niat Bandar", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Smart Money & Peta Kuadran Broker", fontSize = 12.sp, color = TextMuted)
        }

        if (stockDetail != null) {
            val d = stockDetail!!
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(d.symbol, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(d.tacticalSignal, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Foreign Flow: +Rp 80.3 Miliar (Net Buy)", fontSize = 12.sp, color = EmeraldGreen)
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("BEDAH NIAT PEMBELI:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Spacer(modifier = Modifier.height(4.dp))
                        d.buyerIntentList.forEach { line ->
                            Text(line, fontSize = 12.sp, color = Color.White, modifier = Modifier.padding(vertical = 2.dp))
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("BEDAH NIAT PENJUAL:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                        Spacer(modifier = Modifier.height(4.dp))
                        d.sellerIntentList.forEach { line ->
                            Text(line, fontSize = 12.sp, color = Color.White, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }
        }

        item {
            Text("RADAR SAHAM PILIHAN BANDAR", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            listOf(
                Pair("BBRI", "Smart Buyback JP Morgan (Rp 35 M) @ 2.805"),
                Pair("BUMI", "Smart Buyback UBS (Rp 23 M) @ 126"),
                Pair("PTRO", "Akumulasi BNI & Mandiri (Rp 41 M)"),
                Pair("ANTM", "Averaging Down Plat Merah (Wait & See)")
            ).forEach { (sym, desc) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onPickStock(sym) },
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(sym, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(desc, fontSize = 11.sp, color = TextMuted)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = "Pilih", tint = TextMuted)
                    }
                }
            }
        }
    }
}
