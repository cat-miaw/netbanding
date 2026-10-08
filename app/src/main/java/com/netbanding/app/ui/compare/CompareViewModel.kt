package com.netbanding.app.ui.compare

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netbanding.app.data.repo.PackageRepository
import com.netbanding.app.domain.model.Package
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

const val MAX_COMPARE = 3

data class CompareUiState(
    val items: List<Package> = emptyList(),
)

/** Compare set survives rotation + process death; capped at 3. */
@OptIn(ExperimentalCoroutinesApi::class)
class CompareViewModel(
    private val repository: PackageRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val ids = MutableStateFlow(
        savedState.get<ArrayList<String>>("compare")?.toSet() ?: emptySet(),
    )

    val uiState: StateFlow<CompareUiState> = ids.flatMapLatest { repository.observeByIds(it) }
        .map { CompareUiState(items = it.sortedBy { p -> p.monthlyTotal }) }
        .catch { emit(CompareUiState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompareUiState())

    val count: StateFlow<Int> = ids
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Membership set so cards can show ✓ state without per-card flows. */
    val selectedIds: StateFlow<Set<String>> = ids
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun toggle(pkg: Package) {
        val cur = ids.value
        val next = if (pkg.id in cur) cur - pkg.id else {
            if (cur.size >= MAX_COMPARE) return
            cur + pkg.id
        }
        ids.value = next
        savedState["compare"] = ArrayList(next.toList())
    }

    fun isSelected(id: String): Flow<Boolean> = ids.map { id in it }

    fun remove(id: String) {
        val next = ids.value - id
        ids.value = next
        savedState["compare"] = ArrayList(next.toList())
    }

    fun clear() {
        ids.value = emptySet()
        savedState["compare"] = ArrayList<String>()
    }
}
