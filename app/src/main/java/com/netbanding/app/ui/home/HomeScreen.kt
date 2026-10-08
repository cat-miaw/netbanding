package com.netbanding.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netbanding.app.R
import com.netbanding.app.domain.model.Package
import com.netbanding.app.ui.components.DisclaimerLine
import com.netbanding.app.ui.components.MultiSelectDropdown
import com.netbanding.app.ui.components.NetSearch
import com.netbanding.app.ui.components.PackageCard
import com.netbanding.app.ui.components.PageControls
import com.netbanding.app.ui.components.PageHeadline
import com.netbanding.app.ui.components.SectionLabel
import com.netbanding.app.ui.components.SingleSelectDropdown
import com.netbanding.app.ui.components.TypeSegment
import com.netbanding.app.ui.detail.PackageDetailSheet
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.launch

fun formatIdr(amount: Long): String =
    "Rp" + NumberFormat.getNumberInstance(Locale("id", "ID")).format(amount)

/**
 * Home tab content (no Scaffold: the pager-level chrome in MainTabs owns the
 * top/bottom bars so they stay put while swiping). The header (headline,
 * type switch, search) scrolls away with the list; the search field is
 * always part of the list so an empty result can never strand the user
 * without a way to edit/clear the query.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTabContent(
    state: HomeUiState,
    listState: LazyListState,
    focusRequester: FocusRequester,
    filtersVisible: Boolean,
    onQuery: (String) -> Unit,
    onBudget: (Long?) -> Unit,
    onSpeed: (Int?) -> Unit,
    onSort: (String) -> Unit,
    onType: (String) -> Unit,
    onToggleIsp: (String) -> Unit,
    onSetIsps: (Set<String>) -> Unit,
    onTogglePeriod: (String) -> Unit,
    onSetPeriods: (Set<String>) -> Unit,
    onToggleFavorite: (Package) -> Unit,
    onHistory: (String) -> kotlinx.coroutines.flow.Flow<List<com.netbanding.app.domain.model.PricePoint>>,
    onRefresh: () -> Unit,
    onToggleCompare: (Package) -> Unit,
    compareIds: Set<String>,
    onPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by remember { mutableStateOf<Package?>(null) }
    // Local text buffer: the field must never be driven by the DB round-trip
    // (per-keystroke re-query desyncs cursor/composition).
    var text by remember { mutableStateOf(state.query) }
    val focusManager = LocalFocusManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Page switch = fresh result window: jump back to the top.
    androidx.compose.runtime.LaunchedEffect(state.page) {
        runCatching { listState.scrollToItem(0) }
    }
    val isCellular = state.type == Types.CELLULAR
    val tabIsps = remember(state.isps, state.type) {
        state.isps.filter {
            (isCellular && it.category == "cellular") ||
                (!isCellular && it.category != "cellular")
        }
    }

    PullToRefreshBox(
        isRefreshing = state.syncStatus == SyncStatus.SYNCING,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onTap = { focusManager.clearFocus() })
        },
    ) {
        when {
            state.isLoading -> androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.CircularProgressIndicator()
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "header", contentType = "header") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PageHeadline(
                            title = stringResource(
                                if (isCellular) R.string.home_title_cellular else R.string.home_title_broadband,
                            ),
                            sub = stringResource(R.string.home_sub),
                        )
                        TypeSegment(selected = state.type, onSelect = onType)
                        NetSearch(
                            value = text,
                            onValue = { text = it; onQuery(it) },
                            focusRequester = focusRequester,
                        )
                    }
                }
                if (filtersVisible) {
                    item(key = "filters", contentType = "header") {
                        val sortOptions = if (isCellular) listOf(
                            Sorts.CHEAPEST to stringResource(R.string.sort_cheapest),
                            Sorts.PERGB to stringResource(R.string.sort_pergb),
                        ) else listOf(
                            Sorts.CHEAPEST to stringResource(R.string.sort_cheapest),
                            Sorts.VALUE to stringResource(R.string.sort_value),
                            Sorts.FASTEST to stringResource(R.string.sort_fastest),
                        )
                        val allLabel = stringResource(R.string.filter_all)
                        val periodOptions = listOf(
                            Periods.DAILY to stringResource(R.string.period_daily),
                            Periods.WEEKLY to stringResource(R.string.period_weekly),
                            Periods.MONTHLY to stringResource(R.string.period_monthly),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SingleSelectDropdown(
                                    label = stringResource(R.string.filter_sort),
                                    options = sortOptions,
                                    selected = state.sort,
                                    onSelect = { it?.let(onSort) },
                                    modifier = Modifier.weight(1f),
                                )
                                if (isCellular) {
                                    MultiSelectDropdown(
                                        label = stringResource(R.string.filter_period),
                                        allLabel = allLabel,
                                        options = periodOptions,
                                        selected = state.periods,
                                        onToggle = onTogglePeriod,
                                        onSelectAll = {
                                            onSetPeriods(setOf(Periods.DAILY, Periods.WEEKLY, Periods.MONTHLY))
                                        },
                                        onClear = { onSetPeriods(emptySet()) },
                                        modifier = Modifier.weight(1f),
                                    )
                                } else {
                                    SingleSelectDropdown(
                                        label = stringResource(R.string.filter_budget),
                                        options = listOf(
                                            "all" to allLabel,
                                            "300" to stringResource(R.string.budget_300),
                                            "500" to stringResource(R.string.budget_500),
                                        ),
                                        selected = when (state.maxMonthly) {
                                            300_000L -> "300"
                                            500_000L -> "500"
                                            else -> "all"
                                        },
                                        onSelect = {
                                            onBudget(when (it) {
                                                "300" -> 300_000L
                                                "500" -> 500_000L
                                                else -> null
                                            })
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (tabIsps.isNotEmpty()) {
                                    MultiSelectDropdown(
                                        label = stringResource(R.string.filter_provider),
                                        allLabel = allLabel,
                                        options = tabIsps.map { it.id to it.name },
                                        selected = state.ispIds,
                                        onToggle = onToggleIsp,
                                        onSelectAll = { onSetIsps(tabIsps.map { it.id }.toSet()) },
                                        onClear = { onSetIsps(emptySet()) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (!isCellular) {
                                    SingleSelectDropdown(
                                        label = stringResource(R.string.filter_speed),
                                        options = listOf(
                                            "all" to allLabel,
                                            "50" to stringResource(R.string.speed_50),
                                            "100" to stringResource(R.string.speed_100),
                                        ),
                                        selected = when (state.minSpeed) {
                                            50 -> "50"
                                            100 -> "100"
                                            else -> "all"
                                        },
                                        onSelect = {
                                            onSpeed(when (it) {
                                                "50" -> 50
                                                "100" -> 100
                                                else -> null
                                            })
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                            SectionLabel(stringResource(R.string.section_packages))
                        }
                    }
                }
                if (state.showUpdateApp || state.showStale || state.syncStatus == SyncStatus.FAILED) {
                    item(key = "banners", contentType = "header") {
                        Column(modifier = Modifier.fillMaxWidth()) {
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
                }
                if (state.items.isEmpty()) {
                    item(key = "empty", contentType = "empty") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                stringResource(R.string.empty_result),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                            )
                            OutlinedButton(onClick = { text = ""; onQuery("") }) {
                                Text(stringResource(R.string.clear_search))
                            }
                        }
                    }
                } else {
                    items(items = state.items, key = { it.id }, contentType = { "package" }) { pkg ->
                        PackageCard(
                            pkg = pkg,
                            onClick = { selected = pkg },
                            onFavorite = { onToggleFavorite(pkg) },
                            onCompare = { onToggleCompare(pkg) },
                            isCompared = pkg.id in compareIds,
                        )
                    }
                }
                if (state.pageCount > 1) {
                    item(key = "pages", contentType = "pages") {
                        PageControls(
                            page = state.page,
                            pageCount = state.pageCount,
                            onPage = onPage,
                        )
                    }
                }
                item(key = "footer", contentType = "footer") {
                    DisclaimerLine(modifier = Modifier.padding(top = 4.dp))
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
                isCompared = pkg.id in compareIds,
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
