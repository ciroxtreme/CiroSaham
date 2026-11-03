package com.ciro.saham

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
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

// Light & Fresh Theme Palettes
val WhiteBg = Color(0xFFFFFFFF)
val SurfaceBg = Color(0xFFF8FAFC)
val CardBorder = Color(0xFFE2E8F0)
val PrimaryGreen = Color(0xFF00A86B) // Aksen hijau segar khas Sekuritas
val LightGreenText = Color(0xFF00A86B)
val LightRedText = Color(0xFFE11D48)
val DarkText = Color(0xFF0F172A)
val MutedText = Color(0xFF64748B)
val DividerGray = Color(0xFFF1F5F9)

// Helper Brand Color Avatar Emiten
fun getAvatarColor(symbol: String): Color {
    return when (symbol.uppercase()) {
        "DFAM" -> Color(0xFF0D9488) // Hijau Teal
        "MKNT" -> Color(0xFF7C3AED) // Ungu Modern
        "ROCK" -> Color(0xFF1E293B) // Hitam Elegan
        "AKPI" -> Color(0xFF0284C7) // Biru Cerah
        "BLTA" -> Color(0xFFDC2626) // Merah Bendera Tanker
        "BBRI" -> Color(0xFF0284C7) // Biru BRI
        "BBCA" -> Color(0xFF1D4ED8) // Biru BCA
        "BMRI" -> Color(0xFFD97706) // Kuning Emas Mandiri
        "ANTM" -> Color(0xFF059669) // Hijau Antam
        "BUMI" -> Color(0xFFB45309) // Emas Bumi
        "PTRO" -> Color(0xFF4338CA) // Indigo Petrosea
        "GOTO" -> Color(0xFF16A34A) // Hijau Gojek
        else -> Color(0xFF475569)
    }
}

@Composable
fun CiroSahamApp(viewModel: MainViewModel = viewModel()) {
    var selectedTab by remember { mutableStateOf(2) } // Default tab: Pasar (sesuai foto kedua)
    val tabs = listOf("Dashboard", "Portofolio", "Pasar", "Analisis", "Radar")

    Scaffold(
        containerColor = WhiteBg,
        bottomBar = {
            NavigationBar(
                containerColor = WhiteBg,
                tonalElevation = 8.dp
            ) {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        label = {
                            Text(
                                title,
                                fontSize = 11.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        },
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
                            selectedIconColor = PrimaryGreen,
                            selectedTextColor = PrimaryGreen,
                            unselectedIconColor = MutedText,
                            unselectedTextColor = MutedText,
                            indicatorColor = Color(0xFFE6F4EA)
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
                .background(WhiteBg)
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
            title = { Text("Reset Portofolio Virtual?", color = DarkText) },
            text = { Text("Seluruh riwayat trading dan kepemilikan saham akan dihapus. Saldo kas kembali menjadi Rp 100.000.000.", color = MutedText) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetAccount()
                    showResetDialog = false
                }) {
                    Text("Ya, Reset", color = LightRedText, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Batal", color = MutedText)
                }
            },
            containerColor = WhiteBg
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
                    Text("CiroSaham Terminal", fontSize = 13.sp, color = MutedText, fontWeight = FontWeight.Medium)
                    Text("Portofolio Virtual", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DarkText)
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
                colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("TOTAL NILAI ASET (EQUITY)", fontSize = 12.sp, color = MutedText, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(formatRupiah(totalEquity), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = DarkText)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Sisa Kas Menganggur", fontSize = 11.sp, color = MutedText)
                            Text(formatRupiah(cash), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Total Floating PnL", fontSize = 11.sp, color = MutedText)
                            Text(
                                "${if (totalPnL >= 0) "+" else ""}${formatRupiah(totalPnL)} (${String.format("%.2f", totalPnLPct)}%)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (totalPnL >= 0) LightGreenText else LightRedText
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Saham yang Dimiliki", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkText)
                TextButton(onClick = onNavigateToMarket) {
                    Text("Beli Saham Baru >", color = PrimaryGreen, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (holdings.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Belum ada portofolio saham. Silakan pilih di tab Pasar!", color = MutedText, fontSize = 13.sp)
                    }
                }
            }
        } else {
            items(holdings) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = WhiteBg),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(getAvatarColor(item.symbol), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(item.symbol.take(2), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(item.symbol, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = DarkText)
                                Text("${item.lot} Lot (${item.totalShares} lembar)", fontSize = 11.sp, color = MutedText)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatRupiah(item.currentValue), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = DarkText)
                            Text(
                                "${if (item.unrealizedPnLPct >= 0) "+" else ""}${String.format("%.2f", item.unrealizedPnLPct)}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (item.unrealizedPnLPct >= 0) LightGreenText else LightRedText
                            )
                        }
                    }
                }
            }
        }
    }
}

