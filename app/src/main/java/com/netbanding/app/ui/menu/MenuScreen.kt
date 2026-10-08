package com.netbanding.app.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.netbanding.app.R
import com.netbanding.app.ui.components.PageHeadline
import com.netbanding.app.ui.components.SectionLabel

/** Drawer content: same menu as before, now sliding over the current tab. */
@Composable
fun MenuDrawerContent(
    dataVersion: Int,
    lastUpdated: String?,
    onClose: () -> Unit,
    onCellular: () -> Unit,
    onBroadband: () -> Unit,
    onFavorites: () -> Unit,
    onCompare: () -> Unit,
    onSettings: () -> Unit,
    onPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxSize()
            .background(cs.surface)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
            }
        }
        PageHeadline(title = stringResource(R.string.menu_title), sub = stringResource(R.string.menu_sub))
        SectionLabel(stringResource(R.string.menu_explore))
        MenuRow(
            icon = Icons.Filled.SignalCellularAlt,
            title = stringResource(R.string.tab_cellular),
            sub = stringResource(R.string.menu_cellular_desc),
            highlighted = true,
            onClick = onCellular,
        )
        MenuRow(
            icon = Icons.Filled.Wifi,
            title = stringResource(R.string.tab_broadband),
            sub = stringResource(R.string.menu_broadband_desc),
            onClick = onBroadband,
        )
        SectionLabel(stringResource(R.string.menu_mine))
        MenuRow(icon = Icons.Filled.FavoriteBorder, title = stringResource(R.string.favorites_title), onClick = onFavorites)
        MenuRow(icon = Icons.AutoMirrored.Filled.CompareArrows, title = stringResource(R.string.compare_title), onClick = onCompare)
        Spacer(Modifier.weight(1f))
        MenuRow(icon = Icons.Filled.Settings, title = stringResource(R.string.settings), onClick = onSettings)
        MenuRow(icon = Icons.Filled.PrivacyTip, title = stringResource(R.string.privacy_title), onClick = onPrivacy)
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = cs.primary),
        )
        Text(
            stringResource(R.string.data_version, dataVersion, lastUpdated?.take(10) ?: "-"),
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    sub: String? = null,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (highlighted) cs.primaryContainer else cs.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (highlighted) cs.primary else cs.onSurfaceVariant)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = if (highlighted) cs.primary else cs.onSurface,
                ),
            )
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = cs.onSurfaceVariant)
    }
}
