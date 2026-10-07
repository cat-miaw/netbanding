package com.netbanding.app.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netbanding.app.data.repo.PackageRepository
import com.netbanding.app.domain.model.Package
import com.netbanding.app.domain.model.PricePoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FavoritesUiState(
    val items: List<Package> = emptyList(),
)

class FavoritesViewModel(repository: PackageRepository) : ViewModel() {
    private val repo = repository

    val uiState: StateFlow<FavoritesUiState> = repo.observeFavorites()
        .map { FavoritesUiState(items = it) }
        .catch { emit(FavoritesUiState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavoritesUiState())

    fun history(id: String): Flow<List<PricePoint>> = repo.observeHistory(id)

    fun toggleFavorite(pkg: Package) {
        viewModelScope.launch { repo.toggleFavorite(pkg.id, pkg.isFavorite) }
    }
}
