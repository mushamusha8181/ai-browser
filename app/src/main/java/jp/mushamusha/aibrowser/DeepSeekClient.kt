package jp.mushamusha.aibrowser

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ChatTurn(val role: String, val content: String)

/**
 * DeepSeek traffic is deliberately isolated from WebView traffic.
 * This client only accepts https://api.deepseek.com URLs and never logs headers/bodies.
 */
class DeepSeekClient {
    private val deepSeekOnly = Interceptor { chain ->
        val request = chain.request()
        val url = request.url
        check(url.isHttps && url.host == "api.deepseek.com") { "Blocked non-DeepSeek AI endpoint" }
        chain.proceed(request)
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(deepSeekOnly)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(240, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun chat(
        apiKey: String,
        model: String,
        reasoningEffort: String,
        maxTokens: Int,
        system: String,
        user: String,
        history: List<ChatTurn> = emptyList(),
        jsonMode: Boolean = false
    ): Result<String> = resultOf {
            val effort = normalizedEffort(reasoningEffort)
            val bodyJson = JSONObject().apply {
                put("model", normalizedModel(model))
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", system))
                    boundedHistory(history).forEach { turn ->
                        put(JSONObject().put("role", turn.role).put("content", turn.content))
                    }
                    put(JSONObject().put("role", "user").put("content", user))
                })
                putThinking(this, effort)
                put("max_tokens", maxTokens.coerceIn(256, 65536))
                put("stream", false)
                if (jsonMode) put("response_format", JSONObject().put("type", "json_object"))
            }
            extractMessage(post(apiKey, "/chat/completions", bodyJson), "DeepSeek returned an empty response")
    }

    suspend fun vision(
        apiKey: String,
        reasoningEffort: String,
        maxTokens: Int,
        system: String,
        user: String,
        imageBase64: String,
        mimeType: String = "image/jpeg"
    ): Result<String> = resultOf {
            require(imageBase64.isNotBlank()) { "Image data is empty" }
            val content = JSONArray().apply {
                put(JSONObject().put("type", "text").put("text", user))
                put(JSONObject().put("type", "image_url").put("image_url", JSONObject().apply {
                    put("url", "data:$mimeType;base64,$imageBase64")
                    put("detail", "high")
                }))
            }
            visionChat(apiKey, reasoningEffort, maxTokens, system, content)
    }

    suspend fun visionUrl(
        apiKey: String,
        reasoningEffort: String,
        maxTokens: Int,
        system: String,
        user: String,
        imageUrl: String
    ): Result<String> = resultOf {
            require(imageUrl.startsWith("https://") || imageUrl.startsWith("http://")) { "Unsupported image URL" }
            require(imageUrl.length <= 8192) { "Image URL is too long" }
            require(!AiPrivacy.containsSensitiveUrlData(imageUrl)) {
                "認証トークン等を含む可能性がある画像URLは外部AIへ直接送信しません。画面画像解析を使ってください。"
            }
            val content = JSONArray().apply {
                put(JSONObject().put("type", "text").put("text", user))
                put(JSONObject().put("type", "image_url").put("image_url", JSONObject().apply {
                    put("url", imageUrl)
                    put("detail", "high")
                }))
            }
            visionChat(apiKey, reasoningEffort, maxTokens, system, content)
    }

    suspend fun search(apiKey: String, query: String, maxResults: Int = 10, needContent: Boolean = false): Result<String> = resultOf {
            val normalized = query.trim()
            require(normalized.isNotEmpty()) { "Search query is empty" }
            require(normalized.length <= 128) { "DeepSeek Searchの検索語は128文字以内にしてください" }
            val body = JSONObject()
                .put("query", normalized)
                .put("count", maxResults.coerceIn(1, 50))
                .put("need_content", needContent)
            post(apiKey, "/v1/tools/search", body).toString(2)
    }

    private suspend fun visionChat(
        apiKey: String,
        reasoningEffort: String,
        maxTokens: Int,
        system: String,
        content: JSONArray
    ): String {
        val effort = normalizedEffort(reasoningEffort)
        val bodyJson = JSONObject().apply {
            put("model", "deepseek-flash")
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", system))
                put(JSONObject().put("role", "user").put("content", content))
            })
            putThinking(this, effort)
            put("max_tokens", maxTokens.coerceIn(256, 65536))
            put("stream", false)
        }
        return extractMessage(post(apiKey, "/chat/completions", bodyJson), "DeepSeek returned an empty vision response")
    }

    private suspend fun <T> resultOf(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (t: Throwable) {
        if (t is CancellationException) throw t
        Result.failure(t)
    }

    private fun normalizedModel(model: String): String =
        model.takeIf { it in setOf("deepseek-flash", "deepseek-v4-pro") } ?: "deepseek-flash"

    private fun normalizedEffort(reasoningEffort: String): String =
        reasoningEffort.takeIf { it in setOf("none", "low", "high", "max") } ?: "high"

    private fun putThinking(root: JSONObject, effort: String) {
        root.put("reasoning_effort", effort)
        root.put("thinking", JSONObject().put("type", if (effort == "none") "disabled" else "enabled"))
    }

    private fun extractMessage(root: JSONObject, emptyMessage: String): String =
        root.getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            .optString("content").ifBlank { error(emptyMessage) }

    private fun boundedHistory(history: List<ChatTurn>, maxMessages: Int = 20, maxChars: Int = 120_000): List<ChatTurn> {
        var remaining = maxChars
        val kept = ArrayList<ChatTurn>()
        history.asReversed().forEach { turn ->
            if (kept.size >= maxMessages || remaining <= 0) return@forEach
            if (turn.role != "user" && turn.role != "assistant") return@forEach
            val content = turn.content.takeLast(minOf(60_000, remaining))
            if (content.isNotBlank()) {
                kept += ChatTurn(turn.role, content)
                remaining -= content.length
            }
        }
        return kept.asReversed()
    }

    private suspend fun post(apiKey: String, path: String, bodyJson: JSONObject): JSONObject {
        require(apiKey.isNotBlank()) { "API key is empty" }
        val request = Request.Builder()
            .url("https://api.deepseek.com$path")
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .post(bodyJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
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
                    try {
                        response.use { r ->
                            val text = r.body?.string().orEmpty()
                            if (!r.isSuccessful) {
                                val safeMessage = runCatching {
                                    JSONObject(text).optJSONObject("error")?.optString("message")
                                }.getOrNull()
                                throw IllegalStateException("DeepSeek API ${r.code}: ${safeMessage ?: "request failed"}")
                            }
                            val json = JSONObject(text)
                            continuation.resumeWith(Result.success(json))
                        }
                    } catch (t: Throwable) {
                        continuation.resumeWith(Result.failure(t))
                    }
                }
            })
        }
    }
}
