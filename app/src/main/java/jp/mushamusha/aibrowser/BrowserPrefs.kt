package jp.mushamusha.aibrowser

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Small non-secret settings store shared by the normal and incognito processes.
 *
 * Android SharedPreferences does not support coherent multi-process caching. The browser has a
 * dedicated :incognito process, so settings are persisted as one atomic JSON file and read fresh
 * on every getter. Writers use an OS file lock and atomic replace to avoid partial/cross-process
 * writes. The old SharedPreferences file is only read once for migration.
 */
class BrowserPrefs(context: Context) {
    companion object {
        private const val LEGACY_NAME = "browser_prefs"
        private const val FILE_NAME = "browser_prefs_v2.json"
        private const val LOCK_NAME = "browser_prefs_v2.lock"
    }

    private val app = context.applicationContext
    private val file = File(app.noBackupFilesDir, FILE_NAME)
    private val lockFile = File(app.noBackupFilesDir, LOCK_NAME)
    private val legacy = app.getSharedPreferences(LEGACY_NAME, Context.MODE_PRIVATE)

    init { ensureMigrated() }

    private fun defaults(): JSONObject = JSONObject().apply {
        put("model", "deepseek-flash")
        put("reasoning", "high")
        put("max_tokens", 8192)
        put("home_page", "https://www.google.com")
        put("search_engine", "Google")
        put("javascript", true)
        put("third_party_cookies", true)
        put("tracker_block", false)
        put("https_only", false)
        put("secure_ai_windows", true)
        put("desktop_default", false)
        put("save_history", true)
        put("block_popups", true)
        put("initial_permissions_version", 0)
        put("ai_context_mode", "full")
        put("auto_agent_max_steps", 8)
        put("ai_privacy_notice_seen", false)
    }

    private fun ensureMigrated() {
        if (file.exists()) return
        withWriteLock {
            if (file.exists()) return@withWriteLock
            val root = defaults()
            fun string(key: String) { if (legacy.contains(key)) root.put(key, legacy.getString(key, root.optString(key))) }
            fun bool(key: String) { if (legacy.contains(key)) root.put(key, legacy.getBoolean(key, root.optBoolean(key))) }
            fun int(key: String) { if (legacy.contains(key)) root.put(key, legacy.getInt(key, root.optInt(key))) }
            string("model"); string("reasoning"); int("max_tokens"); string("home_page"); string("search_engine")
            bool("javascript"); bool("third_party_cookies"); bool("tracker_block"); bool("https_only")
            bool("secure_ai_windows"); bool("desktop_default"); bool("save_history"); bool("block_popups")
            int("initial_permissions_version"); string("ai_context_mode"); int("auto_agent_max_steps"); bool("ai_privacy_notice_seen")
            writeRootUnlocked(root)
            legacy.edit().clear().apply()
        }
    }

    private fun readRoot(): JSONObject {
        ensureMigrated()
        val parsed = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull()
        return parsed ?: defaults()
    }

    private fun update(block: (JSONObject) -> Unit) {
        withWriteLock {
            val root = if (file.exists()) {
                runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrElse { defaults() }
            } else defaults()
            block(root)
            writeRootUnlocked(root)
        }
    }

    private inline fun withWriteLock(block: () -> Unit) {
        lockFile.parentFile?.mkdirs()
        RandomAccessFile(lockFile, "rw").channel.use { channel ->
            channel.lock().use { block() }
        }
    }

    private fun writeRootUnlocked(root: JSONObject) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "$FILE_NAME.tmp.${android.os.Process.myPid()}.${System.nanoTime()}")
        tmp.outputStream().use { out ->
            out.write(root.toString().toByteArray(Charsets.UTF_8))
            out.flush()
            if (out is java.io.FileOutputStream) out.fd.sync()
        }
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Throwable) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    var model: String
        get() = readRoot().optString("model", "deepseek-flash")
        set(v) = update { it.put("model", v) }

    var reasoning: String
        get() = readRoot().optString("reasoning", "high")
        set(v) = update { it.put("reasoning", v) }

    var maxTokens: Int
        get() = readRoot().optInt("max_tokens", 8192).coerceIn(256, 65536)
        set(v) = update { it.put("max_tokens", v.coerceIn(256, 65536)) }

    var homePage: String
        get() = readRoot().optString("home_page", "https://www.google.com").ifBlank { "https://www.google.com" }
        set(v) = update { it.put("home_page", v) }

    var searchEngine: String
        get() = readRoot().optString("search_engine", "Google")
        set(v) = update { it.put("search_engine", v) }

    var javaScript: Boolean
        get() = readRoot().optBoolean("javascript", true)
        set(v) = update { it.put("javascript", v) }

    var thirdPartyCookies: Boolean
        get() = readRoot().optBoolean("third_party_cookies", true)
        set(v) = update { it.put("third_party_cookies", v) }

    var trackerBlock: Boolean
        get() = readRoot().optBoolean("tracker_block", false)
        set(v) = update { it.put("tracker_block", v) }

    var httpsOnly: Boolean
        get() = readRoot().optBoolean("https_only", false)
        set(v) = update { it.put("https_only", v) }

    var secureAiWindows: Boolean
        get() = readRoot().optBoolean("secure_ai_windows", true)
        set(v) = update { it.put("secure_ai_windows", v) }

    var desktopByDefault: Boolean
        get() = readRoot().optBoolean("desktop_default", false)
        set(v) = update { it.put("desktop_default", v) }

    var saveHistory: Boolean
        get() = readRoot().optBoolean("save_history", true)
        set(v) = update { it.put("save_history", v) }

    var blockPopups: Boolean
        get() = readRoot().optBoolean("block_popups", true)
        set(v) = update { it.put("block_popups", v) }

    var initialPermissionsVersion: Int
        get() = readRoot().optInt("initial_permissions_version", 0)
        set(v) = update { it.put("initial_permissions_version", v) }

    var aiContextMode: String
        get() = readRoot().optString("ai_context_mode", "full").takeIf { it in setOf("full", "text", "selection", "none") } ?: "full"
        set(v) = update { it.put("ai_context_mode", v.takeIf { x -> x in setOf("full", "text", "selection", "none") } ?: "full") }

    var autoAgentMaxSteps: Int
        get() = readRoot().optInt("auto_agent_max_steps", 8).coerceIn(1, 20)
        set(v) = update { it.put("auto_agent_max_steps", v.coerceIn(1, 20)) }

    var aiPrivacyNoticeSeen: Boolean
        get() = readRoot().optBoolean("ai_privacy_notice_seen", false)
        set(v) = update { it.put("ai_privacy_notice_seen", v) }

    fun searchUrl(query: String): String {
        val encoded = java.net.URLEncoder.encode(query, Charsets.UTF_8.name())
        return when (searchEngine) {
            "DuckDuckGo" -> "https://duckduckgo.com/?q=$encoded"
            "Bing" -> "https://www.bing.com/search?q=$encoded"
            "Brave" -> "https://search.brave.com/search?q=$encoded"
            else -> "https://www.google.com/search?q=$encoded"
        }
    }
}
