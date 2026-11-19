package com.apilens.performance

import com.apilens.core.ApiTransaction
import kotlin.math.ceil

/**
 * Computes network latency percentiles, TTFB averages, connection reuse ratios,
 * and per-endpoint bottleneck breakdowns from real captured [ApiTransaction] records.
 */
object NetworkPerfAnalyzer {

    fun analyze(
        transactions: List<ApiTransaction>,
        slowThresholdMs: Long,
        sortMode: EndpointSortMode = EndpointSortMode.SLOWEST
    ): NetworkPerfSummary {
        val completed = transactions.filter { !it.isPending }
        if (completed.isEmpty()) {
            return NetworkPerfSummary()
        }

        val sortedLatencies = completed.map { it.timing.totalDurationMs }.sorted()
        val p50 = percentile(sortedLatencies, 50)
        val p90 = percentile(sortedLatencies, 90)
        val p95 = percentile(sortedLatencies, 95)
        val maxMs = sortedLatencies.last()

        val ttfbValues = completed.mapNotNull { it.timing.serverResponseMs }
        val avgTtfb = if (ttfbValues.isNotEmpty()) ttfbValues.sum() / ttfbValues.size else null

        val detailedTimingCalls = completed.filter { it.timing.hasDetailedTiming }
        val reusePercent = if (detailedTimingCalls.isNotEmpty()) {
            val reused = detailedTimingCalls.count {
                it.timing.dnsLookupMs == null && it.timing.tcpConnectMs == null
            }
            ((reused.toFloat() / detailedTimingCalls.size.toFloat()) * 100f).toInt()
        } else {
            null
        }

        val totalBytes = completed.sumOf { tx ->
            tx.request.sizeBytes + (tx.response?.sizeBytes ?: 0L)
        }
        val slowCount = completed.count { it.timing.totalDurationMs >= slowThresholdMs }

        val recentSamples = completed
            .sortedBy { it.request.timestampMs }
            .takeLast(30)
            .map { tx ->
                LatencySample(
                    id = tx.id,
                    method = tx.request.method,
                    path = tx.request.path.substringBefore('?'),
                    durationMs = tx.timing.totalDurationMs,
                    isFailure = tx.isFailure,
                    isSlow = tx.timing.totalDurationMs >= slowThresholdMs
                )
            }

        val grouped = completed.groupBy { tx ->
            tx.request.method.uppercase() to tx.request.path.substringBefore('?')
        }

        val breakdown = grouped.map { (key, groupTxs) ->
            val (method, path) = key
            val durations = groupTxs.map { it.timing.totalDurationMs }.sorted()
            val failures = groupTxs.count { it.isFailure }
            val avgPayload = groupTxs.sumOf { it.request.sizeBytes + (it.response?.sizeBytes ?: 0L) } / groupTxs.size
            EndpointPerfStats(
                method = method,
                path = path,
                callCount = groupTxs.size,
                failureCount = failures,
                errorRatePercent = (failures.toFloat() / groupTxs.size.toFloat()) * 100f,
                avgDurationMs = durations.sum() / durations.size,
                p95DurationMs = percentile(durations, 95),
                maxDurationMs = durations.last(),
                avgPayloadBytes = avgPayload
            )
        }.let { list ->
            when (sortMode) {
                EndpointSortMode.SLOWEST -> list.sortedByDescending { it.p95DurationMs }
                EndpointSortMode.MOST_FREQUENT -> list.sortedByDescending { it.callCount }
                EndpointSortMode.HIGHEST_ERROR -> list.sortedWith(
                    compareByDescending<EndpointPerfStats> { it.errorRatePercent }
                        .thenByDescending { it.failureCount }
                )
            }
        }

        return NetworkPerfSummary(
            totalCompletedCalls = completed.size,
            p50Ms = p50,
            p90Ms = p90,
            p95Ms = p95,
            maxMs = maxMs,
            avgTtfbMs = avgTtfb,
            totalBytesTransferred = totalBytes,
            connectionReusePercent = reusePercent,
            slowCallCount = slowCount,
            recentSamples = recentSamples,
            endpointBreakdown = breakdown
        )
    }

    internal fun percentile(sortedValues: List<Long>, p: Int): Long {
        if (sortedValues.isEmpty()) return 0L
        val rank = ceil((p / 100.0) * sortedValues.size).toInt().coerceIn(1, sortedValues.size)
        return sortedValues[rank - 1]
    }
}
