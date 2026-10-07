package com.netbanding.app.data.remote

import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.http.GET

/**
 * Static JSON on GitHub Pages (blueprint D4). OkHttp handles ETag/gzip;
 * files are fetched as raw bodies so the repo can verify sha256 itself.
 */
interface NetbandingApi {
    @GET("manifest.json")
    suspend fun manifest(): ResponseBody

    @GET("catalog.json")
    suspend fun catalog(): ResponseBody

    @GET("history.json")
    suspend fun history(): ResponseBody
}

/** TODO: point at the real Pages URL once `pages.yml` has deployed. */
const val DEFAULT_BASE_URL = "https://cat-miaw.github.io/netbanding/"

@Serializable
data class RemoteManifestFile(val path: String, val sha256: String, val bytes: Long)

@Serializable
data class RemoteManifest(
    val schema_version: Int,
    val data_version: Int,
    val generated_at: String,
    val ppn_rate: Double = 0.11,
    val files: Map<String, RemoteManifestFile> = emptyMap(),
)
