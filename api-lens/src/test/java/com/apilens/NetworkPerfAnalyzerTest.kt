package com.apilens

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.apilens.core.ApiErrorType
import com.apilens.core.ApiRequest
import com.apilens.core.ApiResponse
import com.apilens.core.ApiTiming
import com.apilens.core.ApiTransaction
import com.apilens.performance.EndpointSortMode
import com.apilens.performance.NetworkPerfAnalyzer
import com.apilens.performance.PerformanceTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NetworkPerfAnalyzerTest {

    @Test
    fun `empty and pending-only transactions return zeroed summary`() {
        val pendingTx = buildTx(
            id = "pending",
            method = "GET",
            path = "/api/ping",
            durationMs = 500L,
            isPending = true
        )
        val summary = NetworkPerfAnalyzer.analyze(
            transactions = listOf(pendingTx),
            slowThresholdMs = 1000L
        )
        assertEquals(0, summary.totalCompletedCalls)
        assertEquals(0L, summary.p50Ms)
        assertEquals(0L, summary.p95Ms)
        assertNull(summary.avgTtfbMs)
        assertNull(summary.connectionReusePercent)
        assertTrue(summary.endpointBreakdown.isEmpty())
    }

    @Test
    fun `computes percentiles TTFB connection reuse and endpoint bottlenecks accurately`() {
        val txs = listOf(
            buildTx(
                id = "1",
                method = "GET",
                path = "/api/products?page=1",
                durationMs = 100L,
                ttfbMs = 40L,
                dnsMs = 10L,
                tcpMs = 15L,
                reqBytes = 100L,
                respBytes = 900L,
                statusCode = 200
            ),
            buildTx(
                id = "2",
                method = "GET",
                path = "/api/products?page=2",
                durationMs = 200L,
                ttfbMs = 80L,
                dnsMs = null,
                tcpMs = null,
                reqBytes = 120L,
                respBytes = 880L,
                statusCode = 200
            ),
            buildTx(
                id = "3",
                method = "POST",
                path = "/api/orders",
                durationMs = 1200L,
                ttfbMs = null,
                dnsMs = null,
                tcpMs = null,
                hasDetailedTiming = true,
                reqBytes = 300L,
                respBytes = 200L,
                statusCode = 500
            ),
            buildTx(
                id = "4",
                method = "GET",
                path = "/api/analytics",
                durationMs = 1500L,
                ttfbMs = 900L,
                dnsMs = null,
                tcpMs = null,
                hasDetailedTiming = true,
                reqBytes = 80L,
                respBytes = 420L,
                statusCode = 200
            )
        )

        val slowestSummary = NetworkPerfAnalyzer.analyze(
            transactions = txs,
            slowThresholdMs = 1000L,
            sortMode = EndpointSortMode.SLOWEST
        )

        assertEquals(4, slowestSummary.totalCompletedCalls)
        assertEquals(200L, slowestSummary.p50Ms)
        assertEquals(1500L, slowestSummary.p90Ms)
        assertEquals(1500L, slowestSummary.p95Ms)
        assertEquals(1500L, slowestSummary.maxMs)
        // Only non-null TTFBs: (40 + 80 + 900) / 3 = 340
        assertEquals(340L, slowestSummary.avgTtfbMs)
        // Reused among detailedTiming (4 calls): ids 2, 3, 4 have null dns & tcp => 3/4 = 75%
        assertEquals(75, slowestSummary.connectionReusePercent)
        assertEquals(2, slowestSummary.slowCallCount)
        assertEquals(3000L, slowestSummary.totalBytesTransferred)
        assertEquals("/api/analytics", slowestSummary.endpointBreakdown.first().path)

        val frequentSummary = NetworkPerfAnalyzer.analyze(
            transactions = txs,
            slowThresholdMs = 1000L,
            sortMode = EndpointSortMode.MOST_FREQUENT
        )
        assertEquals("/api/products", frequentSummary.endpointBreakdown.first().path)
        assertEquals(2, frequentSummary.endpointBreakdown.first().callCount)

        val errorSummary = NetworkPerfAnalyzer.analyze(
            transactions = txs,
            slowThresholdMs = 1000L,
            sortMode = EndpointSortMode.HIGHEST_ERROR
        )
        assertEquals("/api/orders", errorSummary.endpointBreakdown.first().path)
        assertEquals(100f, errorSummary.endpointBreakdown.first().errorRatePercent, 0.01f)
    }

    @Test
    fun `performance tracker records real JVM heap and active threads safely`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PerformanceTracker.initialize(context, isEnabled = true)
        PerformanceTracker.refreshNow()

        val snap = PerformanceTracker.snapshot.value
        assertTrue(snap.timestampMs > 0L)
        assertTrue(snap.maxHeapMb > 0f)
        assertTrue(snap.usedHeapMb >= 0f)
        assertTrue(snap.activeThreadCount >= 1)
    }

    private fun buildTx(
        id: String,
        method: String,
        path: String,
        durationMs: Long,
        ttfbMs: Long? = null,
        dnsMs: Long? = null,
        tcpMs: Long? = null,
        hasDetailedTiming: Boolean = true,
        reqBytes: Long = 0L,
        respBytes: Long = 0L,
        statusCode: Int = 200,
        isPending: Boolean = false
    ): ApiTransaction {
        val cleanPath = path.substringBefore('?')
        val req = ApiRequest(
            id = id,
            url = "https://example.com$path",
            scheme = "https",
            host = "example.com",
            path = cleanPath,
            queryParams = emptyList(),
            method = method,
            headers = emptyList(),
            body = null,
            contentType = null,
            sizeBytes = reqBytes,
            timestampMs = 1000L + durationMs
        )
        val resp = if (isPending) null else ApiResponse(
            statusCode = statusCode,
            message = if (statusCode in 200..299) "OK" else "Error",
            headers = emptyList(),
            body = null,
            contentType = "application/json",
            sizeBytes = respBytes,
            timestampMs = 1000L + durationMs
        )
        return ApiTransaction(
            id = id,
            request = req,
            response = resp,
            timing = ApiTiming(
                dnsLookupMs = dnsMs,
                tcpConnectMs = tcpMs,
                serverResponseMs = ttfbMs,
                totalDurationMs = durationMs,
                hasDetailedTiming = hasDetailedTiming
            ),
            errorType = if (statusCode >= 400) ApiErrorType.HTTP_ERROR else ApiErrorType.NONE,
            isPending = isPending
        )
    }
}
