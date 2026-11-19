package com.apilens.presentation.performance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.apilens.ApiLens
import com.apilens.ApiLensConfig
import com.apilens.data.ApiRepository
import com.apilens.performance.EndpointSortMode
import com.apilens.performance.NetworkPerfAnalyzer
import com.apilens.performance.NetworkPerfSummary
import com.apilens.performance.PerformanceTracker
import com.apilens.performance.RuntimePerfSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class PerformanceInspectorUiState(
    val runtime: RuntimePerfSnapshot = RuntimePerfSnapshot(),
    val network: NetworkPerfSummary = NetworkPerfSummary(),
    val sortMode: EndpointSortMode = EndpointSortMode.SLOWEST,
    val slowThresholdMs: Long = ApiLensConfig.DEFAULT_SLOW_THRESHOLD_MS,
    val isInspectorEnabled: Boolean = true
)

class PerformanceInspectorViewModel(
    repository: ApiRepository,
    configFlow: StateFlow<ApiLensConfig>
) : ViewModel() {

    private val sortMode = MutableStateFlow(EndpointSortMode.SLOWEST)

    val uiState: StateFlow<PerformanceInspectorUiState> = combine(
        repository.transactions,
        PerformanceTracker.snapshot,
        configFlow,
        sortMode
    ) { transactions, runtimeSnapshot, config, endpointSort ->
        val networkSummary = NetworkPerfAnalyzer.analyze(
            transactions = transactions,
            slowThresholdMs = config.slowRequestThresholdMs,
            sortMode = endpointSort
        )
        PerformanceInspectorUiState(
            runtime = runtimeSnapshot,
            network = networkSummary,
            sortMode = endpointSort,
            slowThresholdMs = config.slowRequestThresholdMs,
            isInspectorEnabled = config.enablePerformanceInspector
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PerformanceInspectorUiState(
            runtime = PerformanceTracker.snapshot.value,
            slowThresholdMs = configFlow.value.slowRequestThresholdMs,
            isInspectorEnabled = configFlow.value.enablePerformanceInspector
        )
    )

    fun setSortMode(mode: EndpointSortMode) {
        sortMode.value = mode
    }

    fun forceGc() {
        PerformanceTracker.forceGcAndRefresh()
    }

    fun resetRuntimeMetrics() {
        PerformanceTracker.resetMetrics()
    }

    fun refreshRuntimeSnapshot() {
        PerformanceTracker.refreshNow()
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PerformanceInspectorViewModel(
                repository = ApiLens.requireRepository(),
                configFlow = ApiLens.configFlow()
            ) as T
        }
    }
}
