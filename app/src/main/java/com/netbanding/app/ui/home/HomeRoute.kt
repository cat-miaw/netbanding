package com.netbanding.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onQuery = viewModel::setQuery,
        onBudget = viewModel::setBudget,
        onSpeed = viewModel::setMinSpeed,
        onSort = viewModel::setSort,
        onToggleIsp = viewModel::toggleIsp,
        onToggleFavorite = viewModel::toggleFavorite,
        onHistory = viewModel::history,
        onRefresh = viewModel::refresh,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
    )
}
