package com.apilens.presentation.dashboard

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Http
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apilens.ApiLensTheme
import com.apilens.presentation.components.FilterAndSortBar
import com.apilens.presentation.components.RequestCard
import com.apilens.presentation.details.ApiDetailsScreen
import com.apilens.presentation.details.ResponseComparisonSheet
import com.apilens.presentation.performance.PerformanceInspectorScreen
import com.apilens.presentation.theme.JetBrainsMonoFontFamily
import com.apilens.presentation.theme.LocalApiLensColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiDashboardScreen(
    onCloseDashboard: () -> Unit,
    initialSection: DashboardSection = DashboardSection.TRAFFIC,
    vm: ApiDashboardViewModel = viewModel(factory = ApiDashboardViewModel.Factory())
) {
    LaunchedEffect(initialSection) {
        vm.setActiveSection(initialSection)
    }

    val state by vm.uiState.collectAsStateWithLifecycle()
    val colors = LocalApiLensColors.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var showClearDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showComparisonSheet by remember { mutableStateOf(false) }

    val saveJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(vm.buildExportJson().toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Exported ${state.filteredTransactions.size} traces to JSON")
                }
            }.onFailure { err ->
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Export failed: ${err.message}")
                }
            }
        }
    }

    fun shareExportJson() {
        val json = vm.buildExportJson()
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "API Lens Export (${state.filteredTransactions.size} calls)")
            putExtra(Intent.EXTRA_TEXT, json)
        }
        context.startActivity(Intent.createChooser(intent, "Share API Lens JSON export"))
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val isWideLayout = maxWidth >= 720.dp
        val selectedTxId = state.selectedTransactionId

        if (!isWideLayout && selectedTxId != null && state.activeSection == DashboardSection.TRAFFIC) {
            BackHandler {
                vm.selectTransaction(null)
            }
            ApiDetailsScreen(
                transactionId = selectedTxId,
                onBack = { vm.selectTransaction(null) },
                showBackButton = true
            )
        } else {
            Scaffold(
                containerColor = colors.canvas,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = colors.surfaceCard,
                            titleContentColor = colors.textPrimary
                        ),
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.accentCyan.copy(alpha = 0.16f))
                                        .border(1.dp, colors.accentCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Radar,
                                        contentDescription = null,
                                        tint = colors.accentCyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "API Lens",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = colors.textPrimary
                                    )
                                    Text(
                                        text = "In-App Network Inspector",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.textMuted
                                    )
                                }
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    val next = if (colors.isDark) ApiLensTheme.LIGHT else ApiLensTheme.DARK
                                    vm.setTheme(next)
                                },
                                modifier = Modifier.testTag("theme_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (colors.isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                    contentDescription = "Toggle theme",
                                    tint = colors.textSecondary
                                )
                            }
                            if (state.filteredTransactions.isNotEmpty()) {
                                IconButton(
                                    onClick = { saveJsonLauncher.launch("api_lens_traces.json") },
                                    modifier = Modifier.testTag("export_json_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Download,
                                        contentDescription = "Save JSON export",
                                        tint = colors.accentCyan
                                    )
                                }
                                IconButton(
                                    onClick = { shareExportJson() },
                                    modifier = Modifier.testTag("share_export_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Share,
                                        contentDescription = "Share JSON export",
                                        tint = colors.textSecondary
                                    )
                                }
                                IconButton(
                                    onClick = { showClearDialog = true },
                                    modifier = Modifier.testTag("clear_history_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteSweep,
                                        contentDescription = "Clear history",
                                        tint = colors.statusError
                                    )
                                }
                            }
                            IconButton(
                                onClick = { showSettingsSheet = true },
                                modifier = Modifier.testTag("settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Settings,
                                    contentDescription = "API Lens settings",
                                    tint = colors.textSecondary
                                )
                            }
                            IconButton(
                                onClick = onCloseDashboard,
                                modifier = Modifier.testTag("close_dashboard_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close API Lens",
                                    tint = colors.textPrimary
                                )
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    DashboardSectionSwitcher(
                        activeSection = state.activeSection,
                        onSelectSection = vm::setActiveSection,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    if (state.activeSection == DashboardSection.PERFORMANCE) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            PerformanceInspectorScreen(
                                onEndpointClick = { endpointPath ->
                                    vm.inspectEndpointTraffic(endpointPath)
                                },
                                onEnableInspector = {
                                    vm.setPerformanceInspectorEnabled(true)
                                },
                                modifier = Modifier
                                    .widthIn(max = 900.dp)
                                    .fillMaxSize()
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(if (isWideLayout) 0.48f else 1f)
                                    .fillMaxHeight()
                            ) {
                        DashboardMetricsRow(
                            metrics = state.metrics,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )

                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = vm::onSearchQueryChanged,
                            singleLine = true,
                            placeholder = {
                                Text(
                                    "Search URL, endpoint, method, status...",
                                    fontSize = 13.sp,
                                    color = colors.textMuted
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = "Search requests",
                                    tint = colors.textMuted
                                )
                            },
                            trailingIcon = {
                                if (state.searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { vm.onSearchQueryChanged("") }) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Clear search",
                                            tint = colors.textMuted
                                        )
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.accentCyan,
                                unfocusedBorderColor = colors.borderSubtle,
                                focusedContainerColor = colors.surfaceCard,
                                unfocusedContainerColor = colors.surfaceCard,
                                focusedTextColor = colors.textPrimary,
                                unfocusedTextColor = colors.textPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .testTag("dashboard_search_input")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        FilterAndSortBar(
                            selectedFilter = state.selectedFilter,
                            selectedSort = state.selectedSort,
                            onFilterSelected = vm::onFilterSelected,
                            onSortSelected = vm::onSortSelected,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        AnimatedVisibility(visible = state.compareIds.isNotEmpty()) {
                            CompareSelectionBanner(
                                count = state.compareIds.size,
                                onCompare = { showComparisonSheet = true },
                                onClear = vm::clearCompareSelection
                            )
                        }

                        if (state.filteredTransactions.isEmpty()) {
                            EmptyRequestsView(
                                hasTotalRequests = state.allTransactions.isNotEmpty(),
                                onResetFilters = {
                                    vm.onSearchQueryChanged("")
                                    vm.onFilterSelected(ApiFilter.ALL)
                                }
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("requests_lazy_column"),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(
                                    items = state.filteredTransactions,
                                    key = { it.id }
                                ) { tx ->
                                    RequestCard(
                                        transaction = tx,
                                        slowThresholdMs = state.config.slowRequestThresholdMs,
                                        isSelected = isWideLayout && tx.id == selectedTxId,
                                        isMarkedForCompare = tx.id in state.compareIds,
                                        onClick = { vm.selectTransaction(tx.id) },
                                        onToggleCompare = { vm.toggleCompareSelection(tx.id) }
                                    )
                                }
                            }
                        }
                    }

                    if (isWideLayout) {
                        VerticalDivider(color = colors.borderSubtle)
                        Box(
                            modifier = Modifier
                                .weight(0.52f)
                                .fillMaxHeight()
                        ) {
                            if (selectedTxId != null) {
                                ApiDetailsScreen(
                                    transactionId = selectedTxId,
                                    onBack = { vm.selectTransaction(null) },
                                    showBackButton = false
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Select any API request on the left to inspect headers, JSON, and timing.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.textSecondary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(24.dp)
                                    )
                                }
                            }
                        }
                    }
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = colors.surfaceCard,
            title = { Text("Clear Request History?", color = colors.textPrimary) },
            text = {
                Text(
                    "This removes all captured API requests from memory and the local database.",
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        vm.clearHistory()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.statusError),
                    modifier = Modifier.testTag("confirm_clear_button")
                ) {
                    Text("Clear All", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }

    if (showSettingsSheet) {
        SettingsBottomSheet(
            state = state,
            onDismiss = { showSettingsSheet = false },
            onToggleFloatingButton = vm::setFloatingButtonVisible,
            onToggleBodyInspection = vm::setBodyInspectionEnabled,
            onTogglePerformanceInspector = vm::setPerformanceInspectorEnabled,
            onThemeSelected = vm::setTheme,
            onSlowThresholdSelected = vm::setSlowThresholdMs
        )
    }

    if (showComparisonSheet && state.compareIds.size == 2) {
        val left = state.allTransactions.firstOrNull { it.id == state.compareIds[0] }
        val right = state.allTransactions.firstOrNull { it.id == state.compareIds[1] }
        if (left != null && right != null) {
            ResponseComparisonSheet(
                left = left,
                right = right,
                onDismiss = { showComparisonSheet = false }
            )
        }
    }
}

@Composable
private fun DashboardMetricsRow(
    metrics: DashboardMetrics,
    modifier: Modifier = Modifier
) {
    val colors = LocalApiLensColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricTile(
            label = "Total",
            value = metrics.totalRequests.toString(),
            accent = colors.accentCyan,
            modifier = Modifier.weight(1f).testTag("metric_total")
        )
        MetricTile(
            label = "Success",
            value = metrics.successRequests.toString(),
            accent = colors.statusSuccess,
            modifier = Modifier.weight(1f).testTag("metric_success")
        )
        MetricTile(
            label = "Failed",
            value = metrics.failedRequests.toString(),
            accent = colors.statusError,
            modifier = Modifier.weight(1f).testTag("metric_failed")
        )
        MetricTile(
            label = "Avg Latency",
            value = "${metrics.averageResponseMs}ms",
            accent = if (metrics.slowRequests > 0) colors.slowWarning else colors.textPrimary,
            modifier = Modifier.weight(1f).testTag("metric_avg_latency")
        )
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalApiLensColors.current
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
        border = BorderStroke(1.dp, colors.borderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textMuted,
                maxLines = 1
            )
            Text(
                text = value,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = accent,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CompareSelectionBanner(
    count: Int,
    onCompare: () -> Unit,
    onClear: () -> Unit
) {
    val colors = LocalApiLensColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.methodPost.copy(alpha = 0.14f))
            .border(1.dp, colors.methodPost.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.CompareArrows,
                contentDescription = null,
                tint = colors.methodPost,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = if (count == 1) "Select 1 more request to compare" else "2 requests ready to compare",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textPrimary
            )
        }
        Row {
            if (count == 2) {
                TextButton(
                    onClick = onCompare,
                    modifier = Modifier.testTag("open_compare_button")
                ) {
                    Text("Compare", color = colors.methodPost, fontWeight = FontWeight.Bold)
                }
            }
            TextButton(onClick = onClear) {
                Text("Clear", color = colors.textSecondary)
            }
        }
    }
}

@Composable
private fun EmptyRequestsView(
    hasTotalRequests: Boolean,
    onResetFilters: () -> Unit
) {
    val colors = LocalApiLensColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Http,
            contentDescription = null,
            tint = colors.accentCyan.copy(alpha = 0.6f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (hasTotalRequests) "No matching requests" else "Waiting for HTTP traffic",
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (hasTotalRequests) {
                "Try clearing your search query or switching filters."
            } else {
                "Every OkHttp and Retrofit request made by your app will appear here in real time."
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )
        if (hasTotalRequests) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onResetFilters,
                colors = ButtonDefaults.buttonColors(containerColor = colors.accentCyan)
            ) {
                Text("Reset Filters", color = colors.canvas)
            }
        }
    }
}

