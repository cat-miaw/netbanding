package com.netbanding.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.netbanding.app.R
import com.netbanding.app.domain.model.Package
import com.netbanding.app.ui.home.Types
import com.netbanding.app.ui.home.formatIdr
import java.text.NumberFormat
import java.util.Locale

fun formatQuota(quotaMb: Int): String {
    val gb = quotaMb / 1024.0
    val s = NumberFormat.getNumberInstance(Locale("id", "ID")).format(gb)
        .trimEnd('0').trimEnd(',')
    return "$s GB"
}

@Composable
fun PackageCard(
    pkg: Package,
    onClick: () -> Unit,
    onFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(pkg.ispName, style = MaterialTheme.typography.labelMedium)
                    Text(pkg.name, style = MaterialTheme.typography.titleMedium)
                }
                IconButton(onClick = onFavorite) {
                    Icon(
                        if (pkg.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = stringResource(
                            if (pkg.isFavorite) R.string.unfavorite else R.string.favorite,
                        ),
                    )
                }
            }
            Text(
                buildString {
                    append(formatIdr(pkg.monthlyTotal))
                    append("/bln")
                    if (pkg.type == Types.CELLULAR) {
                        pkg.quotaMb?.let { append(" • ${formatQuota(it)}") }
                        pkg.validityDays?.let { append(" • $it hr") }
                    } else {
                        pkg.speedMbps?.let { append(" • $it Mbps") }
                    }
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                if (pkg.taxInclusive) "Sudah termasuk PPN" else "Belum termasuk PPN",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
