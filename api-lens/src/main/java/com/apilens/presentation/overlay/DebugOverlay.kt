package com.apilens.presentation.overlay

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.apilens.ApiLens
import com.apilens.ApiLensConfig
import com.apilens.FloatingButtonPosition
import com.apilens.R
import com.apilens.presentation.dashboard.ApiLensDashboardActivity
import com.apilens.presentation.theme.ApiLensMaterialTheme
import kotlinx.coroutines.flow.StateFlow
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

/**
 * Lifecycle-aware application-level overlay manager that attaches a non-blocking
 * Compose floating button to resumed host Activities without requiring SYSTEM_ALERT_WINDOW.
 *
 * Uses [ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed] and removes the
 * overlay view on [onActivityDestroyed] so destroyed Activities are never leaked.
 */
internal class DebugOverlayManager(
    private val configFlow: StateFlow<ApiLensConfig>,
    private val enabledFlow: StateFlow<Boolean>,
    private val requestStatsProvider: () -> StateFlow<OverlayStats>
) {
    data class OverlayStats(
        val totalCount: Int = 0,
        val failedCount: Int = 0
    )

    private val registeredApplications = Collections.synchronizedSet(
        Collections.newSetFromMap(WeakHashMap<Application, Boolean>())
    )

    // Normalized [0f..1f] coordinates persisted across activity transitions and config changes.
    @Volatile
    private var hasUserDragged: Boolean = false

    @Volatile
    private var savedXFraction: Float = 1f

    @Volatile
    private var savedYFraction: Float = 0.85f

    private var currentActivityRef: WeakReference<Activity>? = null

    fun register(application: Application) {
        synchronized(registeredApplications) {
            if (registeredApplications.add(application)) {
                application.registerActivityLifecycleCallbacks(lifecycleCallbacks)
            }
        }
    }

    fun tryAttachToCurrentActivity(activity: Activity) {
        if (activity is ApiLensDashboardActivity) return
        attachOverlayToActivity(activity)
    }

    private val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityResumed(activity: Activity) {
            if (activity is ApiLensDashboardActivity) return
            currentActivityRef = WeakReference(activity)
            attachOverlayToActivity(activity)
        }

        override fun onActivityPaused(activity: Activity) {
            if (currentActivityRef?.get() === activity) {
                currentActivityRef = null
            }
        }

        override fun onActivityStopped(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) {
            if (currentActivityRef?.get() === activity) {
                currentActivityRef = null
            }
            detachOverlayFromActivity(activity)
        }
    }

    private fun attachOverlayToActivity(activity: Activity) {
        val contentRoot = activity.findViewById<ViewGroup>(android.R.id.content) ?: return
        val lifecycleOwner = contentRoot.findViewTreeLifecycleOwner()
            ?: activity.window?.decorView?.findViewTreeLifecycleOwner()
        if (lifecycleOwner == null) return

        val existing = contentRoot.findViewById<View>(R.id.api_lens_overlay_view)
        if (existing != null) {
            existing.bringToFront()
            return
        }

        val density = activity.resources.displayMetrics.density
        val marginPx = (16f * density).toInt()
        var safeLeft = marginPx
        var safeTop = marginPx
        var safeRight = marginPx
        var safeBottom = marginPx

        val activityWeakRef = WeakReference(activity)

        val composeView = ComposeView(activity).apply {
            id = R.id.api_lens_overlay_view
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            )
        }

        fun applyPositionFromFractions(parentW: Int, parentH: Int, viewW: Int, viewH: Int) {
            if (parentW <= 0 || parentH <= 0 || viewW <= 0 || viewH <= 0) return
            val minX = safeLeft.toFloat()
            val maxX = (parentW - safeRight - viewW).coerceAtLeast(safeLeft).toFloat()
            val minY = safeTop.toFloat()
            val maxY = (parentH - safeBottom - viewH).coerceAtLeast(safeTop).toFloat()

            val (fx, fy) = if (hasUserDragged) {
                savedXFraction to savedYFraction
            } else {
                defaultFractions(configFlow.value.floatingButtonPosition)
            }

            composeView.translationX = (minX + (maxX - minX) * fx).coerceIn(minX, maxX)
            composeView.translationY = (minY + (maxY - minY) * fy).coerceIn(minY, maxY)
        }

        ViewCompat.setOnApplyWindowInsetsListener(contentRoot) { _, insets ->
            val sysBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            safeLeft = sysBars.left + marginPx
            safeTop = sysBars.top + marginPx
            safeRight = sysBars.right + marginPx
            safeBottom = sysBars.bottom + marginPx
            applyPositionFromFractions(
                contentRoot.width,
                contentRoot.height,
                composeView.width,
                composeView.height
            )
            insets
        }

        composeView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            applyPositionFromFractions(
                contentRoot.width,
                contentRoot.height,
                composeView.width,
                composeView.height
            )
        }

        contentRoot.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            applyPositionFromFractions(
                contentRoot.width,
                contentRoot.height,
                composeView.width,
                composeView.height
            )
        }

        composeView.setContent {
            val config by configFlow.collectAsStateWithLifecycle()
            val enabled by enabledFlow.collectAsStateWithLifecycle()
            val stats by requestStatsProvider().collectAsStateWithLifecycle()
            val showOverlay = enabled && config.showFloatingButton

            ApiLensMaterialTheme(themeMode = config.theme) {
                FloatingDebugButton(
                    visible = showOverlay,
                    requestCount = stats.totalCount,
                    failedCount = stats.failedCount,
                    onClick = {
                        activityWeakRef.get()?.let { ApiLens.showDashboard(it) }
                    },
                    onDragDelta = { dx, dy ->
                        val parentW = contentRoot.width
                        val parentH = contentRoot.height
                        val viewW = composeView.width
                        val viewH = composeView.height
                        if (parentW > 0 && parentH > 0) {
                            val minX = safeLeft.toFloat()
                            val maxX = (parentW - safeRight - viewW).coerceAtLeast(safeLeft).toFloat()
                            val minY = safeTop.toFloat()
                            val maxY = (parentH - safeBottom - viewH).coerceAtLeast(safeTop).toFloat()

                            val nextX = (composeView.translationX + dx).coerceIn(minX, maxX)
                            val nextY = (composeView.translationY + dy).coerceIn(minY, maxY)
                            composeView.translationX = nextX
                            composeView.translationY = nextY
                            hasUserDragged = true
                            savedXFraction = if (maxX > minX) (nextX - minX) / (maxX - minX) else 0f
                            savedYFraction = if (maxY > minY) (nextY - minY) / (maxY - minY) else 0f
                        }
                    },
                    onDragEnd = {
                        // Snap to nearest horizontal edge for ergonomic thumb placement
                        val parentW = contentRoot.width
                        val viewW = composeView.width
                        val minX = safeLeft.toFloat()
                        val maxX = (parentW - safeRight - viewW).coerceAtLeast(safeLeft).toFloat()
                        if (maxX > minX) {
                            val snapRight = savedXFraction >= 0.5f
                            savedXFraction = if (snapRight) 1f else 0f
                            composeView.translationX = if (snapRight) maxX else minX
                        }
                    }
                )
            }
        }

        contentRoot.addView(composeView)
        ViewCompat.requestApplyInsets(contentRoot)
    }

    private fun detachOverlayFromActivity(activity: Activity) {
        val contentRoot = activity.findViewById<FrameLayout>(android.R.id.content) ?: return
        val existing = contentRoot.findViewById<View>(R.id.api_lens_overlay_view) ?: return
        contentRoot.removeView(existing)
    }

    private fun defaultFractions(position: FloatingButtonPosition): Pair<Float, Float> {
        return when (position) {
            FloatingButtonPosition.TOP_START -> 0f to 0.08f
            FloatingButtonPosition.TOP_END -> 1f to 0.08f
            FloatingButtonPosition.CENTER_START -> 0f to 0.5f
            FloatingButtonPosition.CENTER_END -> 1f to 0.5f
            FloatingButtonPosition.BOTTOM_START -> 0f to 0.85f
            FloatingButtonPosition.BOTTOM_END -> 1f to 0.85f
        }
    }
}
