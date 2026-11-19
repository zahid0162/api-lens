package com.apilens.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.apilens.ApiLens
import com.apilens.ApiLensConfig
import com.apilens.ApiLensTheme
import com.apilens.core.ApiTransaction
import com.apilens.data.ApiRepository
import com.apilens.utils.RequestFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class ApiFilter(val label: String) {
    ALL("All"),
    SUCCESS("Success"),
    FAILED("Failed"),
    SLOW("Slow"),
    GET("GET"),
    POST("POST"),
    PUT("PUT"),
    PATCH("PATCH"),
    DELETE("DELETE")
}

enum class ApiSortOrder(val label: String) {
    LATEST("Latest"),
    OLDEST("Oldest"),
    SLOWEST("Slowest"),
    FASTEST("Fastest")
}

data class DashboardMetrics(
    val totalRequests: Int = 0,
    val successRequests: Int = 0,
    val failedRequests: Int = 0,
    val slowRequests: Int = 0,
    val averageResponseMs: Long = 0L
)

enum class DashboardSection(val label: String) {
    TRAFFIC("Traffic"),
    PERFORMANCE("Performance")
}

data class ApiDashboardUiState(
    val allTransactions: List<ApiTransaction> = emptyList(),
    val filteredTransactions: List<ApiTransaction> = emptyList(),
    val metrics: DashboardMetrics = DashboardMetrics(),
    val searchQuery: String = "",
    val selectedFilter: ApiFilter = ApiFilter.ALL,
    val selectedSort: ApiSortOrder = ApiSortOrder.LATEST,
    val selectedTransactionId: String? = null,
    val compareIds: List<String> = emptyList(),
    val activeSection: DashboardSection = DashboardSection.TRAFFIC,
    val config: ApiLensConfig = ApiLensConfig()
)

