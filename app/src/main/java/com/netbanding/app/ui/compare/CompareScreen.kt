package com.netbanding.app.ui.compare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netbanding.app.R
import com.netbanding.app.domain.model.Package
import com.netbanding.app.ui.components.formatPricePeriode
import com.netbanding.app.ui.components.formatQuota
import com.netbanding.app.ui.home.formatIdr

/** Broadband (monthly, validity null) ranks as a 30-day cycle for "longest" wins. */
const val MONTH_RANK_DAYS = 30

/** Total is only a fair fight when every package bills on the same cycle. */
fun totalComparable(items: List<Package>): Boolean =
    items.map { it.validityDays }.toSet().size <= 1

fun longestDays(items: List<Package>): Int =
    items.maxOf { it.validityDays ?: MONTH_RANK_DAYS }

/** Cheapest rupiah-per-MB; null when nobody states a quota. */
fun bestPerGb(items: List<Package>): Double? =
    items.mapNotNull { p ->
        p.quotaMb?.takeIf { it > 0 }?.let { p.monthlyTotal.toDouble() / it }
    }.minOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareRoute(
    viewModel: CompareViewModel,
    topInset: Dp,
    bottomInset: Dp,
    onBrowse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.items.size < 2) {
        Column(
            modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.CompareArrows,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Text(
                stringResource(R.string.compare_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            OutlinedButton(onClick = onBrowse, shape = RoundedCornerShape(14.dp)) {
                Text(stringResource(R.string.browse_packages))
            }
        }
    } else {
        val items = state.items
        val cheapest = items.minOf { it.monthlyTotal }
        val fastest = items.mapNotNull { it.speedMbps }.maxOrNull()
        val bestValue = items.mapNotNull { it.pricePerMbps }.minOrNull()
        val perGb = bestPerGb(items)
        val longest = longestDays(items)
        val fairTotal = totalComparable(items)
        val hasSpeed = items.any { it.speedMbps != null }
        val hasQuota = items.any { (it.quotaMb ?: 0) > 0 }
        val unknownShort = stringResource(R.string.install_unknown_short)
        val ppnYes = stringResource(R.string.compare_included)
        val ppnNo = stringResource(R.string.compare_excluded)
        val monthlyLabel = stringResource(R.string.validity_monthly)
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = topInset, end = 16.dp, bottom = bottomInset),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items.forEach { pkg ->
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(pkg.ispName, style = MaterialTheme.typography.labelSmall)
                                Text(pkg.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    formatPricePeriode(pkg.monthlyTotal, pkg.validityDays),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                TextButton(onClick = { viewModel.remove(pkg.id) }) {
                                    Text(stringResource(R.string.compare_remove))
                                }
                            }
                        }
                    }
                }
            }
            item {
                CompareRow(stringResource(R.string.compare_monthly), items,
                    { formatPricePeriode(it.monthlyTotal, it.validityDays) },
                    { fairTotal && it.monthlyTotal == cheapest })
                if (!fairTotal) {
                    Text(
                        stringResource(R.string.compare_mixed_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (hasSpeed) {
                item {
                    CompareRow(stringResource(R.string.compare_speed), items,
                        { it.speedMbps?.let { s -> "$s Mbps" } ?: "-" },
                        { it.speedMbps != null && it.speedMbps == fastest })
                }
                item {
                    CompareRow(stringResource(R.string.compare_per_mbps), items,
                        { it.pricePerMbps?.let(::formatIdr) ?: "-" },
                        { it.pricePerMbps != null && it.pricePerMbps == bestValue })
                }
            }
            if (hasQuota) {
                item {
                    CompareRow(stringResource(R.string.compare_quota), items,
                        { it.quotaMb?.let(::formatQuota) ?: "-" },
                        { false })
                }
                item {
                    CompareRow(
                        stringResource(R.string.compare_per_gb), items,
                        { p ->
                            p.quotaMb?.takeIf { it > 0 }
                                ?.let { formatIdr((p.monthlyTotal.toDouble() / it).toLong()) + "/GB" }
                                ?: "-"
                        },
                        { p ->
                            p.quotaMb?.takeIf { it > 0 }
                                ?.let { p.monthlyTotal.toDouble() / it } == perGb &&
                                perGb != null
                        },
                    )
                }
            }
            item {
                CompareRow(stringResource(R.string.compare_install), items,
                    { it.installFee?.let(::formatIdr) ?: unknownShort },
                    { false })
            }
            item {
                CompareRow(stringResource(R.string.compare_validity), items,
                    { it.validityDays?.let { d -> "$d hari" } ?: monthlyLabel },
                    { (it.validityDays ?: MONTH_RANK_DAYS) == longest })
            }
            item {
                CompareRow("PPN", items,
                    { if (it.taxInclusive) ppnYes else ppnNo },
                    { false })
            }
        }
    }
}

@Composable
private fun CompareRow(
    label: String,
    items: List<Package>,
    text: (Package) -> String,
    isBest: (Package) -> Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEach { pkg ->
                    Text(
                        (if (isBest(pkg)) "✓ " else "") + text(pkg),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (isBest(pkg)) FontWeight.Bold else FontWeight.Normal,
                        ),
                        color = if (isBest(pkg)) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
