package com.netbanding.app.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netbanding.app.data.prefs.UserPrefs
import com.netbanding.app.data.repo.PackageRepository
import com.netbanding.app.data.sync.SyncRepository
import com.netbanding.app.data.sync.SyncResult
import com.netbanding.app.data.sync.isStale
import com.netbanding.app.domain.model.Package
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

object Sorts {
    const val CHEAPEST = "cheapest"
    const val VALUE = "value"
    const val FASTEST = "fastest"
}

enum class SyncStatus { IDLE, SYNCING, FAILED }

/** Filters survive rotation + process death via SavedStateHandle; region persists in DataStore. */
data class HomeUiState(
    val isLoading: Boolean = true,
    val items: List<Package> = emptyList(),
    val isps: List<String> = emptyList(),
    val query: String = "",
    val maxMonthly: Long? = null,
    val minSpeed: Int? = null,
    val ispIds: Set<String> = emptySet(),
    val sort: String = Sorts.CHEAPEST,
    val region: String = "JAVA_ALL",
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val lastUpdated: String? = null,
    val showStale: Boolean = false,
    val showUpdateApp: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: PackageRepository,
    private val prefs: UserPrefs,
    private val sync: SyncRepository,
    private val onScheduleWorker: () -> Unit = {},
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val query = savedState.getStateFlow("q", "")
    private val maxMonthly = savedState.getStateFlow("max", -1L)
    private val minSpeed = savedState.getStateFlow("spd", -1)
    private val sort = savedState.getStateFlow("sort", Sorts.CHEAPEST)
    private val ispIds = MutableStateFlow<Set<String>>(
        savedState.get<ArrayList<String>>("isps")?.toSet() ?: emptySet(),
    )
    private val syncStatus = MutableStateFlow(SyncStatus.IDLE)
    private val updateApp = MutableStateFlow(false)

    private data class Keys5(val region: String, val q: String, val max: Long, val spd: Int, val sort: String)
    private data class Keys(val k5: Keys5, val isps: Set<String>)

    private val keys: kotlinx.coroutines.flow.Flow<Keys> = combine(
        prefs.region, query, maxMonthly, minSpeed, sort,
    ) { r, q, m, sp, so -> Keys5(r as String, q as String, m as Long, sp as Int, so as String) }
        .combine(ispIds) { k5, isps -> Keys(k5, isps) }

    val uiState: StateFlow<HomeUiState> = combine(
        keys.flatMapLatest { (k5, isps) ->
            combine(
                repository.observePackages(
                    region = k5.region,
                    maxMonthly = k5.max.takeIf { it > 0 },
                    minSpeed = k5.spd.takeIf { it > 0 },
                    query = k5.q,
                    ispIds = isps,
                    sort = k5.sort,
                ),
                repository.observeIsps(),
            ) { items, ispList ->
                Triple(items, ispList.map { it.id }, k5 to isps)
            }
        },
        syncStatus,
        updateApp,
        prefs.syncState,
    ) { data, status, needUpdate, syncState ->
        val (items, ispList, keysNow) = data
        val (k5, isps) = keysNow
        HomeUiState(
            isLoading = false, items = items,
            isps = ispList,
            query = k5.q, maxMonthly = k5.max.takeIf { it > 0 },
            minSpeed = k5.spd.takeIf { it > 0 },
            ispIds = isps, sort = k5.sort, region = k5.region,
            syncStatus = status,
            lastUpdated = syncState.generatedAt,
            showStale = isStale(syncState.generatedAt),
            showUpdateApp = needUpdate,
        )
    }.catch { emit(HomeUiState(isLoading = false)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        onScheduleWorker()
        viewModelScope.launch {
            val s = prefs.snapshot()
            if (System.currentTimeMillis() - s.lastCheckAt > 24L * 60 * 60 * 1000) refresh()
        }
    }

    fun refresh() {
        if (syncStatus.value == SyncStatus.SYNCING) return
        viewModelScope.launch {
            syncStatus.value = SyncStatus.SYNCING
            when (val r = sync.sync()) {
                is SyncResult.Updated, SyncResult.NoChange -> {
                    updateApp.value = false
                    syncStatus.value = SyncStatus.IDLE
                }
                SyncResult.NeedsAppUpdate -> {
                    updateApp.value = true
                    syncStatus.value = SyncStatus.IDLE
                }
                is SyncResult.Failed -> syncStatus.value = SyncStatus.FAILED
            }
        }
    }

    fun setQuery(q: String) { savedState["q"] = q }
    fun setBudget(max: Long?) { savedState["max"] = max ?: -1L }
    fun setMinSpeed(spd: Int?) { savedState["spd"] = spd ?: -1 }
    fun setSort(s: String) { savedState["sort"] = s }
    fun toggleIsp(id: String) {
        val next = if (id in ispIds.value) ispIds.value - id else ispIds.value + id
        ispIds.value = next
        savedState["isps"] = ArrayList(next.toList())
    }

    fun toggleFavorite(pkg: Package) {
        viewModelScope.launch { repository.toggleFavorite(pkg.id, pkg.isFavorite) }
    }

    fun history(id: String) = repository.observeHistory(id)
}
