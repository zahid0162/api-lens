package com.apilens.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.apilens.core.ApiErrorType
import com.apilens.core.ApiHeader
import com.apilens.core.ApiRequest
import com.apilens.core.ApiResponse
import com.apilens.core.ApiTiming
import com.apilens.core.ApiTransaction
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "api_transactions")
data class ApiEntity(
    @PrimaryKey val id: String,
    val method: String,
    val url: String,
    val scheme: String,
    val host: String,
    val path: String,
    val queryParamsJson: String,
    val requestHeadersJson: String,
    val requestBody: String?,
    val requestContentType: String?,
    val requestSizeBytes: Long,
    val requestTimestampMs: Long,
    val requestBodyTruncated: Boolean,
    val requestBodyOmittedReason: String?,
    val statusCode: Int?,
    val statusMessage: String?,
    val responseHeadersJson: String?,
    val responseBody: String?,
    val responseContentType: String?,
    val responseSizeBytes: Long?,
    val responseTimestampMs: Long?,
    val responseBodyTruncated: Boolean,
    val responseBodyOmittedReason: String?,
    val dnsLookupMs: Long?,
    val tcpConnectMs: Long?,
    val tlsHandshakeMs: Long?,
    val requestTransferMs: Long?,
    val serverResponseMs: Long?,
    val responseDownloadMs: Long?,
    val totalDurationMs: Long,
    val hasDetailedTiming: Boolean,
    val protocol: String?,
    val remoteAddress: String?,
    val errorType: String,
    val errorMessage: String?,
    val isPending: Boolean
) {
    fun toDomain(): ApiTransaction {
        val parsedError = runCatching { ApiErrorType.valueOf(errorType) }.getOrDefault(ApiErrorType.NONE)
        val req = ApiRequest(
            id = id,
            url = url,
            scheme = scheme,
            host = host,
            path = path,
            queryParams = decodePairs(queryParamsJson),
            method = method,
            headers = decodeHeaders(requestHeadersJson),
            body = requestBody,
            contentType = requestContentType,
            sizeBytes = requestSizeBytes,
            timestampMs = requestTimestampMs,
            bodyTruncated = requestBodyTruncated,
            bodyOmittedReason = requestBodyOmittedReason
        )
        val resp = if (statusCode != null && responseTimestampMs != null) {
            ApiResponse(
                statusCode = statusCode,
                message = statusMessage.orEmpty(),
                headers = decodeHeaders(responseHeadersJson.orEmpty()),
                body = responseBody,
                contentType = responseContentType,
                sizeBytes = responseSizeBytes ?: 0L,
                timestampMs = responseTimestampMs,
                bodyTruncated = responseBodyTruncated,
                bodyOmittedReason = responseBodyOmittedReason
            )
        } else {
            null
        }
        val timing = ApiTiming(
            dnsLookupMs = dnsLookupMs,
            tcpConnectMs = tcpConnectMs,
            tlsHandshakeMs = tlsHandshakeMs,
            requestTransferMs = requestTransferMs,
            serverResponseMs = serverResponseMs,
            responseDownloadMs = responseDownloadMs,
            totalDurationMs = totalDurationMs,
            hasDetailedTiming = hasDetailedTiming,
            protocol = protocol,
            remoteAddress = remoteAddress
        )
        return ApiTransaction(
            id = id,
            request = req,
            response = resp,
            timing = timing,
            errorType = parsedError,
            errorMessage = errorMessage,
            isPending = isPending
        )
    }

    companion object {
        fun fromDomain(tx: ApiTransaction): ApiEntity {
            return ApiEntity(
                id = tx.id,
                method = tx.request.method,
                url = tx.request.url,
                scheme = tx.request.scheme,
                host = tx.request.host,
                path = tx.request.path,
                queryParamsJson = encodePairs(tx.request.queryParams),
                requestHeadersJson = encodeHeaders(tx.request.headers),
                requestBody = tx.request.body,
                requestContentType = tx.request.contentType,
                requestSizeBytes = tx.request.sizeBytes,
                requestTimestampMs = tx.request.timestampMs,
                requestBodyTruncated = tx.request.bodyTruncated,
                requestBodyOmittedReason = tx.request.bodyOmittedReason,
                statusCode = tx.response?.statusCode,
                statusMessage = tx.response?.message,
                responseHeadersJson = tx.response?.let { encodeHeaders(it.headers) },
                responseBody = tx.response?.body,
                responseContentType = tx.response?.contentType,
                responseSizeBytes = tx.response?.sizeBytes,
                responseTimestampMs = tx.response?.timestampMs,
                responseBodyTruncated = tx.response?.bodyTruncated ?: false,
                responseBodyOmittedReason = tx.response?.bodyOmittedReason,
                dnsLookupMs = tx.timing.dnsLookupMs,
                tcpConnectMs = tx.timing.tcpConnectMs,
                tlsHandshakeMs = tx.timing.tlsHandshakeMs,
                requestTransferMs = tx.timing.requestTransferMs,
                serverResponseMs = tx.timing.serverResponseMs,
                responseDownloadMs = tx.timing.responseDownloadMs,
                totalDurationMs = tx.timing.totalDurationMs,
                hasDetailedTiming = tx.timing.hasDetailedTiming,
                protocol = tx.timing.protocol,
                remoteAddress = tx.timing.remoteAddress,
                errorType = tx.errorType.name,
                errorMessage = tx.errorMessage,
                isPending = tx.isPending
            )
        }

        private fun encodeHeaders(headers: List<ApiHeader>): String {
            return JSONArray().apply {
                headers.forEach { h ->
                    put(
                        JSONObject().apply {
                            put("n", h.name)
                            put("v", h.value)
                            put("r", h.isRedacted)
                        }
                    )
                }
            }.toString()
        }

        private fun decodeHeaders(raw: String): List<ApiHeader> {
            if (raw.isBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    ApiHeader(
                        name = obj.optString("n"),
                        value = obj.optString("v"),
                        isRedacted = obj.optBoolean("r", false)
                    )
                }
            }.getOrDefault(emptyList())
        }

        private fun encodePairs(pairs: List<Pair<String, String>>): String {
            return JSONArray().apply {
                pairs.forEach { (k, v) ->
                    put(
                        JSONObject().apply {
                            put("k", k)
                            put("v", v)
                        }
                    )
                }
            }.toString()
        }

        private fun decodePairs(raw: String): List<Pair<String, String>> {
            if (raw.isBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    obj.optString("k") to obj.optString("v")
                }
            }.getOrDefault(emptyList())
        }
    }
}
