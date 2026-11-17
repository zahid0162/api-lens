package com.apilens.utils

import com.apilens.core.ApiHeader
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Redacts sensitive HTTP headers and URL query parameters before persistence or display.
 */
object HeaderRedactor {

    private const val REDACTED_PLACEHOLDER = "•••••••• (REDACTED)"

    fun redactHeaders(
        headers: List<Pair<String, String>>,
        sensitiveHeaderNames: Set<String>
    ): List<ApiHeader> {
        val normalizedSensitive = sensitiveHeaderNames.map { it.trim().lowercase() }.toSet()
        return headers.map { (name, value) ->
            if (name.trim().lowercase() in normalizedSensitive) {
                ApiHeader(
                    name = name,
                    value = redactHeaderValue(name, value),
                    isRedacted = true
                )
            } else {
                ApiHeader(name = name, value = value, isRedacted = false)
            }
        }
    }

    private fun redactHeaderValue(name: String, value: String): String {
        val lower = name.trim().lowercase()
        if (lower == "authorization" || lower == "proxy-authorization") {
            val spaceIndex = value.indexOf(' ')
            if (spaceIndex in 1..20) {
                val scheme = value.substring(0, spaceIndex)
                return "$scheme $REDACTED_PLACEHOLDER"
            }
        }
        return REDACTED_PLACEHOLDER
    }

    fun redactUrl(rawUrl: String, sensitiveQueryParams: Set<String>): String {
        val parsed = rawUrl.toHttpUrlOrNull() ?: return rawUrl
        if (parsed.querySize == 0) return rawUrl
        val normalizedSensitive = sensitiveQueryParams.map { it.trim().lowercase() }.toSet()
        var changed = false
        val builder = parsed.newBuilder()
        for (i in 0 until parsed.querySize) {
            val paramName = parsed.queryParameterName(i)
            if (paramName.lowercase() in normalizedSensitive) {
                changed = true
            }
        }
        if (!changed) return rawUrl
        // Rebuild query preserving parameter order while redacting sensitive values
        val rebuiltQuery = buildList {
            for (i in 0 until parsed.querySize) {
                val paramName = parsed.queryParameterName(i)
                val paramValue = parsed.queryParameterValue(i)
                if (paramName.lowercase() in normalizedSensitive) {
                    add(paramName to "REDACTED")
                } else {
                    add(paramName to paramValue)
                }
            }
        }
        builder.query(null)
        rebuiltQuery.forEach { (k, v) ->
            builder.addQueryParameter(k, v)
        }
        return builder.build().toString()
    }

    fun extractRedactedQueryParams(
        httpUrl: HttpUrl,
        sensitiveQueryParams: Set<String>
    ): List<Pair<String, String>> {
        if (httpUrl.querySize == 0) return emptyList()
        val normalizedSensitive = sensitiveQueryParams.map { it.trim().lowercase() }.toSet()
        val params = ArrayList<Pair<String, String>>(httpUrl.querySize)
        for (i in 0 until httpUrl.querySize) {
            val key = httpUrl.queryParameterName(i)
            val rawValue = httpUrl.queryParameterValue(i).orEmpty()
            val value = if (key.lowercase() in normalizedSensitive) {
                REDACTED_PLACEHOLDER
            } else {
                rawValue
            }
            params.add(key to value)
        }
        return params
    }
}
