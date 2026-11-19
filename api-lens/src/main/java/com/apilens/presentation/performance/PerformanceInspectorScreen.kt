package com.apilens.presentation.performance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PauseCircleOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apilens.performance.EndpointPerfStats
import com.apilens.performance.EndpointSortMode
import com.apilens.performance.LatencySample
import com.apilens.performance.NetworkPerfSummary
import com.apilens.performance.RuntimePerfSnapshot
import com.apilens.presentation.components.HttpMethodBadge
import com.apilens.presentation.theme.JetBrainsMonoFontFamily
import com.apilens.presentation.theme.LocalApiLensColors
import com.apilens.utils.RequestFormatter
import java.util.Locale

@Composable
fun PerformanceInspectorScreen(
    onEndpointClick: (String) -> Unit,
    onEnableInspector: () -> Unit,
    modifier: Modifier = Modifier,
    vm: PerformanceInspectorViewModel = viewModel(factory = PerformanceInspectorViewModel.Factory())
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val colors = LocalApiLensColors.current

    LaunchedEffect(Unit) {
        vm.refreshRuntimeSnapshot()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("performance_inspector_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!state.isInspectorEnabled) {
            item {
                PausedInspectorBanner(onEnableInspector = onEnableInspector)
            }
        }

        item {
            RuntimeHealthSection(
                runtime = state.runtime,
                onForceGc = vm::forceGc,
                onResetMetrics = vm::resetRuntimeMetrics
            )
        }

        item {
            NetworkLatencySection(
                network = state.network,
                slowThresholdMs = state.slowThresholdMs
            )
        }

        item {
            EndpointLeaderboardHeader(
                selectedMode = state.sortMode,
                endpointCount = state.network.endpointBreakdown.size,
                onSelectMode = vm::setSortMode
            )
        }

        if (state.network.endpointBreakdown.isEmpty()) {
            item {
                EmptyEndpointPerformanceCard()
            }
        } else {
            items(
                items = state.network.endpointBreakdown,
                key = { "${it.method}_${it.path}" }
            ) { endpoint ->
                EndpointPerfCard(
                    endpoint = endpoint,
                    slowThresholdMs = state.slowThresholdMs,
                    onClick = { onEndpointClick(endpoint.path) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PausedInspectorBanner(onEnableInspector: () -> Unit) {
    val colors = LocalApiLensColors.current
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.slowWarning.copy(alpha = 0.12f)),
        border = BorderStroke(1.dp, colors.slowWarning.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("perf_paused_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.PauseCircleOutline,
                contentDescription = "Performance telemetry paused",
                tint = colors.slowWarning,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Runtime FPS & Heap Tracking Paused",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary
                )
                Text(
                    text = "Enable live Choreographer & memory sampling in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }
            Button(
                onClick = onEnableInspector,
                colors = ButtonDefaults.buttonColors(containerColor = colors.slowWarning),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("enable_perf_inspector_button")
            ) {
                Text("Resume", color = colors.canvas, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun RuntimeHealthSection(
    runtime: RuntimePerfSnapshot,
    onForceGc: () -> Unit,
    onResetMetrics: () -> Unit
) {
    val colors = LocalApiLensColors.current
    val fpsColor = when {
        runtime.fps <= 0f -> colors.textMuted
        runtime.fps >= 55f -> colors.statusSuccess
        runtime.fps >= 40f -> colors.slowWarning
        else -> colors.statusError
    }
    val heapRatio = (runtime.usedHeapMb / runtime.maxHeapMb.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val heapColor = when {
        heapRatio >= 0.85f -> colors.statusError
        heapRatio >= 0.65f -> colors.slowWarning
        else -> colors.accentCyan
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // FPS & Frame Stability Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
            border = BorderStroke(1.dp, colors.borderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("runtime_fps_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = "UI Frame Rate",
                            tint = fpsColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "UI Render Health",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary
                        )
                    }
                    Text(
                        text = if (runtime.fps > 0f) String.format(Locale.US, "%.0f FPS", runtime.fps) else "-- FPS",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = fpsColor,
                        modifier = Modifier.testTag("runtime_fps_value")
                    )
                }

                FpsSparklineChart(
                    samples = runtime.fpsHistory,
                    lineColor = fpsColor,
                    guideColor = colors.statusSuccess.copy(alpha = 0.4f),
                    trackColor = colors.surfaceElevated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MiniStatChip(
                        label = "Avg Frame",
                        value = String.format(Locale.US, "%.1fms", runtime.avgFrameTimeMs),
                        accent = colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    MiniStatChip(
                        label = "Worst",
                        value = String.format(Locale.US, "%.0fms", runtime.worstFrameTimeMs),
                        accent = if (runtime.worstFrameTimeMs > 32f) colors.slowWarning else colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    MiniStatChip(
                        label = "Jank",
                        value = runtime.jankyFrameCount.toString(),
                        accent = if (runtime.jankyFrameCount > 0) colors.slowWarning else colors.statusSuccess,
                        modifier = Modifier.weight(1f)
                    )
                    MiniStatChip(
                        label = "Frozen",
                        value = runtime.frozenFrameCount.toString(),
                        accent = if (runtime.frozenFrameCount > 0) colors.statusError else colors.statusSuccess,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Memory & Thread Telemetry Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
            border = BorderStroke(1.dp, colors.borderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("runtime_memory_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Memory,
                            contentDescription = "Memory and Threads",
                            tint = heapColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "Memory & Threads",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary
                            )
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "JVM %.1f / %.0f MB • Native %.1f MB",
                                    runtime.usedHeapMb,
                                    runtime.maxHeapMb,
                                    runtime.nativeHeapMb
                                ),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = colors.textSecondary
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = onForceGc,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            border = BorderStroke(1.dp, colors.borderSubtle),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("perf_force_gc_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CleaningServices,
                                contentDescription = "Trigger garbage collection",
                                tint = colors.accentCyan,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("GC", fontSize = 12.sp, color = colors.accentCyan)
                        }
                        OutlinedButton(
                            onClick = onResetMetrics,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            border = BorderStroke(1.dp, colors.borderSubtle),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("perf_reset_metrics_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Reset frame metrics",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset", fontSize = 12.sp, color = colors.textSecondary)
                        }
                    }
                }

                LinearProgressIndicator(
                    progress = { heapRatio },
                    color = heapColor,
                    trackColor = colors.surfaceElevated,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )

                if (runtime.heapHistoryMb.size >= 2) {
                    HeapTrendChart(
                        samples = runtime.heapHistoryMb,
                        maxHeapMb = runtime.maxHeapMb,
                        lineColor = heapColor,
                        trackColor = colors.surfaceElevated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MiniStatChip(
                        label = "Heap %",
                        value = "${(heapRatio * 100f).toInt()}%",
                        accent = heapColor,
                        modifier = Modifier.weight(1f)
                    )
                    MiniStatChip(
                        label = "Threads",
                        value = runtime.activeThreadCount.toString(),
                        accent = colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    MiniStatChip(
                        label = "Avail RAM",
                        value = if (runtime.systemAvailRamMb > 0L) "${runtime.systemAvailRamMb} MB" else "N/A",
                        accent = if (runtime.isLowMemory) colors.statusError else colors.statusSuccess,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkLatencySection(
    network: NetworkPerfSummary,
    slowThresholdMs: Long
) {
    val colors = LocalApiLensColors.current
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
        border = BorderStroke(1.dp, colors.borderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("network_perf_summary_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NetworkCheck,
                        contentDescription = "Network Latency Percentiles",
                        tint = colors.accentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Network Latency Percentiles",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary
                    )
                }
                Text(
                    text = "SLA ${slowThresholdMs}ms",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    color = colors.slowWarning
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniStatChip(
                    label = "P50",
                    value = "${network.p50Ms}ms",
                    accent = colors.accentCyan,
                    modifier = Modifier.weight(1f).testTag("perf_p50_value")
                )
                MiniStatChip(
                    label = "P90",
                    value = "${network.p90Ms}ms",
                    accent = if (network.p90Ms >= slowThresholdMs) colors.slowWarning else colors.textPrimary,
                    modifier = Modifier.weight(1f).testTag("perf_p90_value")
                )
                MiniStatChip(
                    label = "P95",
                    value = "${network.p95Ms}ms",
                    accent = if (network.p95Ms >= slowThresholdMs) colors.statusError else colors.statusSuccess,
                    modifier = Modifier.weight(1f).testTag("perf_p95_value")
                )
                MiniStatChip(
                    label = "Max",
                    value = "${network.maxMs}ms",
                    accent = if (network.maxMs >= slowThresholdMs) colors.statusError else colors.textPrimary,
                    modifier = Modifier.weight(1f).testTag("perf_max_value")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MiniStatChip(
                    label = "Avg TTFB",
                    value = network.avgTtfbMs?.let { "${it}ms" } ?: "N/A",
                    accent = colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                MiniStatChip(
                    label = "Conn Reuse",
                    value = network.connectionReusePercent?.let { "$it%" } ?: "N/A",
                    accent = colors.statusSuccess,
                    modifier = Modifier.weight(1f)
                )
                MiniStatChip(
                    label = "Slow Calls",
                    value = network.slowCallCount.toString(),
                    accent = if (network.slowCallCount > 0) colors.slowWarning else colors.statusSuccess,
                    modifier = Modifier.weight(1f)
                )
                MiniStatChip(
                    label = "Traffic",
                    value = RequestFormatter.formatBytes(network.totalBytesTransferred),
                    accent = colors.accentCyan,
                    modifier = Modifier.weight(1f)
                )
            }

            if (network.recentSamples.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Recent ${network.recentSamples.size} Calls vs ${slowThresholdMs}ms SLA",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary
                    )
                    LatencyBarChart(
                        samples = network.recentSamples,
                        slowThresholdMs = slowThresholdMs,
                        normalColor = colors.accentCyan,
                        slowColor = colors.slowWarning,
                        errorColor = colors.statusError,
                        trackColor = colors.surfaceElevated,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(84.dp)
                    )
                }
            } else {
                Text(
                    text = "Trigger API calls to plot latency bars against your SLA threshold.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
            }
        }
    }
}

@Composable
private fun EndpointLeaderboardHeader(
    selectedMode: EndpointSortMode,
    endpointCount: Int,
    onSelectMode: (EndpointSortMode) -> Unit
) {
    val colors = LocalApiLensColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Endpoint Bottlenecks ($endpointCount)",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary
            )
            Text(
                text = "Tap to filter traffic",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EndpointSortMode.entries.forEach { mode ->
                FilterChip(
                    selected = selectedMode == mode,
                    onClick = { onSelectMode(mode) },
                    label = { Text(mode.label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colors.accentCyan.copy(alpha = 0.2f),
                        selectedLabelColor = colors.accentCyan,
                        containerColor = colors.surfaceCard,
                        labelColor = colors.textSecondary
                    ),
                    modifier = Modifier.testTag("perf_sort_${mode.name.lowercase()}")
                )
            }
        }
    }
}

@Composable
private fun EndpointPerfCard(
    endpoint: EndpointPerfStats,
    slowThresholdMs: Long,
    onClick: () -> Unit
) {
    val colors = LocalApiLensColors.current
    val isSlowEndpoint = endpoint.p95DurationMs >= slowThresholdMs
    val hasErrors = endpoint.failureCount > 0

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
        border = BorderStroke(
            1.dp,
            when {
                hasErrors -> colors.statusError.copy(alpha = 0.4f)
                isSlowEndpoint -> colors.slowWarning.copy(alpha = 0.45f)
                else -> colors.borderSubtle
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .testTag("perf_endpoint_card_${endpoint.method}_${endpoint.path}")
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HttpMethodBadge(method = endpoint.method)
                Text(
                    text = endpoint.path,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (isSlowEndpoint) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = "Exceeds slow threshold",
                        tint = colors.slowWarning,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = "P95 ${endpoint.p95DurationMs}ms",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = when {
                        isSlowEndpoint -> colors.slowWarning
                        else -> colors.accentCyan
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${endpoint.callCount} calls • Avg ${endpoint.avgDurationMs}ms • Max ${endpoint.maxDurationMs}ms",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    color = colors.textSecondary
                )
                Text(
                    text = if (hasErrors) {
                        String.format(Locale.US, "%.0f%% err", endpoint.errorRatePercent)
                    } else {
                        RequestFormatter.formatBytes(endpoint.avgPayloadBytes)
                    },
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = if (hasErrors) colors.statusError else colors.textMuted
                )
            }
        }
    }
}

@Composable
private fun EmptyEndpointPerformanceCard() {
    val colors = LocalApiLensColors.current
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
        border = BorderStroke(1.dp, colors.borderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "No completed API calls yet",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary
            )
            Text(
                text = "Per-endpoint P95 latency, error rate, and payload averages appear once requests complete.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MiniStatChip(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalApiLensColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceElevated)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 11.sp,
            color = colors.textMuted,
            maxLines = 1
        )
        Text(
            text = value,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FpsSparklineChart(
    samples: List<Float>,
    lineColor: Color,
    guideColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(trackColor)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxFps = maxOf(65f, samples.maxOrNull() ?: 60f)
            val target60Y = size.height * (1f - (60f / maxFps).coerceIn(0f, 1f))
            drawLine(
                color = guideColor,
                start = Offset(0f, target60Y),
                end = Offset(size.width, target60Y),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
            )

            if (samples.isEmpty()) return@Canvas
            if (samples.size == 1) {
                val y = size.height * (1f - (samples[0] / maxFps).coerceIn(0f, 1f))
                drawCircle(color = lineColor, radius = 5f, center = Offset(size.width / 2f, y))
                return@Canvas
            }

            val linePath = Path()
            val fillPath = Path()
            val stepX = size.width / (samples.size - 1).coerceAtLeast(1)

            samples.forEachIndexed { index, fps ->
                val x = index * stepX
                val y = size.height * (1f - (fps / maxFps).coerceIn(0f, 1f))
                if (index == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, size.height)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo((samples.size - 1) * stepX, size.height)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0.02f))
                )
            )
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun HeapTrendChart(
    samples: List<Float>,
    maxHeapMb: Float,
    lineColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(trackColor)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (samples.size < 2) return@Canvas
            val maxVal = maxOf(maxHeapMb * 0.5f, samples.maxOrNull() ?: 1f).coerceAtLeast(1f)
            val path = Path()
            val stepX = size.width / (samples.size - 1).coerceAtLeast(1)
            samples.forEachIndexed { index, mb ->
                val x = index * stepX
                val y = size.height * (1f - (mb / maxVal).coerceIn(0f, 1f))
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 3.5f, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun LatencyBarChart(
    samples: List<LatencySample>,
    slowThresholdMs: Long,
    normalColor: Color,
    slowColor: Color,
    errorColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(trackColor)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (samples.isEmpty()) return@Canvas
            val peakMs = maxOf(
                slowThresholdMs.toFloat() * 1.15f,
                samples.maxOf { it.durationMs }.toFloat()
            ).coerceAtLeast(1f)

            val slaY = size.height * (1f - (slowThresholdMs.toFloat() / peakMs).coerceIn(0f, 1f))
            drawLine(
                color = slowColor.copy(alpha = 0.7f),
                start = Offset(0f, slaY),
                end = Offset(size.width, slaY),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
            )

            val slotWidth = size.width / samples.size.coerceAtLeast(1)
            val barWidth = (slotWidth * 0.68f).coerceAtLeast(4f).coerceAtMost(24f)
            samples.forEachIndexed { index, sample ->
                val barHeight = (size.height * (sample.durationMs.toFloat() / peakMs)).coerceAtLeast(4f)
                val left = index * slotWidth + (slotWidth - barWidth) / 2f
                val top = size.height - barHeight
                val barColor = when {
                    sample.isFailure -> errorColor
                    sample.isSlow -> slowColor
                    else -> normalColor
                }
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(4f, 4f)
                )
            }
        }
    }
}
