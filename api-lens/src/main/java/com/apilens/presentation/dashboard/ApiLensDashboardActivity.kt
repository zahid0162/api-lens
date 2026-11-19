package com.apilens.presentation.dashboard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apilens.ApiLens
import com.apilens.presentation.theme.ApiLensMaterialTheme

/**
 * Host activity displaying the full-screen API Lens debugging dashboard, performance inspector,
 * and request detail views. Automatically excluded from floating overlay attachment.
 */
class ApiLensDashboardActivity : ComponentActivity() {

    private var requestedSection by mutableStateOf(DashboardSection.TRAFFIC)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedSection = resolveSection(intent)

        setContent {
            val config by ApiLens.configFlow().collectAsStateWithLifecycle()
            ApiLensMaterialTheme(themeMode = config.theme) {
                ApiDashboardScreen(
                    initialSection = requestedSection,
                    onCloseDashboard = { finish() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        requestedSection = resolveSection(intent)
    }

    private fun resolveSection(intent: Intent?): DashboardSection {
        return when (intent?.getStringExtra(EXTRA_INITIAL_SECTION)) {
            SECTION_PERFORMANCE -> DashboardSection.PERFORMANCE
            else -> DashboardSection.TRAFFIC
        }
    }

    companion object {
        const val EXTRA_INITIAL_SECTION = "com.apilens.EXTRA_INITIAL_SECTION"
        const val SECTION_TRAFFIC = "traffic"
        const val SECTION_PERFORMANCE = "performance"
    }
}
