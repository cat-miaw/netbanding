package com.netbanding.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.prefsStore: DataStore<Preferences> by preferencesDataStore("netbanding")

object PrefKeys {
    val REGION = stringPreferencesKey("selected_region")
    val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    val DATA_VERSION = intPreferencesKey("last_data_version")
    val GENERATED_AT = stringPreferencesKey("last_generated_at")
    val LAST_CHECK_AT = longPreferencesKey("last_check_at")
    val CATALOG_SHA = stringPreferencesKey("catalog_sha256")
    val HISTORY_SHA = stringPreferencesKey("history_sha256")
    val PRICE_BASELINE = stringPreferencesKey("price_alert_baseline") // JSON {packageId: monthlyTotal}
}

/** Provinces per blueprint 4.4. JAVA_ALL = all six. */
val REGIONS = listOf(
    "JAVA_ALL" to "Pulau Jawa",
    "ID-JK" to "DKI Jakarta",
    "ID-BT" to "Banten",
    "ID-JB" to "Jawa Barat",
    "ID-JT" to "Jawa Tengah",
    "ID-YO" to "DI Yogyakarta",
    "ID-JI" to "Jawa Timur",
)

data class SyncState(
    val dataVersion: Int = 0,
    val generatedAt: String? = null,
    val lastCheckAt: Long = 0,
    val catalogSha: String? = null,
    val historySha: String? = null,
)

class UserPrefs(private val context: Context) {
    val region: Flow<String> = context.prefsStore.data.map { it[PrefKeys.REGION] ?: "JAVA_ALL" }
    val onboardingDone: Flow<Boolean> = context.prefsStore.data.map { it[PrefKeys.ONBOARDING_DONE] ?: false }
    val syncState: Flow<SyncState> = context.prefsStore.data.map {
        SyncState(
            dataVersion = it[PrefKeys.DATA_VERSION] ?: 0,
            generatedAt = it[PrefKeys.GENERATED_AT],
            lastCheckAt = it[PrefKeys.LAST_CHECK_AT] ?: 0,
            catalogSha = it[PrefKeys.CATALOG_SHA],
            historySha = it[PrefKeys.HISTORY_SHA],
        )
    }

    suspend fun setRegion(code: String) {
        context.prefsStore.edit { it[PrefKeys.REGION] = code }
    }

    suspend fun setOnboardingDone() {
        context.prefsStore.edit { it[PrefKeys.ONBOARDING_DONE] = true }
    }

    suspend fun updateAfterSync(version: Int, generatedAt: String, catalogSha: String, historySha: String) {
        context.prefsStore.edit {
            it[PrefKeys.DATA_VERSION] = version
            it[PrefKeys.GENERATED_AT] = generatedAt
            it[PrefKeys.CATALOG_SHA] = catalogSha
            it[PrefKeys.HISTORY_SHA] = historySha
            it[PrefKeys.LAST_CHECK_AT] = System.currentTimeMillis()
        }
    }

    suspend fun markChecked() {
        context.prefsStore.edit { it[PrefKeys.LAST_CHECK_AT] = System.currentTimeMillis() }
    }

    suspend fun snapshot(): SyncState = syncState.first()

    /** Baseline monthly totals used by price-drop alerts (packageId -> monthlyTotal). */
    val priceBaseline: Flow<Map<String, Long>> = context.prefsStore.data.map { prefs ->
        runCatching {
            Json.decodeFromString<Map<String, Long>>(
                prefs[PrefKeys.PRICE_BASELINE] ?: "{}",
            )
        }.getOrDefault(emptyMap())
    }

    suspend fun setPriceBaseline(map: Map<String, Long>) {
        context.prefsStore.edit { it[PrefKeys.PRICE_BASELINE] = Json.encodeToString(map) }
    }
}

/** In-memory fake for SyncRepository unit tests (no DataStore needed). */
class FakeSyncStore(var state: SyncState = SyncState()) {
    suspend fun get(): SyncState = state
    suspend fun put(version: Int, generatedAt: String, catalogSha: String, historySha: String) {
        state = state.copy(dataVersion = version, generatedAt = generatedAt, lastCheckAt = System.currentTimeMillis(), catalogSha = catalogSha, historySha = historySha)
    }
}
