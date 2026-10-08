package com.netbanding.app.ui.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.netbanding.app.domain.model.Package

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    listState: LazyListState,
    focusRequester: FocusRequester,
    filtersVisible: Boolean,
    compareIds: Set<String>,
    onToggleCompare: (Package) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeTabContent(
        state = state,
        listState = listState,
        focusRequester = focusRequester,
        filtersVisible = filtersVisible,
        onQuery = viewModel::setQuery,
        onBudget = viewModel::setBudget,
        onSpeed = viewModel::setMinSpeed,
        onSort = viewModel::setSort,
        onToggleIsp = viewModel::toggleIsp,
        onSetIsps = viewModel::setIspIds,
        onTogglePeriod = viewModel::togglePeriod,
        onSetPeriods = viewModel::setPeriods,
        onToggleFavorite = viewModel::toggleFavorite,
        onType = viewModel::setType,
        onHistory = viewModel::history,
        onRefresh = viewModel::refresh,
        onToggleCompare = onToggleCompare,
        compareIds = compareIds,
        onPage = viewModel::setPage,
        modifier = modifier,
    )
}
