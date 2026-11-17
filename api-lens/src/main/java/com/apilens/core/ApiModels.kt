package com.apilens.core

/**
 * Categorized failure state for a captured HTTP transaction.
 */
enum class ApiErrorType(val label: String) {
    NONE("None"),
    HTTP_ERROR("HTTP Error"),
    SOCKET_TIMEOUT("Socket Timeout"),
    CONNECTION_TIMEOUT("Connection Timeout"),
    UNKNOWN_HOST("Unknown Host"),
    SSL_ERROR("SSL / TLS Error"),
    CONNECTION_FAILURE("Connection Failure"),
    NETWORK_ERROR("Network Error")
}

/**
 * Single HTTP header entry with redaction state tracking.
 */
data class ApiHeader(
    val name: String,
    val value: String,
    val isRedacted: Boolean = false
)

/**
 * Network timing breakdown captured via OkHttp's EventListener when attached,
 * combined with overall interceptor duration.
 *
 * Null phase durations explicitly indicate the timing event was not observed
 * (for example, reused pooled connections or when EventListener is not attached),
 * distinguishing unavailable metrics from an actual 0ms measurement.
 */
data class ApiTiming(
    val dnsLookupMs: Long? = null,
    val tcpConnectMs: Long? = null,
    val tlsHandshakeMs: Long? = null,
    val requestTransferMs: Long? = null,
    val serverResponseMs: Long? = null,
    val responseDownloadMs: Long? = null,
    val totalDurationMs: Long = 0L,
    val hasDetailedTiming: Boolean = false,
    val protocol: String? = null,
    val remoteAddress: String? = null
)

/**
 * Captured HTTP request details (post-redaction).
 */
data class ApiRequest(
    val id: String,
    val url: String,
    val scheme: String,
    val host: String,
    val path: String,
    val queryParams: List<Pair<String, String>>,
    val method: String,
    val headers: List<ApiHeader>,
    val body: String?,
    val contentType: String?,
    val sizeBytes: Long,
    val timestampMs: Long,
    val bodyTruncated: Boolean = false,
    val bodyOmittedReason: String? = null
)

/**
 * Captured HTTP response details (post-redaction).
 */
data class ApiResponse(
    val statusCode: Int,
    val message: String,
    val headers: List<ApiHeader>,
    val body: String?,
    val contentType: String?,
    val sizeBytes: Long,
    val timestampMs: Long,
    val bodyTruncated: Boolean = false,
    val bodyOmittedReason: String? = null
) {
    val isSuccess: Boolean
        get() = statusCode in 200..299
}

/**
 * Complete captured HTTP transaction combining request, optional response,
 * timing metrics, and categorized error state.
 */
data class ApiTransaction(
    val id: String,
    val request: ApiRequest,
    val response: ApiResponse? = null,
    val timing: ApiTiming = ApiTiming(),
    val errorType: ApiErrorType = ApiErrorType.NONE,
    val errorMessage: String? = null,
    val isPending: Boolean = false
) {
    val isSuccess: Boolean
        get() = !isPending && errorType == ApiErrorType.NONE && (response?.isSuccess == true)

    val isFailure: Boolean
        get() = !isPending && (errorType != ApiErrorType.NONE || (response != null && !response.isSuccess))
}
