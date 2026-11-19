package com.apilens.performance

/**
 * Real-time Android runtime performance metrics observed from Choreographer, JVM Runtime,
 * Debug native heap, ActivityManager MemoryInfo, and active Thread count.
 */
data class RuntimePerfSnapshot(
    val timestampMs: Long = 0L,
    val fps: Float = 0f,
    val avgFrameTimeMs: Float = 0f,
    val worstFrameTimeMs: Float = 0f,
    val jankyFrameCount: Int = 0,
    val frozenFrameCount: Int = 0,
    val totalFramesObserved: Int = 0,
    val usedHeapMb: Float = 0f,
    val maxHeapMb: Float = 1f,
    val nativeHeapMb: Float = 0f,
    val systemAvailRamMb: Long = 0L,
    val isLowMemory: Boolean = false,
    val activeThreadCount: Int = 0,
    val fpsHistory: List<Float> = emptyList(),
    val heapHistoryMb: List<Float> = emptyList(),
    val isMonitoring: Boolean = false
)

/**
 * Aggregated network performance metrics grouped by HTTP method + normalized route path.
 */
data class EndpointPerfStats(
    val method: String,
    val path: String,
    val callCount: Int,
    val failureCount: Int,
    val errorRatePercent: Float,
    val avgDurationMs: Long,
    val p95DurationMs: Long,
    val maxDurationMs: Long,
    val avgPayloadBytes: Long
)

/**
 * Individual point for plotting recent network latency samples against the slow SLA threshold.
 */
data class LatencySample(
    val id: String,
    val method: String,
    val path: String,
    val durationMs: Long,
    val isFailure: Boolean,
    val isSlow: Boolean
)

/**
 * Overall network performance percentile summary across captured transactions.
 */
data class NetworkPerfSummary(
    val totalCompletedCalls: Int = 0,
    val p50Ms: Long = 0L,
    val p90Ms: Long = 0L,
    val p95Ms: Long = 0L,
    val maxMs: Long = 0L,
    val avgTtfbMs: Long? = null,
    val totalBytesTransferred: Long = 0L,
    val connectionReusePercent: Int? = null,
    val slowCallCount: Int = 0,
    val recentSamples: List<LatencySample> = emptyList(),
    val endpointBreakdown: List<EndpointPerfStats> = emptyList()
)

enum class EndpointSortMode(val label: String) {
    SLOWEST("Slowest P95"),
    MOST_FREQUENT("Most Calls"),
    HIGHEST_ERROR("Error Rate")
}
