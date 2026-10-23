package jp.mushamusha.aibrowser

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.webkit.WebView

class BrowserApp : Application() {
    companion object {
        const val WATCH_CHANNEL = "page_watch"
    }

    override fun onCreate() {
        super.onCreate()
        val incognito = Application.getProcessName().endsWith(":incognito")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && incognito) {
            WebView.setDataDirectorySuffix("incognito")
        }

        // This app stores an API credential. Remote WebView debugging is unnecessary in normal builds.
        WebView.setWebContentsDebuggingEnabled(false)

        if (!incognito) {
            purgeLegacySensitiveUrlsOnce()
            createChannels()
            WatchScheduler.sync(this)
        }
    }

    private fun purgeLegacySensitiveUrlsOnce() {
        val migrationPrefs = getSharedPreferences("app_migrations", Context.MODE_PRIVATE)
        if (migrationPrefs.getBoolean("purged_sensitive_urls_v3", false)) return
        runCatching {
            val db = BrowserDatabase(this)
            try {
                db.purgeSensitiveUrls()
                migrationPrefs.edit().putBoolean("purged_sensitive_urls_v3", true).apply()
            } finally {
                db.close()
            }
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(WATCH_CHANNEL, "ページ変更監視", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "登録したWebページに変更があった時の通知"
            }
        )
    }
}
