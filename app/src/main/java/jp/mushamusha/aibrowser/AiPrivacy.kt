package jp.mushamusha.aibrowser

import java.net.URI

object AiPrivacy {
    private val sensitiveQuery = Regex(
        "(?i)([?&#](?:access_token|refresh_token|id_token|token|auth|authorization|api[_-]?key|apikey|key|signature|sig|session(?:id)?|sid|code|state|jwt|credential|secret|csrf(?:[_-]?token)?|xsrf(?:[_-]?token)?|nonce|x-amz-[a-z0-9_-]+|x-goog-[a-z0-9_-]+)=)[^&#\\s\"']*"
    )
    private val bearer = Regex("(?i)bearer\\s+[A-Za-z0-9._~+/-]{12,}")
    private val jsonSecret = Regex(
        "(?i)(\\\"(?:access_token|refresh_token|id_token|(?:data[-_])?token|authorization|api[_-]?key|apikey|key|signature|session(?:id)?|jwt|credential|secret|csrf(?:[_-]?token)?|xsrf(?:[_-]?token)?|nonce)\\\"\\s*:\\s*\\\")[^\\\"]*(\\\")"
    )
    private val urlLike = Regex("https?://[^\\s<>\"']{1,8192}", RegexOption.IGNORE_CASE)

    fun sanitizeText(text: String): String {
        if (text.isBlank()) return text
        var out = text
            .replace(sensitiveQuery, "$1[REDACTED]")
            .replace(bearer, "Bearer [REDACTED]")
            .replace(jsonSecret, "$1[REDACTED]$2")
        out = urlLike.replace(out) { sanitizeUrl(it.value) }
        return out
    }

    fun sanitizeUrl(url: String): String {
        if (url.isBlank()) return url
        var result = url.replace(sensitiveQuery, "$1[REDACTED]")
        result = runCatching {
            val uri = URI(result)
            val cleanedFragment = uri.fragment?.let { fragment ->
                if (Regex("(?i)(token|code|state|signature|credential|secret|key|csrf|xsrf|nonce)=").containsMatchIn(fragment)) "[REDACTED]" else fragment
            }
            if (!uri.userInfo.isNullOrBlank() || cleanedFragment != uri.fragment) {
                URI(uri.scheme, null, uri.host, uri.port, uri.path, uri.query, cleanedFragment).toString()
            } else result
        }.getOrDefault(result)
        return result
    }

    fun containsSensitiveUrlData(url: String): Boolean {
        if (url.isBlank()) return false
        if (sensitiveQuery.containsMatchIn(url)) return true
        return runCatching {
            val uri = URI(url)
            !uri.userInfo.isNullOrBlank() || Regex("(?i)(token|code|state|signature|credential|secret|key|csrf|xsrf|nonce)=").containsMatchIn(uri.fragment.orEmpty())
        }.getOrDefault(false)
    }
}
