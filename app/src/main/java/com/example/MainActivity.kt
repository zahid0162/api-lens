package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apilens.ApiLens
import com.example.ui.theme.JetBrainsMonoFont
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: SamplePlaygroundViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            MyApplicationTheme(darkTheme = true) {
                Scaffold(
                    contentWindowInsets = WindowInsets.safeDrawing,
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        TopAppBar(
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Radar,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            text = "API Lens Playground",
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                        Text(
                                            text = "Retrofit + OkHttp Host Sample",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        )
                    }
                ) { padding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        contentPadding = PaddingValues(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 600.dp)
                                    .fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                HeroControlCard(
                                    baseUrl = state.serverBaseUrl,
                                    isLoading = state.isLoading,
                                    onOpenDashboard = { ApiLens.showDashboard(this@MainActivity) },
                                    onRunSuite = { viewModel.runBurstSuite() },
                                    onClearHistory = { ApiLens.clearHistory() },
                                    onOpenSecondActivity = {
                                        startActivity(Intent(this@MainActivity, SecondaryDemoActivity::class.java))
                                    }
                                )

                                LastOutcomeCard(state = state)

                                EndpointGroupCard(
                                    title = "Success & Redaction Scenarios",
                                    subtitle = "Real Retrofit calls with header, query, and JSON token redaction"
                                ) {
                                    EndpointActionRow(
                                        label = "GET /api/products (200 JSON + Redacted Query)",
                                        testTag = "btn_get_products",
                                        onClick = { viewModel.fetchProducts() }
                                    )
                                    EndpointActionRow(
                                        label = "POST /api/login (200 + Password/Token Redaction)",
                                        testTag = "btn_post_login",
                                        onClick = { viewModel.loginWithCredentials() }
                                    )
                                    EndpointActionRow(
                                        label = "PUT /api/profile (200 OK)",
                                        testTag = "btn_put_profile",
                                        onClick = { viewModel.updateProfile() }
                                    )
                                    EndpointActionRow(
                                        label = "PATCH /api/preferences (200 OK)",
                                        testTag = "btn_patch_prefs",
                                        onClick = { viewModel.patchPreferences() }
                                    )
                                    EndpointActionRow(
                                        label = "DELETE /api/session (204 No Content)",
                                        testTag = "btn_delete_session",
                                        onClick = { viewModel.deleteSession() }
                                    )
                                }

                                EndpointGroupCard(
                                    title = "HTTP Errors, Slow API & Network Faults",
                                    subtitle = "Inspect 401/500 payloads, Slow API detection, Timeouts & DNS failures"
                                ) {
                                    EndpointActionRow(
                                        label = "GET /api/customers (401 Unauthorized)",
                                        testTag = "btn_get_401",
                                        isErrorAction = true,
                                        onClick = { viewModel.fetchCustomersUnauthorized() }
                                    )
                                    EndpointActionRow(
                                        label = "POST /api/orders (500 Server Error)",
                                        testTag = "btn_post_500",
                                        isErrorAction = true,
                                        onClick = { viewModel.submitOrderError() }
                                    )
                                    EndpointActionRow(
                                        label = "GET /api/analytics/slow (1.25s Slow API)",
                                        testTag = "btn_get_slow",
                                        onClick = { viewModel.fetchSlowAnalytics() }
                                    )
                                    EndpointActionRow(
                                        label = "GET /api/timeout (SocketTimeoutException)",
                                        testTag = "btn_timeout",
                                        isErrorAction = true,
                                        onClick = { viewModel.triggerSocketTimeout() }
                                    )
                                    EndpointActionRow(
                                        label = "GET invalid-host (UnknownHostException)",
                                        testTag = "btn_unknown_host",
                                        isErrorAction = true,
                                        onClick = { viewModel.triggerUnknownHost() }
                                    )
                                    EndpointActionRow(
                                        label = "GET closed-port (Connection Failure)",
                                        testTag = "btn_connect_fail",
                                        isErrorAction = true,
                                        onClick = { viewModel.triggerConnectionFailure() }
                                    )
                                }

                                Spacer(modifier = Modifier.height(64.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroControlCard(
    baseUrl: String,
    isLoading: Boolean,
    onOpenDashboard: () -> Unit,
    onRunSuite: () -> Unit,
    onClearHistory: () -> Unit,
    onOpenSecondActivity: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Tap the floating API Lens pill anywhere in the app or launch the inspector below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Embedded Server: $baseUrl",
                fontFamily = JetBrainsMonoFont,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenDashboard,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_open_dashboard")
                ) {
                    Icon(Icons.Rounded.BugReport, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open Inspector")
                }
                FilledTonalButton(
                    onClick = onRunSuite,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_run_suite")
                ) {
                    Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Run 4 Calls")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenSecondActivity,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_second_activity")
                ) {
                    Icon(Icons.Rounded.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("2nd Activity")
                }
                OutlinedButton(
                    onClick = onClearHistory,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_clear_sample_history")
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear Logs")
                }
            }
        }
    }
}

@Composable
private fun LastOutcomeCard(state: SampleUiState) {
    val borderColor = if (state.lastCallError) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.outline
    }
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Latest Host Response • ${state.lastCallTitle}",
                fontFamily = JetBrainsMonoFont,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = if (state.lastCallError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Text(
                text = state.lastCallOutcome,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun EndpointGroupCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            content()
        }
    }
}

@Composable
private fun EndpointActionRow(
    label: String,
    testTag: String,
    isErrorAction: Boolean = false,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = if (isErrorAction) {
            ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                contentColor = Color(0xFFFCA5A5)
            )
        } else {
            ButtonDefaults.filledTonalButtonColors()
        },
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontFamily = JetBrainsMonoFont,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Execute request",
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
