package com.apilens

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import com.apilens.core.ApiEventListener
import com.apilens.core.ApiInterceptor
import com.apilens.data.ApiRepository
import com.apilens.performance.PerformanceTracker
import com.apilens.presentation.dashboard.ApiLensDashboardActivity
import com.apilens.presentation.overlay.DebugOverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * Primary public entry point for integrating **API Lens** into an Android application.
 *
 * Example usage:
 * ```kotlin
 * ApiLens.initialize(
 *     context = applicationContext,
 *     config = ApiLensConfig(
 *         showFloatingButton = true,
 *         maxRequests = 500,
 *         enableDatabase = true,
 *         enableBodyInspection = true,
 *         theme = ApiLensTheme.DARK
 *     )
 * )
 *
 * val client = OkHttpClient.Builder()
 *     .addInterceptor(ApiLens.interceptor())
 *     .eventListenerFactory(ApiLens.eventListenerFactory())
 *     .build()
 * ```
 */
object ApiLens {

    private val internalScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val configState = MutableStateFlow(ApiLensConfig())
    private val enabledState = MutableStateFlow(true)

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var repository: ApiRepository? = null

    private var statsFlow: StateFlow<DebugOverlayManager.OverlayStats>? = null

    private val overlayManager: DebugOverlayManager by lazy {
        DebugOverlayManager(
            configFlow = configState.asStateFlow(),
            enabledFlow = enabledState.asStateFlow(),
            requestStatsProvider = { getOrCreateStatsFlow() }
        )
    }

    private val interceptorInstance: ApiInterceptor by lazy {
        ApiInterceptor(
            configProvider = { configState.value },
            isEnabledProvider = { enabledState.value },
            repositoryProvider = { repository }
        )
    }

    private val eventListenerFactoryInstance: EventListener.Factory by lazy {
        ApiEventListener.Factory()
    }

    @JvmStatic
    @JvmOverloads
    fun initialize(context: Context, config: ApiLensConfig = ApiLensConfig()) {
        val applicationContext = context.applicationContext
        appContext = applicationContext
        configState.value = config

        synchronized(this) {
            if (repository == null) {
                repository = ApiRepository(
                    context = applicationContext,
                    initialConfig = config
                )
            } else {
                repository?.updateConfig(config)
            }
        }

        val application = applicationContext as? Application
        if (application != null) {
            overlayManager.register(application)
        }
        if (context is Activity) {
            overlayManager.tryAttachToCurrentActivity(context)
        }
        PerformanceTracker.initialize(applicationContext, config.enablePerformanceInspector)
    }

    @JvmStatic
    fun interceptor(): Interceptor = interceptorInstance

    @JvmStatic
    fun eventListenerFactory(): EventListener.Factory = eventListenerFactoryInstance

    /**
     * Java-friendly convenience helper that attaches both the interceptor and event listener
     * to an [OkHttpClient.Builder].
     */
    @JvmStatic
    fun configure(builder: OkHttpClient.Builder): OkHttpClient.Builder {
        return builder
            .addInterceptor(interceptor())
            .eventListenerFactory(eventListenerFactory())
    }

    @JvmStatic
    fun showDashboard(context: Context) {
        launchDashboard(context, ApiLensDashboardActivity.SECTION_TRAFFIC)
    }

    @JvmStatic
    fun showPerformanceInspector(context: Context) {
        launchDashboard(context, ApiLensDashboardActivity.SECTION_PERFORMANCE)
    }

    private fun launchDashboard(context: Context, initialSection: String) {
        val targetContext = appContext ?: context.applicationContext
        val intent = Intent(targetContext, ApiLensDashboardActivity::class.java).apply {
            putExtra(ApiLensDashboardActivity.EXTRA_INITIAL_SECTION, initialSection)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        if (context is Activity) {
            context.startActivity(intent)
        } else {
            targetContext.startActivity(intent)
        }
    }

    @JvmStatic
    fun clearHistory() {
        repository?.clearAllAsync()
    }

    @JvmStatic
    fun disable() {
        enabledState.value = false
        PerformanceTracker.setEnabled(false)
    }

    @JvmStatic
    fun enable() {
        enabledState.value = true
        PerformanceTracker.setEnabled(configState.value.enablePerformanceInspector)
    }

    @JvmStatic
    fun isEnabled(): Boolean = enabledState.value

    @JvmStatic
    fun updateConfig(config: ApiLensConfig) {
        configState.value = config
        repository?.updateConfig(config)
        PerformanceTracker.setEnabled(enabledState.value && config.enablePerformanceInspector)
    }

    internal fun configFlow(): StateFlow<ApiLensConfig> = configState.asStateFlow()

    internal fun requireRepository(): ApiRepository {
        return repository ?: synchronized(this) {
            repository ?: ApiRepository(
                context = appContext,
                initialConfig = configState.value
            ).also { repository = it }
        }
    }

    private fun getOrCreateStatsFlow(): StateFlow<DebugOverlayManager.OverlayStats> {
        return statsFlow ?: synchronized(this) {
            statsFlow ?: requireRepository().transactions
                .map { list ->
                    DebugOverlayManager.OverlayStats(
                        totalCount = list.size,
                        failedCount = list.count { it.isFailure }
                    )
                }
                .stateIn(
                    scope = internalScope,
                    started = SharingStarted.Eagerly,
                    initialValue = DebugOverlayManager.OverlayStats()
                ).also { statsFlow = it }
        }
    }
}

/**
 * Kotlin extension attaching both API Lens's [Interceptor] and [EventListener.Factory]
 * for full request/response capture and network timing breakdown.
 */
fun OkHttpClient.Builder.attachApiLens(): OkHttpClient.Builder =
    ApiLens.configure(this)
