package com.netbanding.app.data.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.netbanding.app.MainActivity
import com.netbanding.app.R
import com.netbanding.app.data.local.NetbandingDb
import com.netbanding.app.data.prefs.UserPrefs
import com.netbanding.app.ui.home.formatIdr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

const val DROP_CHANNEL_ID = "price_drops"
private const val DROP_NOTIFICATION_ID = 1001

data class PriceDrop(val name: String, val oldTotal: Long, val newTotal: Long)

/**
 * v1.1 local price-drop alerts. Computed on-device after each successful
 * sync for favorited packages only. No FCM, no server, still $0.
 * First run only records the baseline; it never notifies.
 */
class PriceDropMonitor(
    private val context: Context,
    private val db: NetbandingDb,
    private val prefs: UserPrefs,
    private val notifier: (List<PriceDrop>) -> Unit = { drops -> defaultNotify(context, drops) },
) {
    suspend fun checkAndNotify(): List<PriceDrop> = withContext(Dispatchers.IO) {
        val current = db.packageDao().observeFavorites().first()
            .associate { it.id to (it.name to it.monthly_total) }
        val baseline = prefs.priceBaseline.first()
        if (baseline.isEmpty()) {
            prefs.setPriceBaseline(current.mapValues { it.value.second })
            return@withContext emptyList()
        }
        val drops = current.mapNotNull { (id, pair) ->
            val old = baseline[id] ?: return@mapNotNull null
            if (pair.second < old) PriceDrop(pair.first, old, pair.second) else null
        }
        prefs.setPriceBaseline(current.mapValues { it.value.second })
        if (drops.isNotEmpty()) notifier(drops)
        drops
    }

    companion object {
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val mgr = context.getSystemService(NotificationManager::class.java) ?: return
            mgr.createNotificationChannel(
                NotificationChannel(
                    DROP_CHANNEL_ID,
                    context.getString(R.string.alert_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.alert_channel_desc) },
            )
        }

        private fun defaultNotify(context: Context, drops: List<PriceDrop>) {
            ensureChannel(context)
            val first = drops.first()
            val text = if (drops.size == 1) {
                context.getString(
                    R.string.alert_text_one,
                    first.name, formatIdr(first.oldTotal), formatIdr(first.newTotal),
                )
            } else {
                context.getString(R.string.alert_text_many, drops.size, first.name)
            }
            val intent = Intent(context, MainActivity::class.java)
            val pending = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, DROP_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(context.getString(R.string.alert_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            context.getSystemService(NotificationManager::class.java)
                ?.notify(DROP_NOTIFICATION_ID, notification)
        }
    }
}
