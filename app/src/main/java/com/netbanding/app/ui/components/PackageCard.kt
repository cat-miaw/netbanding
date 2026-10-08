package com.netbanding.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.netbanding.app.R
import com.netbanding.app.domain.model.Package
import com.netbanding.app.ui.home.Types
import com.netbanding.app.ui.home.formatIdr
import java.text.NumberFormat
import java.util.Locale

fun formatQuota(quotaMb: Int): String {
    if (quotaMb % 1024 == 0) return "${quotaMb / 1024} GB"
    val gb = quotaMb / 1024.0
    val s = NumberFormat.getNumberInstance(Locale("id", "ID")).format(gb)
        .trimEnd('0').trimEnd(',')
    return "$s GB"
}

/**
 * Billing period from package validity: monthly above 21 days, weekly
 * 7-21 days, daily below. Broadband (null validity) bills monthly.
 */
fun periodeFor(validityDays: Int?): String = when {
    validityDays == null || validityDays > 21 -> "bln"
    validityDays >= 7 -> "mgg"
    else -> "hr"
}

fun formatPricePeriode(amount: Long, validityDays: Int?): String =
    "${formatIdr(amount)}/${periodeFor(validityDays)}"

fun formatValidity(days: Int): String = "$days hari"

@Composable
fun PackageCard(
    pkg: Package,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    onCompare: () -> Unit,
    isCompared: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(pkg.ispName, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    Text(pkg.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (pkg.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = stringResource(
                            if (pkg.isFavorite) R.string.unfavorite else R.string.favorite,
                        ),
                        tint = if (pkg.isFavorite) cs.primary else cs.onSurfaceVariant,
                    )
                }
            }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold, fontSize = MaterialTheme.typography.headlineSmall.fontSize)) {
                        append(formatIdr(pkg.monthlyTotal))
                    }
                    withStyle(SpanStyle(color = cs.onSurfaceVariant)) {
                        append("/${periodeFor(pkg.validityDays)}")
                    }
                },
            )
            Text(
                if (pkg.taxInclusive) "Sudah termasuk PPN" else "Belum termasuk PPN",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
            // Info box: quota/speed + validity
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(cs.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pkg.type == Types.CELLULAR) {
                    Icon(Icons.Filled.Storage, null, tint = cs.tertiary, modifier = Modifier.padding(end = 8.dp))
                    Text(
                        pkg.quotaMb?.let(::formatQuota) ?: "-",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f),
                    )
                    pkg.validityDays?.let {
                        Text("$it hari", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                } else {
                    Icon(Icons.Filled.Speed, null, tint = cs.tertiary, modifier = Modifier.padding(end = 8.dp))
                    Text(
                        pkg.speedMbps?.let { "$it Mbps" } ?: "-",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f),
                    )
                    pkg.quotaMb?.let {
                        Text(formatQuota(it), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCompare) {
                    Text(
                        if (isCompared) "✓ ${stringResource(R.string.compare_added)}"
                        else "+ ${stringResource(R.string.compare_add)}",
                        color = cs.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    "${stringResource(R.string.detail_pkg)}  ›",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(end = 4.dp),
                )
            }
        }
    }
}
