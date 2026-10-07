package com.netbanding.app.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netbanding.app.data.prefs.UserPrefs
import com.netbanding.app.data.notify.PriceDropMonitor
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

object Sorts {
    const val CHEAPEST = "cheapest"
    const val VALUE = "value"
    const val FASTEST = "fastest"
}

object Types {
    const val BROADBAND = "broadband"
    const val CELLULAR = "cellular"
}

enum class SyncStatus { IDLE, SYNCING, FAILED }

data class IspOption(val id: String, val name: String, val category: String)

/** Filters survive rotation + process death via SavedStateHandle; region persists in DataStore. */
data class HomeUiState(
    val isLoading: Boolean = true,
    val items: List<Package> = emptyList(),
    val isps: List<IspOption> = emptyList(),
    val query: String = "",
    val type: String = Types.BROADBAND,
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
    private val monitor: PriceDropMonitor,
    private val onScheduleWorker: () -> Unit = {},
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val query = savedState.getStateFlow("q", "")
    private val tab = savedState.getStateFlow("tab", Types.BROADBAND)
    private val maxMonthly = savedState.getStateFlow("max", -1L)
    private val minSpeed = savedState.getStateFlow("spd", -1)
    private val sort = savedState.getStateFlow("sort", Sorts.CHEAPEST)
    private val ispIds = MutableStateFlow<Set<String>>(
        savedState.get<ArrayList<String>>("isps")?.toSet() ?: emptySet(),
    )
    private val syncStatus = MutableStateFlow(SyncStatus.IDLE)
    private val updateApp = MutableStateFlow(false)

    private data class Keys(
        val region: String, val q: String, val type: String,
        val max: Long, val spd: Int, val sort: String, val isps: Set<String>,
    )

    private val keys: kotlinx.coroutines.flow.Flow<Keys> = combine(
        combine(prefs.region, query, tab) { r, q, t -> Triple(r, q, t) },
        combine(maxMonthly, minSpeed, sort) { m, sp, so -> Triple(m, sp, so) },
        ispIds,
    ) { a, b, isps ->
        Keys(
            region = a.first, q = a.second, type = a.third,
            max = b.first, spd = b.second, sort = b.third, isps = isps,
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        keys.flatMapLatest { k ->
            // Speed filter is meaningless for cellular (speeds unstated); drop it there.
            val spd = k.spd.takeIf { it > 0 && k.type == Types.BROADBAND }
            val effectiveSort =
                if (k.type == Types.CELLULAR && k.sort == Sorts.VALUE) Sorts.CHEAPEST else k.sort
            combine(
                repository.observePackages(
                    region = k.region,
                    type = k.type,
                    maxMonthly = k.max.takeIf { it > 0 },
                    minSpeed = spd,
                    query = k.q,
                    ispIds = k.isps,
                    sort = effectiveSort,
                ),
                repository.observeIsps(),
            ) { items, ispList ->
                Triple(items, ispList.map { IspOption(it.id, it.name, it.category) }, k)
            }
        },
        syncStatus,
        updateApp,
        prefs.syncState,
    ) { data, status, needUpdate, syncState ->
        val (items, ispList, k) = data
        HomeUiState(
            isLoading = false, items = items,
            isps = ispList,
            query = k.q, type = k.type, maxMonthly = k.max.takeIf { it > 0 },
            minSpeed = k.spd.takeIf { it > 0 },
            ispIds = k.isps, sort = k.sort, region = k.region,
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
            when (sync.sync()) {
                is SyncResult.Updated -> {
                    updateApp.value = false
                    syncStatus.value = SyncStatus.IDLE
                    runCatching { monitor.checkAndNotify() }
                }
                SyncResult.NoChange -> {
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
    fun setType(t: String) {
        savedState["tab"] = t
        // ISP selection rarely carries across types; reset it on tab switch.
        ispIds.value = emptySet()
        savedState["isps"] = ArrayList<String>()
    }
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
