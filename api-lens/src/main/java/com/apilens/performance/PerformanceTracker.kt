package com.apilens.performance

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Observes real Android UI frame intervals via [Choreographer], JVM/native heap memory,
 * system RAM pressure, and active thread counts while the host application is in the foreground.
 */
internal object PerformanceTracker {

    private const val SAMPLE_INTERVAL_MS = 1000L
    private const val MAX_HISTORY_POINTS = 30
    private const val JANK_FRAME_THRESHOLD_MS = 16.67f
    private const val FROZEN_FRAME_THRESHOLD_MS = 700f

    private val _snapshot = MutableStateFlow(RuntimePerfSnapshot())
    val snapshot: StateFlow<RuntimePerfSnapshot> = _snapshot.asStateFlow()

    private val registeredApps = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Application, Boolean>())
    )
    private val isRunning = AtomicBoolean(false)

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var enabled: Boolean = true

    @Volatile
    private var startedActivityCount: Int = 0

    // Frame window accumulators (main thread)
    private var lastFrameTimeNs: Long = 0L
    private var windowFrameCount: Int = 0
    private var windowDurationNs: Long = 0L
    private var totalFramesObserved: Int = 0
    private var jankyFrameCount: Int = 0
    private var frozenFrameCount: Int = 0
    private var worstFrameTimeMs: Float = 0f

    private val fpsHistoryBuffer = ArrayDeque<Float>(MAX_HISTORY_POINTS)
    private val heapHistoryBuffer = ArrayDeque<Float>(MAX_HISTORY_POINTS)

    private val mainHandler: Handler? by lazy {
        runCatching { Handler(Looper.getMainLooper()) }.getOrNull()
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isRunning.get()) return
            if (lastFrameTimeNs != 0L) {
                val deltaNs = (frameTimeNanos - lastFrameTimeNs).coerceAtLeast(0L)
                val frameMs = deltaNs / 1_000_000f
                windowFrameCount++
                windowDurationNs += deltaNs
                totalFramesObserved++
                if (frameMs > JANK_FRAME_THRESHOLD_MS) {
                    jankyFrameCount++
                }
                if (frameMs > FROZEN_FRAME_THRESHOLD_MS) {
                    frozenFrameCount++
                }
                if (frameMs > worstFrameTimeMs) {
                    worstFrameTimeMs = frameMs
                }
            }
            lastFrameTimeNs = frameTimeNanos
            runCatching { Choreographer.getInstance().postFrameCallback(this) }
        }
    }

    private val sampleRunnable = object : Runnable {
        override fun run() {
            if (!isRunning.get()) return
            publishSample()
            mainHandler?.postDelayed(this, SAMPLE_INTERVAL_MS)
        }
    }

    fun initialize(context: Context, isEnabled: Boolean) {
        appContext = context.applicationContext
        enabled = isEnabled
        val app = context.applicationContext as? Application
        if (app != null) {
            synchronized(registeredApps) {
                if (registeredApps.add(app)) {
                    app.registerActivityLifecycleCallbacks(lifecycleCallbacks)
                }
            }
        }
        if (isEnabled && context is Activity) {
            startedActivityCount = maxOf(startedActivityCount, 1)
            startTrackingIfNeeded()
        } else if (!isEnabled) {
            stopTracking()
        }
    }

    fun setEnabled(isEnabled: Boolean) {
        enabled = isEnabled
        if (isEnabled && startedActivityCount > 0) {
            startTrackingIfNeeded()
        } else if (!isEnabled) {
            stopTracking()
        }
    }

    fun refreshNow() {
        publishSample()
    }

    fun forceGcAndRefresh() {
        runCatching { Runtime.getRuntime().gc() }
        publishSample()
    }

    fun resetMetrics() {
        lastFrameTimeNs = 0L
        windowFrameCount = 0
        windowDurationNs = 0L
        totalFramesObserved = 0
        jankyFrameCount = 0
        frozenFrameCount = 0
        worstFrameTimeMs = 0f
        fpsHistoryBuffer.clear()
        heapHistoryBuffer.clear()
        publishSample()
    }

    private fun startTrackingIfNeeded() {
        if (!enabled) return
        if (!isRunning.compareAndSet(false, true)) return
        val handler = mainHandler ?: return
        handler.post {
            if (!isRunning.get()) return@post
            lastFrameTimeNs = 0L
            runCatching { Choreographer.getInstance().postFrameCallback(frameCallback) }
            publishSample()
            handler.removeCallbacks(sampleRunnable)
            handler.postDelayed(sampleRunnable, SAMPLE_INTERVAL_MS)
        }
    }

    private fun stopTracking() {
        if (!isRunning.compareAndSet(true, false)) return
        mainHandler?.post {
            runCatching { Choreographer.getInstance().removeFrameCallback(frameCallback) }
            mainHandler?.removeCallbacks(sampleRunnable)
        }
        _snapshot.value = _snapshot.value.copy(isMonitoring = false)
    }

    private fun publishSample() {
        val currentFps = if (windowFrameCount > 0 && windowDurationNs > 0L) {
            val seconds = windowDurationNs / 1_000_000_000f
            (windowFrameCount / seconds).coerceIn(0f, 120f)
        } else {
            _snapshot.value.fps
        }
        val avgFrameMs = if (windowFrameCount > 0 && windowDurationNs > 0L) {
            (windowDurationNs / 1_000_000f) / windowFrameCount.toFloat()
        } else {
            _snapshot.value.avgFrameTimeMs
        }

        windowFrameCount = 0
        windowDurationNs = 0L

        val runtime = Runtime.getRuntime()
        val usedHeapMb = (runtime.totalMemory() - runtime.freeMemory()).toFloat() / (1024f * 1024f)
        val maxHeapMb = runtime.maxMemory().toFloat().coerceAtLeast(1f) / (1024f * 1024f)
        val nativeHeapMb = runCatching {
            Debug.getNativeHeapAllocatedSize().toFloat() / (1024f * 1024f)
        }.getOrDefault(0f)

        var sysAvailRamMb = 0L
        var isLowMem = false
        appContext?.let { ctx ->
            runCatching {
                val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                if (am != null) {
                    val info = ActivityManager.MemoryInfo()
                    am.getMemoryInfo(info)
                    sysAvailRamMb = info.availMem / (1024L * 1024L)
                    isLowMem = info.lowMemory
                }
            }
        }

        val activeThreads = Thread.activeCount()

        if (currentFps > 0f) {
            if (fpsHistoryBuffer.size >= MAX_HISTORY_POINTS) fpsHistoryBuffer.removeFirst()
            fpsHistoryBuffer.addLast(currentFps)
        }
        if (heapHistoryBuffer.size >= MAX_HISTORY_POINTS) heapHistoryBuffer.removeFirst()
        heapHistoryBuffer.addLast(usedHeapMb)

        _snapshot.value = RuntimePerfSnapshot(
            timestampMs = System.currentTimeMillis(),
            fps = currentFps,
            avgFrameTimeMs = avgFrameMs,
            worstFrameTimeMs = worstFrameTimeMs,
            jankyFrameCount = jankyFrameCount,
            frozenFrameCount = frozenFrameCount,
            totalFramesObserved = totalFramesObserved,
            usedHeapMb = usedHeapMb,
            maxHeapMb = maxHeapMb,
            nativeHeapMb = nativeHeapMb,
            systemAvailRamMb = sysAvailRamMb,
            isLowMemory = isLowMem,
            activeThreadCount = activeThreads,
            fpsHistory = fpsHistoryBuffer.toList(),
            heapHistoryMb = heapHistoryBuffer.toList(),
            isMonitoring = isRunning.get()
        )
    }

    private val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

        override fun onActivityStarted(activity: Activity) {
            startedActivityCount++
            if (enabled) {
                startTrackingIfNeeded()
            }
        }

        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) {
            startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
            if (startedActivityCount == 0) {
                stopTracking()
            }
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
