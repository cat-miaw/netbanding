package com.netbanding.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.netbanding.app.R

/** Top bar from the mockup: menu/back icon box + title + optional actions. */
@Composable
fun NetTopBar(
    title: String = "NetBanding",
    onMenu: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    onToggleFilters: (() -> Unit)? = null,
    filtersVisible: Boolean = true,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconBox(image = Icons.AutoMirrored.Filled.ArrowBack, desc = "back", onClick = onBack)
        } else if (onMenu != null) {
            IconBox(image = Icons.Filled.Menu, desc = stringResource(R.string.open_menu), onClick = onMenu)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        if (onSearch != null) {
            IconBox(image = Icons.Filled.Search, desc = stringResource(R.string.action_search), onClick = onSearch)
        }
        if (onToggleFilters != null) {
            IconBox(
                image = Icons.Filled.Tune,
                desc = stringResource(R.string.action_filter),
                onClick = onToggleFilters,
                active = filtersVisible,
            )
        }
        if (actionText != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionText) }
        }
    }
}

@Composable
fun IconBox(
    image: ImageVector,
    desc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Icon(
            image,
            contentDescription = desc,
            tint = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun NetSearch(
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.outline,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
        modifier = modifier.fillMaxWidth().then(
            if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier,
        ),
    )
}

/** Segmented Seluler/Broadband pill. */
@Composable
fun TypeSegment(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
    ) {
        SegmentItem(
            active = selected == "cellular",
            label = stringResource(R.string.tab_cellular),
            onClick = { onSelect("cellular") },
        )
        SegmentItem(
            active = selected == "broadband",
            label = stringResource(R.string.tab_broadband),
            onClick = { onSelect("broadband") },
        )
    }
}

@Composable
private fun RowScope.SegmentItem(active: Boolean, label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) cs.surface else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = if (active) cs.primary else cs.onSurfaceVariant,
            ),
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
        ),
        modifier = modifier,
    )
}

@Composable
fun DisclaimerLine(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            "ⓘ  " + stringResource(R.string.disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun NetBottomBar(
    onHome: () -> Unit,
    onFavorites: () -> Unit,
    onCompare: () -> Unit,
    selected: String = "home",
    compareCount: Int = 0,
) {
    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
        indicatorColor = MaterialTheme.colorScheme.surface,
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = selected == "home",
            onClick = onHome,
            icon = { Icon(Icons.Filled.Home, null) },
            label = { Text(stringResource(R.string.nav_home)) },
            colors = colors,
        )
        NavigationBarItem(
            selected = selected == "favorites",
            onClick = onFavorites,
            icon = { Icon(Icons.Filled.FavoriteBorder, null) },
            label = { Text(stringResource(R.string.favorites_title)) },
            colors = colors,
        )
        NavigationBarItem(
            selected = selected == "compare",
            onClick = onCompare,
            icon = { Icon(Icons.AutoMirrored.Filled.CompareArrows, null) },
            label = {
                Text(
                    if (compareCount > 0) stringResource(R.string.compare_open, compareCount)
                    else stringResource(R.string.compare_title),
                )
            },
            colors = colors,
        )
    }
}

/** Big page headline from the mockup. */
@Composable
fun PageHeadline(title: String, sub: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold))
        Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Compact page strip: « 1 … 4 5 6 … 12 ». Pure window math lives in
 * [pageWindow] so it can be unit-tested.
 */
fun pageWindow(page: Int, pageCount: Int): List<Int?> {
    val keep = (setOf(0, pageCount - 1) + (page - 1..page + 1))
        .filter { it in 0 until pageCount }.sorted()
    val out = mutableListOf<Int?>()
    keep.forEachIndexed { i, p ->
        if (i > 0 && p - keep[i - 1] > 1) out += null
        out += p
    }
    return out
}

@Composable
fun PageControls(
    page: Int,
    pageCount: Int,
    onPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (pageCount <= 1) return
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { onPage(page - 1) }, enabled = page > 0) { Text("«") }
        pageWindow(page, pageCount).forEach { p ->
            if (p == null) {
                Text("…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                TextButton(onClick = { onPage(p) }) {
                    Text(
                        "${p + 1}",
                        fontWeight = if (p == page) FontWeight.ExtraBold else FontWeight.Normal,
                        color = if (p == page) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        TextButton(onClick = { onPage(page + 1) }, enabled = page < pageCount - 1) { Text("»") }
    }
}
