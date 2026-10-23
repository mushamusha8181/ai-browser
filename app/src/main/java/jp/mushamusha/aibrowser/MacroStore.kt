package jp.mushamusha.aibrowser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class BrowserMacro(
    val name: String,
    val originHost: String,
    val version: Int,
    val actions: JSONArray
)

class MacroStore(context: Context) {
    companion object { const val CURRENT_VERSION = 2 }
    private val prefs = context.getSharedPreferences("browser_macros", Context.MODE_PRIVATE)

    fun list(): List<BrowserMacro> {
        val root = runCatching { JSONArray(prefs.getString("macros", "[]")) }.getOrDefault(JSONArray())
        return buildList {
            for (i in 0 until root.length()) {
                val obj = root.optJSONObject(i) ?: continue
                add(
                    BrowserMacro(
                        name = obj.optString("name").ifBlank { "マクロ ${i + 1}" },
                        originHost = obj.optString("originHost"),
                        version = obj.optInt("version", 1),
                        actions = obj.optJSONArray("actions") ?: JSONArray()
                    )
                )
            }
        }
    }

    fun add(name: String, originHost: String, actions: List<JSONObject>) {
        val root = JSONArray()
        list().takeLast(49).forEach { macro ->
            root.put(
                JSONObject()
                    .put("name", macro.name)
                    .put("originHost", macro.originHost)
                    .put("version", macro.version)
                    .put("actions", macro.actions)
            )
        }
        root.put(
            JSONObject()
                .put("name", name.ifBlank { "Webマクロ" })
                .put("originHost", originHost)
                .put("version", CURRENT_VERSION)
                .put("actions", JSONArray(actions))
        )
        prefs.edit().putString("macros", root.toString()).apply()
    }

    fun delete(index: Int) {
        val current = list()
        val root = JSONArray()
        current.forEachIndexed { i, macro ->
            if (i != index) {
                root.put(
                    JSONObject()
                        .put("name", macro.name)
                        .put("originHost", macro.originHost)
                        .put("version", macro.version)
                        .put("actions", macro.actions)
                )
            }
        }
        prefs.edit().putString("macros", root.toString()).apply()
    }
}
