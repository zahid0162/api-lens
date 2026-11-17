package com.apilens.presentation.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.apilens.ApiLens
import com.apilens.ApiLensConfig
import com.apilens.core.ApiReplayClient
import com.apilens.core.ApiTransaction
import com.apilens.data.ApiRepository
import com.apilens.presentation.components.JsonDisplayMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DetailsTab(val title: String) {
    OVERVIEW("Overview"),
    REQUEST("Request"),
    RESPONSE("Response"),
    TIMING("Timing")
}

sealed interface ReplayUiState {
    data object Idle : ReplayUiState
    data object Running : ReplayUiState
    data class Completed(val statusCode: Int) : ReplayUiState
    data class Failed(val message: String) : ReplayUiState
}

data class ApiDetailsUiState(
    val transaction: ApiTransaction? = null,
    val selectedTab: DetailsTab = DetailsTab.OVERVIEW,
    val requestDisplayMode: JsonDisplayMode = JsonDisplayMode.PRETTY,
    val responseDisplayMode: JsonDisplayMode = JsonDisplayMode.TREE,
    val responseSearchQuery: String = "",
    val detailSearchQuery: String = "",
    val slowThresholdMs: Long = ApiLensConfig.DEFAULT_SLOW_THRESHOLD_MS,
    val replayState: ReplayUiState = ReplayUiState.Idle
)

class ApiDetailsViewModel(
    transactionId: String,
    repository: ApiRepository,
    configFlow: StateFlow<ApiLensConfig>
) : ViewModel() {

    private val selectedTab = MutableStateFlow(DetailsTab.OVERVIEW)
    private val requestDisplayMode = MutableStateFlow(JsonDisplayMode.PRETTY)
    private val responseDisplayMode = MutableStateFlow(JsonDisplayMode.TREE)
    private val responseSearchQuery = MutableStateFlow("")
    private val detailSearchQuery = MutableStateFlow("")
    private val replayState = MutableStateFlow<ReplayUiState>(ReplayUiState.Idle)

    private data class UiControls(
        val tab: DetailsTab,
        val reqMode: JsonDisplayMode,
        val respMode: JsonDisplayMode,
        val respQuery: String,
        val detailQuery: String,
        val replay: ReplayUiState
    )

    private val controlsFlow = combine(
        selectedTab,
        requestDisplayMode,
        responseDisplayMode,
        responseSearchQuery,
        detailSearchQuery
    ) { tab, reqMode, respMode, respQuery, detailQuery ->
        UiControls(tab, reqMode, respMode, respQuery, detailQuery, ReplayUiState.Idle)
    }.combine(replayState) { base, replay ->
        base.copy(replay = replay)
    }

    val uiState: StateFlow<ApiDetailsUiState> = combine(
        repository.observeTransaction(transactionId),
        configFlow,
        controlsFlow
    ) { tx, config, controls ->
        ApiDetailsUiState(
            transaction = tx,
            selectedTab = controls.tab,
            requestDisplayMode = controls.reqMode,
            responseDisplayMode = controls.respMode,
            responseSearchQuery = controls.respQuery,
            detailSearchQuery = controls.detailQuery,
            slowThresholdMs = config.slowRequestThresholdMs,
            replayState = controls.replay
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ApiDetailsUiState()
    )

    fun selectTab(tab: DetailsTab) {
        selectedTab.value = tab
    }

    fun setRequestDisplayMode(mode: JsonDisplayMode) {
        requestDisplayMode.value = mode
    }

    fun setResponseDisplayMode(mode: JsonDisplayMode) {
        responseDisplayMode.value = mode
    }

    fun setResponseSearchQuery(query: String) {
        responseSearchQuery.value = query
    }

    fun setDetailSearchQuery(query: String) {
        detailSearchQuery.value = query
    }

    fun replayCurrentTransaction() {
        val tx = uiState.value.transaction ?: return
        if (replayState.value is ReplayUiState.Running) return
        replayState.value = ReplayUiState.Running
        viewModelScope.launch {
            val result = ApiReplayClient.replay(tx)
            replayState.value = result.fold(
                onSuccess = { code -> ReplayUiState.Completed(code) },
                onFailure = { err -> ReplayUiState.Failed(err.message ?: "Replay failed") }
            )
        }
    }

    fun dismissReplayBanner() {
        replayState.value = ReplayUiState.Idle
    }

    class Factory(private val transactionId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repo = ApiLens.requireRepository()
            return ApiDetailsViewModel(
                transactionId = transactionId,
                repository = repo,
                configFlow = ApiLens.configFlow()
            ) as T
        }
    }
}
