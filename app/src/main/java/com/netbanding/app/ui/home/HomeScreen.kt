package com.netbanding.app.ui.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netbanding.app.R
import com.netbanding.app.domain.model.Package
import com.netbanding.app.ui.components.PackageCard
import com.netbanding.app.ui.detail.PackageDetailSheet
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.launch

fun formatIdr(amount: Long): String =
    "Rp" + NumberFormat.getNumberInstance(Locale("id", "ID")).format(amount)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onQuery: (String) -> Unit,
    onBudget: (Long?) -> Unit,
    onSpeed: (Int?) -> Unit,
    onSort: (String) -> Unit,
    onToggleIsp: (String) -> Unit,
    onToggleFavorite: (Package) -> Unit,
    onHistory: (String) -> kotlinx.coroutines.flow.Flow<List<com.netbanding.app.domain.model.PricePoint>>,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenCompare: () -> Unit,
    compareCount: Int,
    onToggleCompare: (Package) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf<Package?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    if (compareCount > 0) TextButton(onClick = onOpenCompare) {
                        Text(stringResource(R.string.compare_open, compareCount))
                    }
                    TextButton(onClick = onOpenFavorites) { Text(stringResource(R.string.favorites_title)) }
                    TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.settings)) }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.syncStatus == SyncStatus.SYNCING,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQuery,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.maxMonthly == 300_000L, onClick = {
                        onBudget(if (state.maxMonthly == 300_000L) null else 300_000L)
                    }, label = { Text(stringResource(R.string.budget_300)) },
                )
                FilterChip(
                    selected = state.maxMonthly == 500_000L, onClick = {
                        onBudget(if (state.maxMonthly == 500_000L) null else 500_000L)
                    }, label = { Text(stringResource(R.string.budget_500)) },
                )
                FilterChip(
                    selected = state.minSpeed == 50, onClick = {
                        onSpeed(if (state.minSpeed == 50) null else 50)
                    }, label = { Text(stringResource(R.string.speed_50)) },
                )
                FilterChip(
                    selected = state.minSpeed == 100, onClick = {
                        onSpeed(if (state.minSpeed == 100) null else 100)
                    }, label = { Text(stringResource(R.string.speed_100)) },
                )
                FilterChip(
                    selected = state.sort == Sorts.CHEAPEST, onClick = { onSort(Sorts.CHEAPEST) },
                    label = { Text(stringResource(R.string.sort_cheapest)) },
                )
                FilterChip(
                    selected = state.sort == Sorts.VALUE, onClick = { onSort(Sorts.VALUE) },
                    label = { Text(stringResource(R.string.sort_value)) },
                )
                FilterChip(
                    selected = state.sort == Sorts.FASTEST, onClick = { onSort(Sorts.FASTEST) },
                    label = { Text(stringResource(R.string.sort_fastest)) },
                )
            }
            if (state.isps.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.isps.forEach { isp ->
                        FilterChip(
                            selected = isp in state.ispIds, onClick = { onToggleIsp(isp) },
                            label = { Text(isp) },
                        )
                    }
                }
            }
            if (state.showUpdateApp || state.showStale || state.syncStatus == SyncStatus.FAILED) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    if (state.showUpdateApp) Text(
                        stringResource(R.string.update_app_banner),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (state.showStale) Text(
                        stringResource(R.string.stale_banner),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (state.syncStatus == SyncStatus.FAILED) Text(
                        stringResource(R.string.sync_failed),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.empty_result))
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items = state.items, key = { it.id }, contentType = { "package" }) { pkg ->
                        PackageCard(
                            pkg = pkg,
                            onClick = { selected = pkg },
                            onFavorite = { onToggleFavorite(pkg) },
                        )
                    }
                    item {
                        Text(
                            state.lastUpdated?.let { stringResource(R.string.last_updated, it.take(10)) }
                                ?: stringResource(R.string.offline_seed_note),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
        }
    }

    selected?.let { pkg ->
        val history by remember(pkg.id) { onHistory(pkg.id) }
            .collectAsStateWithLifecycle(initialValue = emptyList())
        ModalBottomSheet(onDismissRequest = { selected = null }, sheetState = sheetState) {
            PackageDetailSheet(
                pkg = pkg,
                history = history,
                onFavorite = {
                    onToggleFavorite(pkg)
                    scope.launch { sheetState.hide(); selected = null }
                },
                onCompare = {
                    onToggleCompare(pkg)
                    scope.launch { sheetState.hide(); selected = null }
                },
            )
        }
    }
}
