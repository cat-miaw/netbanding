package com.netbanding.app.ui.detail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.netbanding.app.R
import com.netbanding.app.domain.model.Package
import com.netbanding.app.domain.model.PricePoint
import com.netbanding.app.domain.usecase.CalculateTrueCost
import com.netbanding.app.ui.home.formatIdr

/** Blueprint 7.6 detail sheet: True Cost breakdown + history + disclaimer + source. */
@Composable
fun PackageDetailSheet(
    pkg: Package,
    history: List<PricePoint>,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uri = LocalUriHandler.current
    val cost = remember(pkg) {
        CalculateTrueCost().invoke(
            basePrice = pkg.basePrice,
            taxInclusive = pkg.taxInclusive,
            deviceRentalFee = pkg.deviceRentalFee,
            installFee = pkg.installFee,
            speedMbps = pkg.speedMbps,
        )
    }
    Column(modifier = modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(pkg.name, style = MaterialTheme.typography.headlineSmall)
        Text(pkg.ispName, style = MaterialTheme.typography.labelLarge)
        Text(stringResource(R.string.base_price, formatIdr(pkg.basePrice)))
        if (!pkg.taxInclusive) Text(stringResource(R.string.ppn_line, formatIdr(cost.taxedBase - pkg.basePrice)))
        if (pkg.deviceRentalFee > 0) Text(stringResource(R.string.rental_line, formatIdr(pkg.deviceRentalFee)))
        Text(
            stringResource(R.string.monthly_total, formatIdr(cost.monthlyTotal)),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            if (pkg.installFee == null) stringResource(R.string.install_unknown)
            else stringResource(R.string.install_line, formatIdr(pkg.installFee)),
        )
        Text(stringResource(R.string.first_month, formatIdr(cost.firstMonthTotal)))
        cost.pricePerMbps?.let { Text(stringResource(R.string.per_mbps, formatIdr(it))) }
        HistorySection(history)
        Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall)
        Button(onClick = { uri.openUri(pkg.sourceUrl) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.open_provider))
        }
        OutlinedButton(
            onClick = onFavorite,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(if (pkg.isFavorite) R.string.unfavorite else R.string.favorite),
            )
        }
    }
}

@Composable
private fun HistorySection(history: List<PricePoint>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.history_title), style = MaterialTheme.typography.titleMedium)
        if (history.size < 2) {
            Text(
                stringResource(R.string.history_empty),
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            StepChart(history, modifier = Modifier.fillMaxWidth().height(120.dp))
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

private fun formatCompact(amount: Long): String =
    if (amount >= 1_000_000 && amount % 1_000_000 == 0L) "${amount / 1_000_000}jt"
    else if (amount >= 1000) "${amount / 1000}rb"
    else amount.toString()

/**
 * Step-line chart on raw Canvas: no chart dependency, negligible APK impact.
 * Flat segments between recordings (blueprint 4.5: the chart is a step line).
 */
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
