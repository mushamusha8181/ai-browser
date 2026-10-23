package jp.mushamusha.aibrowser

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.text.Html
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class WatchWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    private val http = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = BrowserDatabase(applicationContext)
        try {
            val enabled = db.listWatches().filter { it.enabled }
            val safeWatches = enabled.filterNot { watch ->
                if (AiPrivacy.containsSensitiveUrlData(watch.url)) {
                    db.removeWatch(watch.url)
                    true
                } else false
            }

            if (!WatchScheduler.notificationsAvailable(applicationContext)) return@withContext Result.success()

            safeWatches.forEach { watch ->
                if (isStopped) return@forEach
                try {
                    val req = Request.Builder()
                        .url(watch.url)
                        .header("User-Agent", "AI Browser Page Watch/1.3.2 (+Android)")
                        .get()
                        .build()
                    executeCancellable(req).use { response ->
                        if (!response.isSuccessful) error("HTTP ${response.code}")
                        val contentType = response.body?.contentType()?.toString().orEmpty().lowercase()
                        if (contentType.isNotBlank() &&
                            !contentType.startsWith("text/") &&
                            !contentType.contains("html") &&
                            !contentType.contains("xml")
                        ) return@use

                        val body = response.body ?: return@use
                        val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                        val raw = readBodyUpTo(body.byteStream(), 3_000_000, charset)
                        val normalized = stablePageText(raw)
                        if (normalized.isBlank()) return@use
                        val hash = sha256(normalized)
                        val snippet = normalized.take(1200)
                        if (watch.lastHash.isNotBlank() && watch.lastHash != hash) {
                            notifyChanged(watch.title, watch.url, watch.lastSnippet, snippet, watch.id)
                        }
                        db.updateWatchSnapshot(watch.id, hash, snippet)
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                }
            }
        } finally {
            db.close()
        }
        Result.success()
    }

    private suspend fun executeCancellable(request: Request): Response = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isActive) return
                continuation.resumeWith(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                if (!continuation.isActive) {
                    response.close()
                    return
                }
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }

    private fun readBodyUpTo(input: java.io.InputStream, maxBytes: Int, charset: java.nio.charset.Charset): String {
        val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
        val buffer = ByteArray(16 * 1024)
        var remaining = maxBytes
        input.use { stream ->
            while (remaining > 0) {
                val read = stream.read(buffer, 0, minOf(buffer.size, remaining))
                if (read <= 0) break
                output.write(buffer, 0, read)
                remaining -= read
            }
        }
        return output.toByteArray().toString(charset)
    }

    private fun stablePageText(html: String): String {
        var cleaned = html
            .replace(Regex("(?is)<!--.*?-->"), " ")
            .replace(Regex("(?is)<script[^>]*>.*?</script>"), " ")
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), " ")
            .replace(Regex("(?is)<noscript[^>]*>.*?</noscript>"), " ")
            .replace(Regex("(?is)<svg[^>]*>.*?</svg>"), " ")

        fun largestTag(tag: String): String? = Regex("(?is)<$tag\\b[^>]*>(.*?)</$tag>")
            .findAll(cleaned)
            .map { it.groupValues[1] }
            .maxByOrNull { it.length }

        cleaned = largestTag("article") ?: largestTag("main") ?: cleaned
        cleaned = cleaned
            .replace(Regex("(?is)<(nav|footer|header|aside|form)\\b[^>]*>.*?</\\1>"), " ")

        val text = Html.fromHtml(cleaned, Html.FROM_HTML_MODE_LEGACY).toString()
        return text.replace(Regex("\\s+"), " ").trim().take(250_000)
    }

    private fun notifyChanged(title: String, url: String, before: String, after: String, id: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse(url)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            applicationContext, id.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val detail = if (before.isBlank()) {
            "ページ本文が更新された可能性があります"
        } else {
            "前回:\n${before.take(420)}\n\n現在:\n${after.take(420)}"
        }
        val notification = NotificationCompat.Builder(applicationContext, BrowserApp.WATCH_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle("ページ更新: ${title.take(80)}")
            .setContentText("ページ本文の変更を検知しました")
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(applicationContext).notify((10_000 + id).toInt(), notification)
    }

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