// ================= 2. PORTOFOLIO SCREEN =================
@Composable
fun PortfolioScreen(viewModel: MainViewModel, onTrade: (String) -> Unit) {
    val holdings by viewModel.holdings.collectAsState()
    val logs by viewModel.tradeLogs.collectAsState()
    var selectedSubTab by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = WhiteBg,
            contentColor = PrimaryGreen,
            divider = { Divider(color = DividerGray) }
        ) {
            Tab(selected = selectedSubTab == 0, onClick = { selectedSubTab = 0 }, text = { Text("Posisi Aktif (${holdings.size})") })
            Tab(selected = selectedSubTab == 1, onClick = { selectedSubTab = 1 }, text = { Text("Riwayat Transaksi") })
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedSubTab == 0) {
            if (holdings.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Belum ada posisi terbuka.", color = MutedText, fontSize = 13.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(holdings) { item ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = WhiteBg),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier.size(38.dp).background(getAvatarColor(item.symbol), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(item.symbol.take(2), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(item.symbol, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                            Text("${item.lot} Lot @ Rp ${item.avgBuyPrice.toInt()}", fontSize = 11.sp, color = MutedText)
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(formatRupiah(item.currentValue), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                        Text(
                                            "${if (item.unrealizedPnLPct >= 0) "+" else ""}${String.format("%.2f", item.unrealizedPnLPct)}%",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (item.unrealizedPnLPct >= 0) LightGreenText else LightRedText
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Divider(color = DividerGray)
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Floating PnL: ${formatRupiah(item.unrealizedPnL)}", fontSize = 11.sp, color = if (item.unrealizedPnL >= 0) LightGreenText else LightRedText)
                                        Text("Harga Kini: Rp ${item.currentPrice.toInt()}", fontSize = 11.sp, color = MutedText)
                                    }
                                    Button(
                                        onClick = { onTrade(item.symbol) },
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Kelola / Jual", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
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
                    Text("Belum ada riwayat transaksi.", color = MutedText, fontSize = 13.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(logs) { log ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = WhiteBg),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                if (log.action == "BUY") Color(0xFFE6F4EA) else Color(0xFFFFE4E6),
                                                RoundedCornerShape(8.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(log.action, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (log.action == "BUY") LightGreenText else LightRedText)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(log.symbol, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                        Text("${log.lot} Lot @ Rp ${log.price.toInt()}", fontSize = 11.sp, color = MutedText)
                                    }
                                }
                                if (log.action == "SELL") {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            "${if (log.realizedPnL >= 0) "+" else ""}${formatRupiah(log.realizedPnL)}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (log.realizedPnL >= 0) LightGreenText else LightRedText
                                        )
                                        Text("Realized", fontSize = 10.sp, color = MutedText)
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

// ================= 3. MARKET SCREEN (PERSIS FOTO KEDUA) =================
@Composable
fun MarketScreen(viewModel: MainViewModel) {
    val stockDetail by viewModel.selectedStock.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isMoversLoading by viewModel.isMoversLoading.collectAsState()
    val marketMovers by viewModel.marketMovers.collectAsState()
    val context = LocalContext.current

    var selectedCategory by remember { mutableStateOf("Top Gainer") }
    val categories = listOf("Top Gainer", "Top Loser", "Top Value", "Top Volume")

    var searchSymbol by remember { mutableStateOf("") }
    var orderType by remember { mutableStateOf("BUY") }
    var lotInput by remember { mutableStateOf("10") }
    var showTradeSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WhiteBg)
    ) {
        // Bar Kategori Tab Atas (Top Gainer, Top Loser, Top Value, Top Volume) Sesuai Foto Kedua
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            categories.forEach { cat ->
                val isSelected = selectedCategory == cat
                Column(
                    modifier = Modifier
                        .clickable { selectedCategory = cat }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = cat,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) PrimaryGreen else MutedText
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .height(3.dp)
                                .width(64.dp)
                                .background(PrimaryGreen, RoundedCornerShape(2.dp))
                        )
                    } else {
                        Spacer(modifier = Modifier.height(3.dp))
                    }
                }
            }
        }

        Divider(color = DividerGray, thickness = 1.dp)

        // Area Pencarian Cepat
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchSymbol,
                onValueChange = { searchSymbol = it },
                placeholder = { Text("Cari Saham (misal: BBCA, DFAM)", fontSize = 12.sp, color = MutedText) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryGreen,
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = DarkText,
                    unfocusedTextColor = DarkText
                )
            )
            Button(
                onClick = {
                    if (searchSymbol.isNotBlank()) {
                        viewModel.selectStock(searchSymbol)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Cari", fontWeight = FontWeight.Bold)
            }
            IconButton(
                onClick = { viewModel.loadLiveMarketMovers() },
                modifier = Modifier.size(42.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh Live", tint = PrimaryGreen)
            }
        }

        // Tampilan List Saham Kategori (Sesuai Foto Kedua)
        val currentList = marketMovers[selectedCategory] ?: emptyList()

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items(currentList) { item ->
                StockMoverRow(
                    item = item,
                    onClick = {
                        viewModel.selectStock(item.symbol)
                    }
                )
                Divider(color = DividerGray, thickness = 1.dp)
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { /* Aksi selengkapnya */ }
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Selengkapnya >",
                        color = PrimaryGreen,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }

            // Bagian Detail Chart & Orderbook Saham Terpilih
            if (stockDetail != null) {
                val d = stockDetail!!
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header Detail Terpilih
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(44.dp).background(getAvatarColor(d.symbol), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(d.symbol.take(2), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(d.symbol, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                        Text(d.companyName, fontSize = 11.sp, color = MutedText)
                                    }
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Rp ${d.currentPrice.toInt()}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                    Text(
                                        "${if (d.changePct >= 0) "+" else ""}${String.format("%.2f", d.changePct)}%",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (d.changePct >= 0) LightGreenText else LightRedText
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Text("GRAFIK CANDLESTICK 3 BULAN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MutedText)
                            Spacer(modifier = Modifier.height(8.dp))

                            // Candlestick View
                            Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                                if (d.candles.isNotEmpty()) {
                                    val candleList = d.candles.takeLast(25)
                                    val minPx = candleList.minOf { it.low }
                                    val maxPx = candleList.maxOf { it.high }
                                    val range = (maxPx - minPx).coerceAtLeast(1.0)
                                    val w = size.width / candleList.size

                                    candleList.forEachIndexed { i, c ->
                                        val x = i * w + (w / 2)
                                        val isUp = c.close >= c.open
                                        val color = if (isUp) PrimaryGreen else LightRedText

                                        val highY = size.height - (((c.high - minPx) / range).toFloat() * size.height)
                                        val lowY = size.height - (((c.low - minPx) / range).toFloat() * size.height)
                                        val openY = size.height - (((c.open - minPx) / range).toFloat() * size.height)
                                        val closeY = size.height - (((c.close - minPx) / range).toFloat() * size.height)

                                        drawLine(color, Offset(x, highY), Offset(x, lowY), strokeWidth = 2.5f)
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

                            Spacer(modifier = Modifier.height(14.dp))
                            Text("LEVEL 2 ORDER BOOK DEPTH", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MutedText)
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text("BID (Lot)", modifier = Modifier.weight(1f), fontSize = 11.sp, color = PrimaryGreen, fontWeight = FontWeight.Bold)
                                Text("OFFER (Lot)", modifier = Modifier.weight(1f), fontSize = 11.sp, color = LightRedText, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            d.bids.zip(d.offers).take(6).forEach { (b, o) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${b.price.toInt()}", fontSize = 12.sp, color = PrimaryGreen, fontWeight = FontWeight.SemiBold)
                                        Text("${b.lot}", fontSize = 12.sp, color = DarkText)
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${o.lot}", fontSize = 12.sp, color = DarkText)
                                        Text("${o.price.toInt()}", fontSize = 12.sp, color = LightRedText, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            // Tombol Beli / Jual Virtual
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        orderType = "BUY"
                                        showTradeSheet = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryGreen),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("BELI VIRTUAL", fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        orderType = "SELL"
                                        showTradeSheet = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = LightRedText),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("JUAL VIRTUAL", fontWeight = FontWeight.Bold)
                                }
                            }
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
            title = { Text("$orderType $sym @ Rp ${price.toInt()}", color = DarkText, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Masukkan jumlah lot:", color = MutedText)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = lotInput,
                        onValueChange = { lotInput = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryGreen,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = DarkText,
                            unfocusedTextColor = DarkText
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Total: ${formatRupiah(totalRp)}", fontWeight = FontWeight.Bold, color = DarkText)
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
                    colors = ButtonDefaults.buttonColors(containerColor = if (orderType == "BUY") PrimaryGreen else LightRedText)
                ) {
                    Text("Konfirmasi $orderType")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTradeSheet = false }) { Text("Batal", color = MutedText) }
            },
            containerColor = WhiteBg
        )
    }
}

// Komponen Baris Saham Sesuai Foto Kedua
@Composable
fun StockMoverRow(item: StockMoverItem, onClick: () -> Unit) {
    val isPositive = item.changeNominal >= 0
    val changeColor = if (isPositive) LightGreenText else LightRedText

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(getAvatarColor(item.symbol), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.symbol.take(2),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = item.symbol,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText
                )
                Text(
                    text = item.name,
                    fontSize = 12.sp,
                    color = MutedText,
                    maxLines = 1
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${item.price.toInt()}",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText
            )
            val nominalStr = if (isPositive) "+${item.changeNominal.toInt()}" else "${item.changeNominal.toInt()}"
            val pctStr = String.format("%.2f", item.changePct)
            Text(
                text = "$nominalStr(+$pctStr%)",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = changeColor
            )
        }
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
            Text("Analisis Performa Trading", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DarkText)
            Text("Statistik & Evaluasi Strategi", fontSize = 12.sp, color = MutedText)
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("WIN RATE", fontSize = 11.sp, color = MutedText, fontWeight = FontWeight.Bold)
                        Text("${String.format("%.1f", metrics.winRatePct)}%", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PrimaryGreen)
                        Text("${metrics.totalWins} Win / ${metrics.totalLosses} Loss", fontSize = 11.sp, color = MutedText)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("PROFIT FACTOR", fontSize = 11.sp, color = MutedText, fontWeight = FontWeight.Bold)
                        Text(String.format("%.2f", metrics.profitFactor), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
                        Text("Rasio Cuan / Rugi", fontSize = 11.sp, color = MutedText)
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("TOTAL REALIZED PnL", fontSize = 11.sp, color = MutedText, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Akumulasi Profit (+):", fontSize = 12.sp, color = MutedText)
                        Text(formatRupiah(metrics.totalRealizedProfit), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryGreen)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Akumulasi Kerugian (-):", fontSize = 12.sp, color = MutedText)
                        Text(formatRupiah(metrics.totalRealizedLoss), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LightRedText)
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("DIAGNOSIS & EVALUASI STRATEGI", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(metrics.evaluationNote, fontSize = 13.sp, color = DarkText, lineHeight = 20.sp)
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
            Text("Radar Niat Bandar", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = DarkText)
            Text("Smart Money & Peta Kuadran Broker", fontSize = 12.sp, color = MutedText)
        }

        if (stockDetail != null) {
            val d = stockDetail!!
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(d.symbol, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkText)
                            Text(d.tacticalSignal, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryGreen)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Foreign Flow: +Rp 80.3 Miliar (Net Buy)", fontSize = 12.sp, color = PrimaryGreen, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("BEDAH NIAT PEMBELI:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MutedText)
                        Spacer(modifier = Modifier.height(4.dp))
                        d.buyerIntentList.forEach { line ->
                            Text(line, fontSize = 12.sp, color = DarkText, modifier = Modifier.padding(vertical = 2.dp))
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("BEDAH NIAT PENJUAL:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MutedText)
                        Spacer(modifier = Modifier.height(4.dp))
                        d.sellerIntentList.forEach { line ->
                            Text(line, fontSize = 12.sp, color = DarkText, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }
        }

        item {
            Text("RADAR SAHAM PILIHAN BANDAR", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkText)
            Spacer(modifier = Modifier.height(8.dp))
            listOf(
                Pair("BBRI", "Smart Buyback JP Morgan (Rp 35 M) @ 2.805"),
                Pair("DFAM", "Spekulatif Top Gainer (+33.93%)"),
                Pair("ROCK", "Breakout Rally Saham Properti (+24.73%)"),
                Pair("ANTM", "Averaging Down Plat Merah (Wait & See)")
            ).forEach { (sym, desc) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onPickStock(sym) },
                    colors = CardDefaults.cardColors(containerColor = WhiteBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(36.dp).background(getAvatarColor(sym), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(sym.take(2), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(sym, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                Text(desc, fontSize = 11.sp, color = MutedText)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = "Pilih", tint = MutedText)
                    }
                }
            }
        }
    }
}
