package com.netbanding.app.ui.detail

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.netbanding.app.R
import com.netbanding.app.domain.model.Package
import com.netbanding.app.domain.model.PricePoint
import com.netbanding.app.domain.usecase.CalculateTrueCost
import com.netbanding.app.ui.components.formatPricePeriode
import com.netbanding.app.ui.components.formatQuota
import com.netbanding.app.ui.components.formatValidity
import com.netbanding.app.ui.components.periodeFor
import com.netbanding.app.ui.home.Types
import com.netbanding.app.ui.home.formatIdr

@Composable
fun PackageDetailSheet(
    pkg: Package,
    history: List<PricePoint>,
    onFavorite: () -> Unit,
    onCompare: () -> Unit,
    isCompared: Boolean = false,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val uri = LocalUriHandler.current
    val cs = MaterialTheme.colorScheme
    val cost = remember(pkg) {
        CalculateTrueCost().invoke(
            basePrice = pkg.basePrice,
            taxInclusive = pkg.taxInclusive,
            deviceRentalFee = pkg.deviceRentalFee,
            installFee = pkg.installFee,
            speedMbps = pkg.speedMbps,
        )
    }
    Column(modifier = modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(pkg.ispName, style = MaterialTheme.typography.bodySmall, color = cs.primary)
                Text(pkg.name, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold))
            }
            if (onDismiss != null) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.base_price, formatIdr(pkg.basePrice)),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Total: ", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                Text(
                    formatIdr(cost.monthlyTotal),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                )
                Text("/${periodeFor(pkg.validityDays)}", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (pkg.type == Types.CELLULAR) {
                InfoBox(
                    title = stringResource(R.string.quota_title),
                    value = pkg.quotaMb?.let(::formatQuota) ?: "-",
                    icon = { Icon(Icons.Filled.Storage, null, tint = cs.tertiary) },
                    modifier = Modifier.weight(1f),
                )
                InfoBox(
                    title = stringResource(R.string.active_title),
                    value = pkg.validityDays?.let { formatValidity(it) } ?: "-",
                    icon = { Icon(Icons.Filled.CalendarMonth, null, tint = cs.tertiary) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                InfoBox(
                    title = stringResource(R.string.speed_title),
                    value = pkg.speedMbps?.let { "$it Mbps" } ?: "-",
                    icon = { Icon(Icons.Filled.Storage, null, tint = cs.tertiary) },
                    modifier = Modifier.weight(1f),
                )
                InfoBox(
                    title = stringResource(R.string.active_title),
                    value = pkg.validityDays?.let { formatValidity(it) } ?: "Bulanan",
                    icon = { Icon(Icons.Filled.CalendarMonth, null, tint = cs.tertiary) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        HistorySection(history)
        Text(
            "ⓘ  " + stringResource(R.string.disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val btnPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            Button(
                onClick = onCompare,
                colors = ButtonDefaults.buttonColors(containerColor = cs.primary),
                shape = RoundedCornerShape(14.dp),
                contentPadding = btnPadding,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    if (isCompared) "✓ ${stringResource(R.string.compare_added)}" else "+ ${stringResource(R.string.compare_add)}",
                    maxLines = 1,
                )
            }
            OutlinedButton(
                onClick = onFavorite,
                shape = RoundedCornerShape(14.dp),
                contentPadding = btnPadding,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    if (pkg.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp),
                )
                Text(stringResource(R.string.save), maxLines = 1)
            }
        }
        val context = LocalContext.current
        OutlinedButton(
            onClick = { uri.openUri(pkg.ispWebsite) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.open_provider))
        }
    }
}

@Composable
private fun InfoBox(title: String, value: String, icon: @Composable () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(cs.surfaceVariant).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        icon()
        Text(title, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun HistorySection(history: List<PricePoint>, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(cs.surfaceVariant).padding(12.dp),
        ) {
            if (history.size < 2) {
                Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            } else {
                Column {
                    StepChart(history, modifier = Modifier.fillMaxWidth().height(80.dp))
                    Text(
                        stringResource(
                            R.string.history_range,
                            formatCompact(history.first().price),
                            formatCompact(history.last().price),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private fun formatCompact(amount: Long): String =
    if (amount >= 1_000_000 && amount % 1_000_000 == 0L) "${amount / 1_000_000}jt"
    else if (amount >= 1000) "${amount / 1000}rb"
    else amount.toString()

@Composable
private fun StepChart(points: List<PricePoint>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val prices = remember(points) { points.map { it.price } }
    Canvas(modifier = modifier) {
        val min = prices.min()
        val max = prices.max()
        val span = (max - min).coerceAtLeast(1)
        fun x(i: Int) = size.width * i / (prices.size - 1).coerceAtLeast(1)
        fun y(v: Long) = size.height - (size.height - 8) * (v - min) / span
        for (i in 1 until prices.size) {
            val x0 = x(i - 1)
            val x1 = x(i)
            val yPrev = y(prices[i - 1])
            val yCur = y(prices[i])
            drawLine(color, Offset(x0, yPrev), Offset(x1, yPrev), strokeWidth = 4f)
            if (yCur != yPrev) drawLine(color, Offset(x1, yPrev), Offset(x1, yCur), strokeWidth = 4f)
        }
        prices.forEachIndexed { i, v -> drawCircle(color, radius = 6f, center = Offset(x(i), y(v))) }
    }
}
