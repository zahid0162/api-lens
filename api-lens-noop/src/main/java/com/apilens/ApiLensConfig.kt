package com.apilens

/**
 * No-op mirror of [ApiLensTheme] for release builds.
 */
enum class ApiLensTheme {
    DARK,
    LIGHT,
    SYSTEM
}

/**
 * No-op mirror of [FloatingButtonPosition] for release builds.
 */
enum class FloatingButtonPosition {
    TOP_START,
    TOP_END,
    CENTER_START,
    CENTER_END,
    BOTTOM_START,
    BOTTOM_END
}

/**
 * No-op mirror of [ApiLensConfig] exposing identical constructors and builder API for release builds.
 */
data class ApiLensConfig @JvmOverloads constructor(
    val showFloatingButton: Boolean = true,
    val floatingButtonPosition: FloatingButtonPosition = FloatingButtonPosition.BOTTOM_END,
    val maxRequests: Int = DEFAULT_MAX_REQUESTS,
    val enableDatabase: Boolean = true,
    val enableBodyInspection: Boolean = true,
    val enablePerformanceInspector: Boolean = true,
    val maxBodySizeBytes: Long = DEFAULT_MAX_BODY_SIZE_BYTES,
    val slowRequestThresholdMs: Long = DEFAULT_SLOW_THRESHOLD_MS,
    val redactedHeaders: Set<String> = DEFAULT_REDACTED_HEADERS,
    val redactedJsonKeys: Set<String> = DEFAULT_REDACTED_JSON_KEYS,
    val redactedQueryParams: Set<String> = DEFAULT_REDACTED_QUERY_PARAMS,
    val theme: ApiLensTheme = ApiLensTheme.DARK
) {
    class Builder {
        private var config = ApiLensConfig()

        fun showFloatingButton(show: Boolean) = apply { config = config.copy(showFloatingButton = show) }
        fun floatingButtonPosition(position: FloatingButtonPosition) = apply {
            config = config.copy(floatingButtonPosition = position)
        }
        fun maxRequests(maxRequests: Int) = apply { config = config.copy(maxRequests = maxRequests) }
        fun enableDatabase(enable: Boolean) = apply { config = config.copy(enableDatabase = enable) }
        fun enableBodyInspection(enable: Boolean) = apply { config = config.copy(enableBodyInspection = enable) }
        fun enablePerformanceInspector(enable: Boolean) = apply {
            config = config.copy(enablePerformanceInspector = enable)
        }
        fun maxBodySizeBytes(maxBytes: Long) = apply { config = config.copy(maxBodySizeBytes = maxBytes) }
        fun slowRequestThresholdMs(thresholdMs: Long) = apply {
            config = config.copy(slowRequestThresholdMs = thresholdMs)
        }
        fun redactedHeaders(headers: Set<String>) = apply { config = config.copy(redactedHeaders = headers) }
        fun redactedJsonKeys(keys: Set<String>) = apply { config = config.copy(redactedJsonKeys = keys) }
        fun redactedQueryParams(params: Set<String>) = apply { config = config.copy(redactedQueryParams = params) }
        fun theme(theme: ApiLensTheme) = apply { config = config.copy(theme = theme) }
        fun build(): ApiLensConfig = config
    }

    companion object {
        const val DEFAULT_MAX_REQUESTS: Int = 500
        const val DEFAULT_MAX_BODY_SIZE_BYTES: Long = 256L * 1024L
        const val DEFAULT_SLOW_THRESHOLD_MS: Long = 1000L
        val DEFAULT_REDACTED_HEADERS: Set<String> = emptySet()
        val DEFAULT_REDACTED_JSON_KEYS: Set<String> = emptySet()
        val DEFAULT_REDACTED_QUERY_PARAMS: Set<String> = emptySet()
    }
}
