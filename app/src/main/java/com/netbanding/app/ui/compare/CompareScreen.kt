package com.netbanding.app.ui.compare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netbanding.app.R
import com.netbanding.app.domain.model.Package
import com.netbanding.app.ui.components.formatPricePeriode
import com.netbanding.app.ui.components.formatQuota
import com.netbanding.app.ui.home.formatIdr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareRoute(viewModel: CompareViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.compare_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹") } },
                actions = {
                    if (state.items.isNotEmpty()) TextButton(onClick = viewModel::clear) {
                        Text(stringResource(R.string.compare_clear))
                    }
                },
            )
        },
    ) { padding ->
        if (state.items.size < 2) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.compare_empty))
            }
        } else {
            val items = state.items
            val cheapest = items.minOf { it.monthlyTotal }
            val fastest = items.mapNotNull { it.speedMbps }.maxOrNull()
            val bestValue = items.mapNotNull { it.pricePerMbps }.minOrNull()
            val bestPerGb = items.mapNotNull { p ->
                p.quotaMb?.takeIf { it > 0 }?.let { p.monthlyTotal.toDouble() / it }
            }.minOrNull()
            val unknownShort = stringResource(R.string.install_unknown_short)
            val ppnYes = stringResource(R.string.compare_included)
            val ppnNo = stringResource(R.string.compare_excluded)
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items.forEach { pkg ->
                            Card(modifier = Modifier.weight(1f)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(pkg.ispName, style = MaterialTheme.typography.labelSmall)
                                    Text(pkg.name, style = MaterialTheme.typography.titleSmall)
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
                        { it.monthlyTotal == cheapest })
                }
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
                item {
                    CompareRow(stringResource(R.string.compare_install), items,
                        { it.installFee?.let(::formatIdr) ?: unknownShort },
                        { false })
                }
                item {
                    CompareRow(stringResource(R.string.compare_quota), items,
                        { it.quotaMb?.let(::formatQuota) ?: "-" },
                        { p ->
                            p.quotaMb?.takeIf { it > 0 }
                                ?.let { p.monthlyTotal.toDouble() / it } == bestPerGb &&
                                bestPerGb != null
                        })
                }
                item {
                    CompareRow(stringResource(R.string.compare_validity), items,
                        { it.validityDays?.let { d -> "$d hari" } ?: "-" },
                        { false })
                }
                item {
                    CompareRow("PPN", items,
                        { if (it.taxInclusive) ppnYes else ppnNo },
                        { false })
                }
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
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { pkg ->
                Text(
                    (if (isBest(pkg)) "✓ " else "") + text(pkg),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (isBest(pkg)) FontWeight.Bold else FontWeight.Normal,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
