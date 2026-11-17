package com.apilens.presentation.dashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apilens.ApiLens
import com.apilens.presentation.theme.ApiLensMaterialTheme

/**
 * Host activity displaying the full-screen API Lens debugging dashboard and detail views.
 * Automatically excluded from floating overlay attachment.
 */
class ApiLensDashboardActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val config by ApiLens.configFlow().collectAsStateWithLifecycle()
            ApiLensMaterialTheme(themeMode = config.theme) {
                ApiDashboardScreen(
                    onCloseDashboard = { finish() }
                )
            }
        }
    }
}
