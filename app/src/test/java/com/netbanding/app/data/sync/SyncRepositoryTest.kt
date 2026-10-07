package com.netbanding.app.data.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.netbanding.app.data.local.NetbandingDb
import com.netbanding.app.data.prefs.FakeSyncStore
import com.netbanding.app.data.remote.NetbandingApi
import java.security.MessageDigest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Retrofit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class SyncRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db: NetbandingDb = Room.inMemoryDatabaseBuilder(context, NetbandingDb::class.java)
        .allowMainThreadQueries().build()
    private val server = MockWebServer()

    @After fun tearDown() {
        server.shutdown()
        db.close()
    }

    private fun api(): NetbandingApi = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .client(OkHttpClient())
        .build()
        .create(NetbandingApi::class.java)

    private fun sha(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private val catalog = """
        {"isps":[{"id":"biznet","name":"Biznet Home","category":"broadband","logo_asset":"ic","website_url":"https://x"}],
         "packages":[{"id":"biznet-home-50","isp_id":"biznet","name":"Home 50","type":"broadband","speed_mbps":50,
         "base_price":250000,"tax_inclusive":false,"device_rental_fee":0,"install_fee":0,"regions":["JAVA_ALL"],
         "source_url":"https://x","is_active":true,"last_verified_at":"2026-10-07T00:00:00Z","updated_at":"2026-10-07T00:00:00Z"}]}
    """.trimIndent()
    private val history = """{"biznet-home-50":[{"price":250000,"tax_inclusive":false,"recorded_at":"2026-10-07T00:00:00Z"}]}"""

    private fun manifest(version: Int, schema: Int = 1, cat: String = catalog, hist: String = history) = """
        {"schema_version":$schema,"data_version":$version,"generated_at":"2026-10-07T00:00:00Z","ppn_rate":0.11,
         "files":{"catalog":{"path":"catalog.json","sha256":"${sha(cat)}","bytes":${cat.length}},
                  "history":{"path":"history.json","sha256":"${sha(hist)}","bytes":${hist.length}}}}
    """.trimIndent()

    @Test fun success_appliesInOneTransaction() = runTest {
        server.enqueue(MockResponse().setBody(manifest(1)))
        server.enqueue(MockResponse().setBody(catalog))
        server.enqueue(MockResponse().setBody(history))
        val store = FakeSyncStore()
        val r = SyncRepository(api(), db, store).sync()
        assertEquals(SyncResult.Updated(1), r)
        assertEquals(1, db.packageDao().activeCount())
        assertEquals(1, store.state.dataVersion)
        // monthly_total = 250_000 * 1.11 = 277_500
        val rows = db.packageDao().observePackages("JAVA_ALL", null, null, null, emptyList(), 0, "cheapest")
        assertEquals(277_500, rows.first().single().monthly_total)
    }

    @Test fun corruptPayload_leavesOldDataIntact() = runTest {
        // Seed good data first.
        server.enqueue(MockResponse().setBody(manifest(1)))
        server.enqueue(MockResponse().setBody(catalog))
        server.enqueue(MockResponse().setBody(history))
        val store = FakeSyncStore()
        assertEquals(SyncResult.Updated(1), SyncRepository(api(), db, store).sync())
        // Now serve garbage catalog bytes with a *valid* new version but wrong sha.
        val bad = "{not json"
        server.enqueue(MockResponse().setBody(manifest(2)))
        server.enqueue(MockResponse().setBody(bad))
        server.enqueue(MockResponse().setBody(history))
        val r = SyncRepository(api(), db, store).sync()
        assertTrue(r is SyncResult.Failed)
        assertEquals(1, db.packageDao().activeCount())
        assertEquals(1, store.state.dataVersion)
    }

    @Test fun sameVersion_skipsDownload() = runTest {
        server.enqueue(MockResponse().setBody(manifest(1)))
        server.enqueue(MockResponse().setBody(catalog))
        server.enqueue(MockResponse().setBody(history))
        val store = FakeSyncStore()
        val repo = SyncRepository(api(), db, store)
        assertEquals(SyncResult.Updated(1), repo.sync())
        server.enqueue(MockResponse().setBody(manifest(1)))
        assertEquals(SyncResult.NoChange, repo.sync())
        assertEquals(4, server.requestCount) // manifest, catalog, history, manifest
    }

    @Test fun newerSchema_keepsOldDataAndFlagsUpdate() = runTest {
        server.enqueue(MockResponse().setBody(manifest(9, schema = 99)))
        val r = SyncRepository(api(), db, FakeSyncStore()).sync()
        assertEquals(SyncResult.NeedsAppUpdate, r)
        assertEquals(0, db.packageDao().activeCount())
    }
}
