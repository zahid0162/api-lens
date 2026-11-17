package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.network.LoginPayload
import com.example.network.OrderPayload
import com.example.network.PreferencesPayload
import com.example.network.ProfilePayload
import com.example.network.SampleNetworkClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SampleCallLog(
    val title: String,
    val outcome: String,
    val isError: Boolean
)

data class SampleUiState(
    val serverBaseUrl: String = "",
    val isLoading: Boolean = false,
    val lastCallTitle: String = "Ready",
    val lastCallOutcome: String = "Tap any endpoint below to execute a real Retrofit + OkHttp call and inspect it via the floating API Lens button.",
    val lastCallError: Boolean = false,
    val callHistory: List<SampleCallLog> = emptyList()
)

class SamplePlaygroundViewModel(application: Application) : AndroidViewModel(application) {

    private val sampleApp = application as SampleApplication
    private val baseUrl: String get() = sampleApp.demoServer.baseUrl

    private val catalogService by lazy { SampleNetworkClient.createCatalogService(baseUrl) }
    private val accountOrdersService by lazy { SampleNetworkClient.createAccountOrdersService(baseUrl) }

    private val _uiState = MutableStateFlow(SampleUiState(serverBaseUrl = baseUrl))
    val uiState: StateFlow<SampleUiState> = _uiState.asStateFlow()

    fun fetchProducts() = executeCall("GET /api/products") {
        val resp = catalogService.getProducts()
        "HTTP ${resp.code()} (${resp.body()?.string()?.take(90) ?: "empty"})"
    }

    fun loginWithCredentials() = executeCall("POST /api/login") {
        val resp = accountOrdersService.login(
            payload = LoginPayload(
                email = "engineer@apilens.dev",
                password = "SuperSecretPassword123!"
            )
        )
        "HTTP ${resp.code()} — Auth token & password redacted automatically"
    }

    fun fetchCustomersUnauthorized() = executeCall("GET /api/customers") {
        val resp = accountOrdersService.getCustomers()
        "HTTP ${resp.code()} ${resp.message()}"
    }

    fun submitOrderError() = executeCall("POST /api/orders") {
        val resp = accountOrdersService.createOrder(OrderPayload(sku = "prd_01", quantity = 2))
        "HTTP ${resp.code()} ${resp.message()}"
    }

    fun updateProfile() = executeCall("PUT /api/profile") {
        val resp = accountOrdersService.updateProfile(
            ProfilePayload(displayName = "Staff Mobile Architect", role = "ANDROID")
        )
        "HTTP ${resp.code()} OK"
    }

    fun patchPreferences() = executeCall("PATCH /api/preferences") {
        val resp = accountOrdersService.patchPreferences(
            PreferencesPayload(theme = "obsidian-dark", compactDensity = true)
        )
        "HTTP ${resp.code()} OK"
    }

    fun deleteSession() = executeCall("DELETE /api/session") {
        val resp = accountOrdersService.logoutSession()
        "HTTP ${resp.code()} No Content"
    }

    fun fetchSlowAnalytics() = executeCall("GET /api/analytics/slow") {
        val resp = catalogService.getSlowAnalytics()
        "HTTP ${resp.code()} — Flagged as Slow API (>900ms)"
    }

    fun triggerSocketTimeout() = executeCall("GET /api/timeout") {
        val resp = catalogService.triggerTimeout()
        "HTTP ${resp.code()}"
    }

    fun triggerUnknownHost() = executeCall("GET invalid-host.apilens-test.invalid") {
        val resp = catalogService.callExternalUrl("https://invalid-host.apilens-test.invalid/ping")
        "HTTP ${resp.code()}"
    }

    fun triggerConnectionFailure() = executeCall("GET 127.0.0.1:59991/closed") {
        val resp = catalogService.callExternalUrl("http://127.0.0.1:59991/closed")
        "HTTP ${resp.code()}"
    }

    fun runBurstSuite() {
        viewModelScope.launch {
            fetchProducts()
            loginWithCredentials()
            fetchCustomersUnauthorized()
            submitOrderError()
        }
    }

    private fun executeCall(title: String, block: suspend () -> String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                lastCallTitle = title,
                lastCallOutcome = "Executing Retrofit call..."
            )
            val result = runCatching { block() }
            val outcome = result.getOrElse { err ->
                "${err::class.java.simpleName}: ${err.message ?: "Request failed"}"
            }
            val isErr = result.isFailure || outcome.startsWith("HTTP 4") || outcome.startsWith("HTTP 5")
            val log = SampleCallLog(title = title, outcome = outcome, isError = isErr)
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                lastCallTitle = title,
                lastCallOutcome = outcome,
                lastCallError = isErr,
                callHistory = (listOf(log) + _uiState.value.callHistory).take(8)
            )
        }
    }
}
