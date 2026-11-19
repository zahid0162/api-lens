package com.apilens

/**
 * Color theme options for the API Lens debugging dashboard and detail screens.
 */
enum class ApiLensTheme {
    DARK,
    LIGHT,
    SYSTEM
}

/**
 * Default anchor positions for the floating debug overlay button.
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
 * Configuration for [ApiLens].
 *
 * All fields have sensible defaults and support both Kotlin named arguments
 * and Java via [Builder] or overloaded constructors.
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
    init {
        require(maxRequests > 0) { "maxRequests must be greater than 0" }
        require(maxBodySizeBytes >= 0L) { "maxBodySizeBytes must be >= 0" }
        require(slowRequestThresholdMs > 0L) { "slowRequestThresholdMs must be > 0" }
    }

    /**
     * Java-friendly builder for constructing [ApiLensConfig].
     */
    class Builder {
        private var showFloatingButton: Boolean = true
        private var floatingButtonPosition: FloatingButtonPosition = FloatingButtonPosition.BOTTOM_END
        private var maxRequests: Int = DEFAULT_MAX_REQUESTS
        private var enableDatabase: Boolean = true
        private var enableBodyInspection: Boolean = true
        private var enablePerformanceInspector: Boolean = true
        private var maxBodySizeBytes: Long = DEFAULT_MAX_BODY_SIZE_BYTES
        private var slowRequestThresholdMs: Long = DEFAULT_SLOW_THRESHOLD_MS
        private var redactedHeaders: Set<String> = DEFAULT_REDACTED_HEADERS
        private var redactedJsonKeys: Set<String> = DEFAULT_REDACTED_JSON_KEYS
        private var redactedQueryParams: Set<String> = DEFAULT_REDACTED_QUERY_PARAMS
        private var theme: ApiLensTheme = ApiLensTheme.DARK

        fun showFloatingButton(show: Boolean) = apply { this.showFloatingButton = show }
        fun floatingButtonPosition(position: FloatingButtonPosition) = apply {
            this.floatingButtonPosition = position
        }
        fun maxRequests(maxRequests: Int) = apply { this.maxRequests = maxRequests }
        fun enableDatabase(enable: Boolean) = apply { this.enableDatabase = enable }
        fun enableBodyInspection(enable: Boolean) = apply { this.enableBodyInspection = enable }
        fun enablePerformanceInspector(enable: Boolean) = apply {
            this.enablePerformanceInspector = enable
        }
        fun maxBodySizeBytes(maxBytes: Long) = apply { this.maxBodySizeBytes = maxBytes }
        fun slowRequestThresholdMs(thresholdMs: Long) = apply {
            this.slowRequestThresholdMs = thresholdMs
        }
        fun redactedHeaders(headers: Set<String>) = apply { this.redactedHeaders = headers }
        fun redactedJsonKeys(keys: Set<String>) = apply { this.redactedJsonKeys = keys }
        fun redactedQueryParams(params: Set<String>) = apply { this.redactedQueryParams = params }
        fun theme(theme: ApiLensTheme) = apply { this.theme = theme }

        fun build(): ApiLensConfig = ApiLensConfig(
            showFloatingButton = showFloatingButton,
            floatingButtonPosition = floatingButtonPosition,
            maxRequests = maxRequests,
            enableDatabase = enableDatabase,
            enableBodyInspection = enableBodyInspection,
            enablePerformanceInspector = enablePerformanceInspector,
            maxBodySizeBytes = maxBodySizeBytes,
            slowRequestThresholdMs = slowRequestThresholdMs,
            redactedHeaders = redactedHeaders,
            redactedJsonKeys = redactedJsonKeys,
            redactedQueryParams = redactedQueryParams,
            theme = theme
        )
    }

    companion object {
        const val DEFAULT_MAX_REQUESTS: Int = 500
        const val DEFAULT_MAX_BODY_SIZE_BYTES: Long = 256L * 1024L // 256 KB
        const val DEFAULT_SLOW_THRESHOLD_MS: Long = 1000L

        val DEFAULT_REDACTED_HEADERS: Set<String> = setOf(
            "authorization",
            "proxy-authorization",
            "cookie",
            "set-cookie",
            "x-api-key",
            "x-auth-token",
            "x-access-token",
            "x-refresh-token",
            "x-csrf-token"
        )

        val DEFAULT_REDACTED_JSON_KEYS: Set<String> = setOf(
            "password",
            "passwd",
            "pass",
            "secret",
            "token",
            "access_token",
            "accesstoken",
            "refresh_token",
            "refreshtoken",
            "id_token",
            "idtoken",
            "api_key",
            "apikey",
            "client_secret",
            "clientsecret",
            "authorization",
            "credit_card",
            "creditcard",
            "card_number",
            "cardnumber",
            "cvv",
            "cvc",
            "ssn",
            "pin"
        )

        val DEFAULT_REDACTED_QUERY_PARAMS: Set<String> = setOf(
            "api_key",
            "apikey",
            "token",
            "access_token",
            "secret",
            "password",
            "auth",
            "key"
        )
    }
}
