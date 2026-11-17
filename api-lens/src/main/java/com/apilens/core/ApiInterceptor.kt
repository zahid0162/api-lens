package com.apilens.core

import com.apilens.ApiLensConfig
import com.apilens.data.ApiRepository
import com.apilens.utils.HeaderRedactor
import com.apilens.utils.JsonFormatter
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.buffer
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import javax.net.ssl.SSLException

/**
 * OkHttp [Interceptor] that captures requests, responses, headers, bodies, errors, and timings
 * without consuming or mutating host application streams.
 */
class ApiInterceptor internal constructor(
    private val configProvider: () -> ApiLensConfig,
    private val isEnabledProvider: () -> Boolean,
    private val repositoryProvider: () -> ApiRepository?
) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        if (!isEnabledProvider()) {
            return chain.proceed(originalRequest)
        }

        val repository = repositoryProvider() ?: return chain.proceed(originalRequest)
        val config = configProvider()
        val transactionId = UUID.randomUUID().toString()
        val requestStartMs = System.currentTimeMillis()
        val requestStartNs = System.nanoTime()

        val capturedRequest = buildCapturedRequest(
            id = transactionId,
            request = originalRequest,
            timestampMs = requestStartMs,
            config = config
        )

        val initialTx = ApiTransaction(
            id = transactionId,
            request = capturedRequest,
            response = null,
            timing = ApiTiming(totalDurationMs = 0L, hasDetailedTiming = false),
            errorType = ApiErrorType.NONE,
            isPending = true
        )
        repository.recordAsync(initialTx)

        val response: Response
        try {
            response = chain.proceed(originalRequest)
        } catch (ioe: IOException) {
            val totalDurationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - requestStartNs)
                .coerceAtLeast(0L)
            val errorType = classifyError(ioe)
            val timing = ApiTimingRegistry.snapshotAndAttachCompletion(
                call = chain.call(),
                fallbackTotalDurationMs = totalDurationMs,
                onComplete = { finalTiming ->
                    repository.recordAsync(
                        initialTx.copy(
                            timing = finalTiming,
                            errorType = errorType,
                            errorMessage = ioe.message ?: ioe::class.java.simpleName,
                            isPending = false
                        )
                    )
                }
            ) ?: ApiTiming(totalDurationMs = totalDurationMs, hasDetailedTiming = false)

            repository.recordAsync(
                initialTx.copy(
                    timing = timing,
                    errorType = errorType,
                    errorMessage = ioe.message ?: ioe::class.java.simpleName,
                    isPending = false
                )
            )
            throw ioe
        }

        val responseTimestampMs = System.currentTimeMillis()
        val totalDurationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - requestStartNs)
            .coerceAtLeast(0L)

        val capturedResponse = buildCapturedResponse(
            request = originalRequest,
            response = response,
            timestampMs = responseTimestampMs,
            config = config
        )

        val errorType = if (capturedResponse.isSuccess) ApiErrorType.NONE else ApiErrorType.HTTP_ERROR
        val errorMessage = if (capturedResponse.isSuccess) {
            null
        } else {
            "HTTP ${capturedResponse.statusCode} ${capturedResponse.message}".trim()
        }

        val baseCompletedTx = initialTx.copy(
            response = capturedResponse,
            errorType = errorType,
            errorMessage = errorMessage,
            isPending = false
        )

        val timing = ApiTimingRegistry.snapshotAndAttachCompletion(
            call = chain.call(),
            fallbackTotalDurationMs = totalDurationMs,
            onComplete = { finalTiming ->
                repository.recordAsync(
                    baseCompletedTx.copy(
                        timing = finalTiming.copy(
                            totalDurationMs = maxOf(finalTiming.totalDurationMs, totalDurationMs)
                        )
                    )
                )
            }
        ) ?: ApiTiming(totalDurationMs = totalDurationMs, hasDetailedTiming = false)

        repository.recordAsync(baseCompletedTx.copy(timing = timing))
        return response
    }

    private fun buildCapturedRequest(
        id: String,
        request: Request,
        timestampMs: Long,
        config: ApiLensConfig
    ): ApiRequest {
        val httpUrl = request.url
        val redactedUrl = HeaderRedactor.redactUrl(httpUrl.toString(), config.redactedQueryParams)
        val queryParams = HeaderRedactor.extractRedactedQueryParams(httpUrl, config.redactedQueryParams)
        val headersList = buildList {
            for (i in 0 until request.headers.size) {
                add(request.headers.name(i) to request.headers.value(i))
            }
        }
        val redactedHeaders = HeaderRedactor.redactHeaders(headersList, config.redactedHeaders)
        val body = request.body
        val contentType = body?.contentType()?.toString()

        var bodyText: String? = null
        var sizeBytes = 0L
        var truncated = false
        var omittedReason: String? = null

        if (body != null) {
            sizeBytes = runCatching { body.contentLength() }.getOrDefault(-1L).coerceAtLeast(0L)
            when {
                !config.enableBodyInspection -> {
                    omittedReason = "Body inspection disabled by configuration"
                }
                body.isOneShot() || body.isDuplex() -> {
                    omittedReason = "One-shot or duplex request body omitted to preserve stream"
                }
                isBinaryContentType(body.contentType()) -> {
                    omittedReason = "Binary request body (${contentType ?: "unknown"}) omitted"
                }
                else -> {
                    val result = readRequestBodyBounded(body, config.maxBodySizeBytes)
                    if (result != null) {
                        sizeBytes = maxOf(sizeBytes, result.totalWrittenBytes)
                        truncated = result.truncated
                        val charset = body.contentType()?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8
                        val rawText = result.bytes.toString(charset)
                        bodyText = JsonFormatter.redactBody(
                            raw = rawText,
                            contentType = contentType,
                            sensitiveKeys = config.redactedJsonKeys
                        )
                    } else {
                        omittedReason = "Unable to inspect request body"
                    }
                }
            }
        }

        val path = buildString {
            append(httpUrl.encodedPath)
            if (!httpUrl.encodedQuery.isNullOrEmpty()) {
                val redactedQuery = redactedUrl.substringAfter('?', "")
                if (redactedQuery.isNotEmpty()) {
                    append('?').append(redactedQuery)
                }
            }
        }

        return ApiRequest(
            id = id,
            url = redactedUrl,
            scheme = httpUrl.scheme,
            host = httpUrl.host,
            path = path.ifEmpty { "/" },
            queryParams = queryParams,
            method = request.method,
            headers = redactedHeaders,
            body = bodyText,
            contentType = contentType,
            sizeBytes = sizeBytes,
            timestampMs = timestampMs,
            bodyTruncated = truncated,
            bodyOmittedReason = omittedReason
        )
    }

    private fun buildCapturedResponse(
        request: Request,
        response: Response,
        timestampMs: Long,
        config: ApiLensConfig
    ): ApiResponse {
        val headersList = buildList {
            for (i in 0 until response.headers.size) {
                add(response.headers.name(i) to response.headers.value(i))
            }
        }
        val redactedHeaders = HeaderRedactor.redactHeaders(headersList, config.redactedHeaders)
        val respBody = response.body
        val contentTypeMedia = respBody?.contentType()
        val contentType = contentTypeMedia?.toString()
        val declaredLength = runCatching { respBody?.contentLength() ?: -1L }.getOrDefault(-1L)

        var bodyText: String? = null
        var sizeBytes = if (declaredLength >= 0L) declaredLength else 0L
        var truncated = false
        var omittedReason: String? = null

        val hasBody = request.method != "HEAD" &&
            response.code != 204 &&
            response.code != 304 &&
            respBody != null

        if (hasBody) {
            when {
                !config.enableBodyInspection -> {
                    omittedReason = "Body inspection disabled by configuration"
                }
                isStreamingEventType(contentTypeMedia) -> {
                    omittedReason = "Streaming response (${contentType ?: "event-stream"}) omitted"
                }
                isBinaryContentType(contentTypeMedia) -> {
                    omittedReason = "Binary response (${contentType ?: "binary"}) omitted"
                }
                else -> {
                    val peekLimit = (config.maxBodySizeBytes + 1L).coerceAtLeast(1L)
                    val peekedBytes = runCatching {
                        response.peekBody(peekLimit).bytes()
                    }.getOrNull()

                    if (peekedBytes != null) {
                        val isOverLimit = peekedBytes.size.toLong() > config.maxBodySizeBytes
                        truncated = isOverLimit
                        val boundedBytes = if (isOverLimit) {
                            peekedBytes.copyOf(config.maxBodySizeBytes.toInt())
                        } else {
                            peekedBytes
                        }
                        if (declaredLength < 0L) {
                            sizeBytes = peekedBytes.size.toLong()
                        }

                        val decodedBytes = maybeDecompressGzip(
                            bytes = boundedBytes,
                            contentEncoding = response.header("Content-Encoding")
                        )
                        val charset: Charset = contentTypeMedia?.charset(StandardCharsets.UTF_8)
                            ?: StandardCharsets.UTF_8
                        val rawString = decodedBytes.toString(charset)
                        bodyText = JsonFormatter.redactBody(
                            raw = rawString,
                            contentType = contentType,
                            sensitiveKeys = config.redactedJsonKeys
                        )
                    } else {
                        omittedReason = "Unable to peek response body"
                    }
                }
            }
        }

        return ApiResponse(
            statusCode = response.code,
            message = response.message,
            headers = redactedHeaders,
            body = bodyText,
            contentType = contentType,
            sizeBytes = sizeBytes,
            timestampMs = timestampMs,
            bodyTruncated = truncated,
            bodyOmittedReason = omittedReason
        )
    }

    private data class BoundedReadResult(
        val bytes: ByteArray,
        val totalWrittenBytes: Long,
        val truncated: Boolean
    )

    private fun readRequestBodyBounded(
        body: okhttp3.RequestBody,
        maxBytes: Long
    ): BoundedReadResult? {
        return runCatching {
            val captured = Buffer()
            var totalBytes = 0L
            val sink = object : ForwardingSink(Buffer()) {
                override fun write(source: Buffer, byteCount: Long) {
                    totalBytes += byteCount
                    val remaining = (maxBytes - captured.size).coerceAtLeast(0L)
                    if (remaining > 0L) {
                        val toCopy = minOf(remaining, byteCount)
                        val slice = Buffer()
                        // Copy without mutating the source passed in if needed; source is owned by write()
                        source.copyTo(slice, 0L, toCopy)
                        captured.write(slice, toCopy)
                    }
                    source.skip(byteCount)
                }
            }
            val bufferedSink: BufferedSink = sink.buffer()
            body.writeTo(bufferedSink)
            bufferedSink.flush()
            BoundedReadResult(
                bytes = captured.readByteArray(),
                totalWrittenBytes = totalBytes,
                truncated = totalBytes > maxBytes
            )
        }.getOrNull()
    }

    private fun maybeDecompressGzip(bytes: ByteArray, contentEncoding: String?): ByteArray {
        val isGzipHeader = contentEncoding?.equals("gzip", ignoreCase = true) == true
        val hasGzipMagic = bytes.size >= 2 &&
            bytes[0] == 0x1f.toByte() &&
            bytes[1] == 0x8b.toByte()
        if (!isGzipHeader && !hasGzipMagic) return bytes

        return runCatching {
            GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
        }.getOrDefault(bytes)
    }

    private fun isBinaryContentType(mediaType: MediaType?): Boolean {
        if (mediaType == null) return false
        val type = mediaType.type.lowercase()
        val subtype = mediaType.subtype.lowercase()
        if (type == "image" || type == "video" || type == "audio") return true
        if (type == "application" && (
                subtype == "octet-stream" ||
                    subtype == "pdf" ||
                    subtype == "zip" ||
                    subtype == "gzip" ||
                    subtype == "protobuf" ||
                    subtype == "x-protobuf"
                )
        ) {
            return true
        }
        return false
    }

    private fun isStreamingEventType(mediaType: MediaType?): Boolean {
        if (mediaType == null) return false
        return mediaType.type.equals("text", ignoreCase = true) &&
            mediaType.subtype.equals("event-stream", ignoreCase = true)
    }

    internal fun classifyError(throwable: IOException): ApiErrorType {
        return when (throwable) {
            is UnknownHostException -> ApiErrorType.UNKNOWN_HOST
            is SocketTimeoutException -> {
                val msg = throwable.message?.lowercase().orEmpty()
                if (msg.contains("connect")) ApiErrorType.CONNECTION_TIMEOUT
                else ApiErrorType.SOCKET_TIMEOUT
            }
            is ConnectException -> {
                val msg = throwable.message?.lowercase().orEmpty()
                if (msg.contains("timed out") || msg.contains("timeout")) {
                    ApiErrorType.CONNECTION_TIMEOUT
                } else {
                    ApiErrorType.CONNECTION_FAILURE
                }
            }
            is SSLException -> ApiErrorType.SSL_ERROR
            else -> ApiErrorType.NETWORK_ERROR
        }
    }
}