@Composable
private fun DashboardSectionSwitcher(
    activeSection: DashboardSection,
    onSelectSection: (DashboardSection) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalApiLensColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceCard)
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DashboardSection.entries.forEach { section ->
            val selected = section == activeSection
            val icon = if (section == DashboardSection.TRAFFIC) {
                Icons.Rounded.Http
            } else {
                Icons.Rounded.Speed
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) colors.accentCyan.copy(alpha = 0.16f) else Color.Transparent)
                    .border(
                        width = if (selected) 1.dp else 0.dp,
                        color = if (selected) colors.accentCyan.copy(alpha = 0.5f) else Color.Transparent,
                        shape = RoundedCornerShape(9.dp)
                    )
                    .clickable { onSelectSection(section) }
                    .padding(vertical = 8.dp, horizontal = 12.dp)
                    .testTag("section_tab_${section.name.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = section.label,
                    tint = if (selected) colors.accentCyan else colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = section.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = 13.sp,
                    color = if (selected) colors.accentCyan else colors.textSecondary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsBottomSheet(
    state: ApiDashboardUiState,
    onDismiss: () -> Unit,
    onToggleFloatingButton: (Boolean) -> Unit,
    onToggleBodyInspection: (Boolean) -> Unit,
    onTogglePerformanceInspector: (Boolean) -> Unit,
    onThemeSelected: (ApiLensTheme) -> Unit,
    onSlowThresholdSelected: (Long) -> Unit
) {
    val colors = LocalApiLensColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "API Lens Settings",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Floating Debug Button", color = colors.textPrimary, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Show draggable counter pill over host screens",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = state.config.showFloatingButton,
                    onCheckedChange = onToggleFloatingButton
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Body Inspection", color = colors.textPrimary, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Capture request & response payloads (with sensitive key redaction)",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = state.config.enableBodyInspection,
                    onCheckedChange = onToggleBodyInspection
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Performance Inspector", color = colors.textPrimary, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Track live UI FPS, frame jank, and JVM/native memory",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = state.config.enablePerformanceInspector,
                    onCheckedChange = onTogglePerformanceInspector
                )
            }

            HorizontalDivider(color = colors.borderSubtle)

            Text("Theme", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ApiLensTheme.entries.forEach { theme ->
                    FilterChip(
                        selected = state.config.theme == theme,
                        onClick = { onThemeSelected(theme) },
                        label = { Text(theme.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.accentCyan.copy(alpha = 0.2f),
                            selectedLabelColor = colors.accentCyan
                        )
                    )
                }
            }

            Text("Slow API Threshold", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(500L, 1000L, 2000L, 3000L).forEach { threshold ->
                    FilterChip(
                        selected = state.config.slowRequestThresholdMs == threshold,
                        onClick = { onSlowThresholdSelected(threshold) },
                        label = { Text("${threshold}ms") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.slowWarning.copy(alpha = 0.2f),
                            selectedLabelColor = colors.slowWarning
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
