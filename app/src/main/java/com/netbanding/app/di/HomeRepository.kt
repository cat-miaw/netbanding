package com.netbanding.app.di

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Placeholder data source. Swap the body for your real source (Room, Retrofit,
 * DataStore, ...) without touching anything above this layer.
 *
 * Note the dispatcher hop: anything that touches disk or network must leave the
 * main thread, otherwise you drop frames before the first screen even appears.
 */
class HomeRepository(private val context: Context) {

    suspend fun loadItems(): List<String> = withContext(Dispatchers.IO) {
        delay(400) // stands in for a real I/O call
        List(30) { index -> "Item ${index + 1}" }
    }
}