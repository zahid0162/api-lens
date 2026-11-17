package com.apilens.utils

import com.apilens.core.ApiHeader
import com.apilens.core.ApiTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Formatting, cURL generation, sharing, and JSON export utilities.
 */
object RequestFormatter {

    fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return "0 B"
        return when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }

    fun formatShortTime(timestampMs: Long): String {
        val formatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        return formatter.format(Date(timestampMs))
    }

    fun formatFullTimestamp(timestampMs: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        return formatter.format(Date(timestampMs))
    }

    fun toCurlCommand(transaction: ApiTransaction): String {
        val req = transaction.request
        val sb = StringBuilder("curl -X ${req.method}")
        req.headers.forEach { header ->
            sb.append(" \\\n  -H '${escapeShell(header.name)}: ${escapeShell(header.value)}'")
        }
        if (!req.body.isNullOrBlank()) {
            val singleLineBody = req.body.replace("\r\n", "\n").replace("\n", " ")
            sb.append(" \\\n  -d '${escapeShell(singleLineBody)}'")
        }
        sb.append(" \\\n  '${escapeShell(req.url)}'")
        return sb.toString()
    }

    private fun escapeShell(value: String): String = value.replace("'", "'\"'\"'")

    fun toShareSummary(transaction: ApiTransaction): String {
        val req = transaction.request
        val resp = transaction.response
        return buildString {
            appendLine("=== API Lens Trace ===")
            appendLine("${req.method} ${req.url}")
            appendLine("Time: ${formatFullTimestamp(req.timestampMs)}")
            if (resp != null) {
                appendLine("Status: ${resp.statusCode} ${resp.message}")
            } else {
                appendLine("Status: ${transaction.errorType.label} (${transaction.errorMessage.orEmpty()})")
            }
            appendLine("Duration: ${transaction.timing.totalDurationMs} ms")
            appendLine("Sizes: Req ${formatBytes(req.sizeBytes)} | Resp ${formatBytes(resp?.sizeBytes ?: 0L)}")
            appendLine()
            appendLine("--- Request Headers ---")
            req.headers.forEach { appendLine("${it.name}: ${it.value}") }
            if (!req.body.isNullOrBlank()) {
                appendLine()
                appendLine("--- Request Body ---")
                appendLine(req.body)
            }
            if (resp != null) {
                appendLine()
                appendLine("--- Response Headers ---")
                resp.headers.forEach { appendLine("${it.name}: ${it.value}") }
                if (!resp.body.isNullOrBlank()) {
                    appendLine()
                    appendLine("--- Response Body ---")
                    appendLine(resp.body)
                }
            }
        }
    }

    fun exportTransactionsToJson(transactions: List<ApiTransaction>): String {
        val root = JSONObject()
        root.put("exportedAt", formatFullTimestamp(System.currentTimeMillis()))
        root.put("count", transactions.size)
        val array = JSONArray()
        transactions.forEach { tx ->
            val item = JSONObject()
            item.put("id", tx.id)
            item.put("isSuccess", tx.isSuccess)
            item.put("errorType", tx.errorType.name)
            item.put("errorMessage", tx.errorMessage)

            val reqObj = JSONObject().apply {
                put("method", tx.request.method)
                put("url", tx.request.url)
                put("timestampMs", tx.request.timestampMs)
                put("contentType", tx.request.contentType)
                put("sizeBytes", tx.request.sizeBytes)
                put("headers", headersToJson(tx.request.headers))
                put("body", tx.request.body)
            }
            item.put("request", reqObj)

            tx.response?.let { resp ->
                val respObj = JSONObject().apply {
                    put("statusCode", resp.statusCode)
                    put("message", resp.message)
                    put("timestampMs", resp.timestampMs)
                    put("contentType", resp.contentType)
                    put("sizeBytes", resp.sizeBytes)
                    put("headers", headersToJson(resp.headers))
                    put("body", resp.body)
                }
                item.put("response", respObj)
            }

            val timingObj = JSONObject().apply {
                put("totalDurationMs", tx.timing.totalDurationMs)
                put("dnsLookupMs", tx.timing.dnsLookupMs ?: JSONObject.NULL)
                put("tcpConnectMs", tx.timing.tcpConnectMs ?: JSONObject.NULL)
                put("tlsHandshakeMs", tx.timing.tlsHandshakeMs ?: JSONObject.NULL)
                put("requestTransferMs", tx.timing.requestTransferMs ?: JSONObject.NULL)
                put("serverResponseMs", tx.timing.serverResponseMs ?: JSONObject.NULL)
                put("responseDownloadMs", tx.timing.responseDownloadMs ?: JSONObject.NULL)
            }
            item.put("timing", timingObj)

            array.put(item)
        }
        root.put("transactions", array)
        return root.toString(2)
    }

    private fun headersToJson(headers: List<ApiHeader>): JSONArray {
        return JSONArray().apply {
            headers.forEach { header ->
                put(
                    JSONObject().apply {
                        put("name", header.name)
                        put("value", header.value)
                        put("redacted", header.isRedacted)
                    }
                )
            }
        }
    }
}