class ApiDashboardViewModel(
    private val repository: ApiRepository,
    private val configFlow: StateFlow<ApiLensConfig>,
    private val onUpdateConfig: (ApiLensConfig) -> Unit
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val selectedFilter = MutableStateFlow(ApiFilter.ALL)
    private val selectedSort = MutableStateFlow(ApiSortOrder.LATEST)
    private val selectedTransactionId = MutableStateFlow<String?>(null)
    private val compareIds = MutableStateFlow<List<String>>(emptyList())
    private val activeSection = MutableStateFlow(DashboardSection.TRAFFIC)

    private data class Controls(
        val query: String,
        val filter: ApiFilter,
        val sort: ApiSortOrder,
        val selectedId: String?,
        val compareIds: List<String>,
        val section: DashboardSection
    )

    private val controlsFlow = combine(
        searchQuery,
        selectedFilter,
        selectedSort,
        selectedTransactionId,
        compareIds,
        activeSection
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        Controls(
            query = values[0] as String,
            filter = values[1] as ApiFilter,
            sort = values[2] as ApiSortOrder,
            selectedId = values[3] as String?,
            compareIds = values[4] as List<String>,
            section = values[5] as DashboardSection
        )
    }

    val uiState: StateFlow<ApiDashboardUiState> = combine(
        repository.transactions,
        configFlow,
        controlsFlow
    ) { transactions, config, controls ->
        val completed = transactions.filter { !it.isPending }
        val successCount = completed.count { it.isSuccess }
        val failedCount = completed.count { it.isFailure }
        val slowCount = completed.count { it.timing.totalDurationMs >= config.slowRequestThresholdMs }
        val avgMs = if (completed.isEmpty()) 0L else {
            completed.sumOf { it.timing.totalDurationMs } / completed.size
        }

        val filtered = transactions
            .filter { tx -> matchesFilter(tx, controls.filter, config.slowRequestThresholdMs) }
            .filter { tx -> matchesQuery(tx, controls.query) }
            .let { list -> sortTransactions(list, controls.sort) }

        ApiDashboardUiState(
            allTransactions = transactions,
            filteredTransactions = filtered,
            metrics = DashboardMetrics(
                totalRequests = transactions.size,
                successRequests = successCount,
                failedRequests = failedCount,
                slowRequests = slowCount,
                averageResponseMs = avgMs
            ),
            searchQuery = controls.query,
            selectedFilter = controls.filter,
            selectedSort = controls.sort,
            selectedTransactionId = controls.selectedId,
            compareIds = controls.compareIds,
            activeSection = controls.section,
            config = config
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ApiDashboardUiState()
    )

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun onFilterSelected(filter: ApiFilter) {
        selectedFilter.value = filter
    }

    fun onSortSelected(sort: ApiSortOrder) {
        selectedSort.value = sort
    }

    fun selectTransaction(id: String?) {
        selectedTransactionId.value = id
    }

    fun toggleCompareSelection(id: String) {
        val current = compareIds.value.toMutableList()
        if (id in current) {
            current.remove(id)
        } else {
            if (current.size >= 2) {
                current.removeAt(0)
            }
            current.add(id)
        }
        compareIds.value = current
    }

    fun clearCompareSelection() {
        compareIds.value = emptyList()
    }

    fun clearHistory() {
        selectedTransactionId.value = null
        compareIds.value = emptyList()
        repository.clearAllAsync()
    }

    fun setActiveSection(section: DashboardSection) {
        activeSection.value = section
    }

    fun inspectEndpointTraffic(endpointPath: String) {
        searchQuery.value = endpointPath
        selectedFilter.value = ApiFilter.ALL
        activeSection.value = DashboardSection.TRAFFIC
    }

    fun setTheme(theme: ApiLensTheme) {
        onUpdateConfig(configFlow.value.copy(theme = theme))
    }

    fun setFloatingButtonVisible(visible: Boolean) {
        onUpdateConfig(configFlow.value.copy(showFloatingButton = visible))
    }

    fun setBodyInspectionEnabled(enabled: Boolean) {
        onUpdateConfig(configFlow.value.copy(enableBodyInspection = enabled))
    }

    fun setPerformanceInspectorEnabled(enabled: Boolean) {
        onUpdateConfig(configFlow.value.copy(enablePerformanceInspector = enabled))
    }

    fun setSlowThresholdMs(thresholdMs: Long) {
        onUpdateConfig(configFlow.value.copy(slowRequestThresholdMs = thresholdMs.coerceAtLeast(100L)))
    }

    fun buildExportJson(): String {
        return RequestFormatter.exportTransactionsToJson(uiState.value.filteredTransactions)
    }

    private fun matchesFilter(
        tx: ApiTransaction,
        filter: ApiFilter,
        slowThresholdMs: Long
    ): Boolean {
        return when (filter) {
            ApiFilter.ALL -> true
            ApiFilter.SUCCESS -> tx.isSuccess
            ApiFilter.FAILED -> tx.isFailure
            ApiFilter.SLOW -> !tx.isPending && tx.timing.totalDurationMs >= slowThresholdMs
            ApiFilter.GET -> tx.request.method.equals("GET", ignoreCase = true)
            ApiFilter.POST -> tx.request.method.equals("POST", ignoreCase = true)
            ApiFilter.PUT -> tx.request.method.equals("PUT", ignoreCase = true)
            ApiFilter.PATCH -> tx.request.method.equals("PATCH", ignoreCase = true)
            ApiFilter.DELETE -> tx.request.method.equals("DELETE", ignoreCase = true)
        }
    }

    private fun matchesQuery(tx: ApiTransaction, query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim()
        return tx.request.url.contains(q, ignoreCase = true) ||
            tx.request.path.contains(q, ignoreCase = true) ||
            tx.request.host.contains(q, ignoreCase = true) ||
            tx.request.method.contains(q, ignoreCase = true) ||
            tx.response?.statusCode?.toString()?.contains(q) == true ||
            tx.errorType.label.contains(q, ignoreCase = true)
    }

    private fun sortTransactions(
        list: List<ApiTransaction>,
        sortOrder: ApiSortOrder
    ): List<ApiTransaction> {
        return when (sortOrder) {
            ApiSortOrder.LATEST -> list.sortedByDescending { it.request.timestampMs }
            ApiSortOrder.OLDEST -> list.sortedBy { it.request.timestampMs }
            ApiSortOrder.SLOWEST -> list.sortedByDescending { it.timing.totalDurationMs }
            ApiSortOrder.FASTEST -> list.sortedBy { it.timing.totalDurationMs }
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ApiDashboardViewModel(
                repository = ApiLens.requireRepository(),
                configFlow = ApiLens.configFlow(),
                onUpdateConfig = { ApiLens.updateConfig(it) }
            ) as T
        }
    }
}
