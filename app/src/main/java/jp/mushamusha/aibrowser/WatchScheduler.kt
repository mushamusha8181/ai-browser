package jp.mushamusha.aibrowser

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WatchScheduler {
    private const val PERIODIC_NAME = "ai_browser_page_watch"
    private const val ONE_TIME_NAME = "ai_browser_page_watch_now"

    fun sync(context: Context) {
        val db = BrowserDatabase(context)
        val hasEnabled = try { db.listWatches().any { it.enabled } } finally { db.close() }
        val work = WorkManager.getInstance(context)
        if (!hasEnabled || !notificationsAvailable(context)) {
            work.cancelUniqueWork(PERIODIC_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<WatchWorker>(30, TimeUnit.MINUTES)
            .setConstraints(networkConstraint())
            .build()
        work.enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun runOnce(context: Context) {
        if (!notificationsAvailable(context)) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<WatchWorker>()
                .setConstraints(networkConstraint())
                .build()
        )
    }

    fun notificationsAvailable(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = manager?.getNotificationChannel(BrowserApp.WATCH_CHANNEL)
            if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        return true
    }

    private fun networkConstraint(): Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}
