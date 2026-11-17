package com.apilens.presentation.details

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.WarningAmber
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.apilens.core.ApiErrorType
import com.apilens.core.ApiHeader
import com.apilens.core.ApiTransaction
import com.apilens.presentation.components.HttpMethodBadge
import com.apilens.presentation.components.JsonDisplayMode
import com.apilens.presentation.components.JsonPayloadViewer
import com.apilens.presentation.components.RedactedBadge
import com.apilens.presentation.components.SlowApiBadge
import com.apilens.presentation.components.StatusIndicator
import com.apilens.presentation.components.countJsonMatches
import com.apilens.presentation.theme.JetBrainsMonoFontFamily
import com.apilens.presentation.theme.LocalApiLensColors
import com.apilens.utils.JsonFormatter
import com.apilens.utils.RequestFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiDetailsScreen(
    transactionId: String,
    onBack: () -> Unit,
    showBackButton: Boolean = true,
    modifier: Modifier = Modifier
) {
    val vm: ApiDetailsViewModel = viewModel(
        key = "details_$transactionId",
        factory = ApiDetailsViewModel.Factory(transactionId)
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val colors = LocalApiLensColors.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    var showReplayWarningDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.replayState) {
        when (val replay = state.replayState) {
            is ReplayUiState.Completed -> {
                snackbarHostState.showSnackbar("Replay finished with HTTP ${replay.statusCode}")
                vm.dismissReplayBanner()
            }
            is ReplayUiState.Failed -> {
                snackbarHostState.showSnackbar("Replay failed: ${replay.message}")
                vm.dismissReplayBanner()
            }
            else -> Unit
        }
    }

    fun copyText(label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        coroutineScope.launch {
            snackbarHostState.showSnackbar("$label copied to clipboard")
        }
    }

    fun shareTransaction(tx: ApiTransaction) {
        val summary = RequestFormatter.toShareSummary(tx)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "API Trace: ${tx.request.method} ${tx.request.path}")
            putExtra(Intent.EXTRA_TEXT, summary)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share API trace"))
    }

    val tx = state.transaction

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.canvas,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surfaceCard,
                    titleContentColor = colors.textPrimary
                ),
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("details_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back to dashboard",
                                tint = colors.textPrimary
                            )
                        }
                    }
                },
                title = {
                    if (tx != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HttpMethodBadge(method = tx.request.method)
                            Text(
                                text = tx.request.path,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                    } else {
                        Text("Request Details", style = MaterialTheme.typography.titleMedium)
                    }
                },
                actions = {
                    if (tx != null) {
                        IconButton(
                            onClick = { copyText("cURL command", RequestFormatter.toCurlCommand(tx)) },
                            modifier = Modifier.testTag("copy_curl_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Terminal,
                                contentDescription = "Copy as cURL",
                                tint = colors.accentCyan
                            )
                        }
                        IconButton(
                            onClick = { shareTransaction(tx) },
                            modifier = Modifier.testTag("share_details_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = "Share request details",
                                tint = colors.textSecondary
                            )
                        }
                        IconButton(
                            onClick = { showReplayWarningDialog = true },
                            enabled = state.replayState !is ReplayUiState.Running,
                            modifier = Modifier.testTag("replay_request_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Replay,
                                contentDescription = "Replay request",
                                tint = colors.statusSuccess
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (tx == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Select a captured request to inspect.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Sub-header status strip + details search
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceCard)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusIndicator(transaction = tx)
                Text(
                    text = "${tx.timing.totalDurationMs} ms",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                if (!tx.isPending && tx.timing.totalDurationMs >= state.slowThresholdMs) {
                    SlowApiBadge()
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = RequestFormatter.formatShortTime(tx.request.timestampMs),
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    color = colors.textMuted
                )
            }

            TabRow(
                selectedTabIndex = state.selectedTab.ordinal,
                containerColor = colors.surfaceCard,
                contentColor = colors.accentCyan,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[state.selectedTab.ordinal]),
                        color = colors.accentCyan
                    )
                }
            ) {
                DetailsTab.entries.forEach { tab ->
                    Tab(
                        selected = state.selectedTab == tab,
                        onClick = { vm.selectTab(tab) },
                        text = {
                            Text(
                                text = tab.title,
                                fontSize = 13.sp,
                                fontWeight = if (state.selectedTab == tab) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.testTag("details_tab_${tab.name.lowercase()}")
                    )
                }
            }

            when (state.selectedTab) {
                DetailsTab.OVERVIEW -> OverviewTabContent(
                    tx = tx,
                    slowThresholdMs = state.slowThresholdMs,
                    detailSearchQuery = state.detailSearchQuery,
                    onDetailSearchChange = vm::setDetailSearchQuery,
                    onCopyUrl = { copyText("URL", tx.request.url) }
                )
                DetailsTab.REQUEST -> RequestTabContent(
                    tx = tx,
                    displayMode = state.requestDisplayMode,
                    onDisplayModeChange = vm::setRequestDisplayMode,
                    onCopyBody = { body -> copyText("Request body", body) }
                )
                DetailsTab.RESPONSE -> ResponseTabContent(
                    tx = tx,
                    displayMode = state.responseDisplayMode,
                    searchQuery = state.responseSearchQuery,
                    onDisplayModeChange = vm::setResponseDisplayMode,
                    onSearchQueryChange = vm::setResponseSearchQuery,
                    onCopyJson = { formatted -> copyText("Formatted JSON", formatted) },
                    onCopyRaw = { raw -> copyText("Raw response", raw) }
                )
                DetailsTab.TIMING -> TimingTabContent(tx = tx)
            }
        }
    }

    if (showReplayWarningDialog && tx != null) {
        val hasRedactedHeader = tx.request.headers.any { it.isRedacted }
        AlertDialog(
            onDismissRequest = { showReplayWarningDialog = false },
            containerColor = colors.surfaceCard,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = "Security warning",
                    tint = colors.slowWarning
                )
            },
            title = {
                Text("Replay HTTP Request?", color = colors.textPrimary)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Replaying ${tx.request.method} ${tx.request.path} will send a real network request to ${tx.request.host}. Non-idempotent requests (POST/PUT/DELETE) may mutate server state.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                    if (hasRedactedHeader) {
                        Text(
                            text = "Note: Sensitive redacted headers (such as Authorization or Cookies) are stripped during replay for security.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.redactedFg
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showReplayWarningDialog = false
                        vm.replayCurrentTransaction()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentCyan),
                    modifier = Modifier.testTag("confirm_replay_button")
                ) {
                    Text("Replay Now", color = colors.canvas, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReplayWarningDialog = false }) {
                    Text("Cancel", color = colors.textSecondary)
                }
            }
        )
    }
}

@Composable
private fun OverviewTabContent(
    tx: ApiTransaction,
    slowThresholdMs: Long,
    detailSearchQuery: String,
    onDetailSearchChange: (String) -> Unit,
    onCopyUrl: () -> Unit
) {
    val colors = LocalApiLensColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SearchFieldCompact(
            value = detailSearchQuery,
            onValueChange = onDetailSearchChange,
            placeholder = "Filter overview fields...",
            testTag = "overview_search_input"
        )

        if (tx.errorType != ApiErrorType.NONE || tx.isFailure) {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.statusError.copy(alpha = 0.12f)),
                border = BorderStroke(1.dp, colors.statusError.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = tx.errorType.label,
                        color = colors.statusError,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = tx.errorMessage ?: "Request failed",
                        color = colors.textPrimary,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
            border = BorderStroke(1.dp, colors.borderSubtle),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Full URL", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                    OutlinedButton(
                        onClick = onCopyUrl,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy URL",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy URL", fontSize = 11.sp)
                    }
                }
                SelectionContainer {
                    Text(
                        text = tx.request.url,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                        color = colors.accentCyan
                    )
                }
            }
        }

        val rows = listOf(
            "Method" to tx.request.method,
            "Status" to (tx.response?.let { "${it.statusCode} ${it.message}".trim() } ?: tx.errorType.label),
            "Total Duration" to "${tx.timing.totalDurationMs} ms" +
                if (!tx.isPending && tx.timing.totalDurationMs >= slowThresholdMs) " (Slow)" else "",
            "Request Timestamp" to RequestFormatter.formatFullTimestamp(tx.request.timestampMs),
            "Response Timestamp" to (tx.response?.let { RequestFormatter.formatFullTimestamp(it.timestampMs) } ?: "N/A"),
            "Request Size" to RequestFormatter.formatBytes(tx.request.sizeBytes),
            "Response Size" to (tx.response?.let { RequestFormatter.formatBytes(it.sizeBytes) } ?: "0 B"),
            "Request Content-Type" to (tx.request.contentType ?: "None"),
            "Response Content-Type" to (tx.response?.contentType ?: "None"),
            "Protocol" to (tx.timing.protocol ?: "Unavailable"),
            "Remote Address" to (tx.timing.remoteAddress ?: "Unavailable")
        ).filter { (k, v) ->
            detailSearchQuery.isBlank() ||
                k.contains(detailSearchQuery, ignoreCase = true) ||
                v.contains(detailSearchQuery, ignoreCase = true)
        }

        KeyValueCard(title = "Transaction Summary", entries = rows)
    }
}

@Composable
private fun RequestTabContent(
    tx: ApiTransaction,
    displayMode: JsonDisplayMode,
    onDisplayModeChange: (JsonDisplayMode) -> Unit,
    onCopyBody: (String) -> Unit
) {
    val colors = LocalApiLensColors.current
    val req = tx.request

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (req.queryParams.isNotEmpty()) {
            KeyValueCard(
                title = "Query Parameters (${req.queryParams.size})",
                entries = req.queryParams
            )
        }

        HeadersCard(
            title = "Request Headers (${req.headers.size})",
            headers = req.headers
        )

        val formParams = remember(req.body, req.contentType) {
            if (JsonFormatter.isFormUrlEncoded(req.contentType) && !req.body.isNullOrBlank()) {
                JsonFormatter.parseFormUrlEncoded(req.body)
            } else {
                emptyList()
            }
        }
        if (formParams.isNotEmpty()) {
            KeyValueCard(
                title = "Form Data (${formParams.size})",
                entries = formParams
            )
        }

        SectionHeaderRow(
            title = "Request Body",
            trailing = {
                if (!req.body.isNullOrBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(JsonDisplayMode.PRETTY, JsonDisplayMode.RAW).forEach { mode ->
                            FilterChip(
                                selected = displayMode == mode,
                                onClick = { onDisplayModeChange(mode) },
                                label = { Text(mode.label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = colors.accentCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = colors.accentCyan
                                )
                            )
                        }
                        OutlinedButton(onClick = { onCopyBody(req.body) }) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy request body", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy", fontSize = 11.sp)
                        }
                    }
                }
            }
        )

        if (req.bodyTruncated) {
            InfoBanner(text = "Request body truncated to configured max capture size.", isWarning = true)
        }
        if (req.bodyOmittedReason != null) {
            InfoBanner(text = req.bodyOmittedReason, isWarning = false)
        } else if (req.body.isNullOrBlank()) {
            InfoBanner(text = "No request body", isWarning = false)
        } else {
            JsonPayloadViewer(
                rawBody = req.body,
                contentType = req.contentType,
                displayMode = displayMode,
                searchQuery = ""
            )
        }
    }
}

@Composable
private fun ResponseTabContent(
    tx: ApiTransaction,
    displayMode: JsonDisplayMode,
    searchQuery: String,
    onDisplayModeChange: (JsonDisplayMode) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCopyJson: (String) -> Unit,
    onCopyRaw: (String) -> Unit
) {
    val colors = LocalApiLensColors.current
    val resp = tx.response

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (resp == null) {
            InfoBanner(
                text = if (tx.isPending) "Waiting for response..." else "No HTTP response received (${tx.errorType.label}).",
                isWarning = !tx.isPending
            )
            return@Column
        }

        HeadersCard(
            title = "Response Headers (${resp.headers.size})",
            headers = resp.headers
        )

        SectionHeaderRow(
            title = "Response Body",
            trailing = {
                if (!resp.body.isNullOrBlank()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = { onCopyJson(JsonFormatter.prettyPrint(resp.body)) },
                            modifier = Modifier.testTag("copy_json_button")
                        ) {
                            Icon(Icons.Rounded.Code, contentDescription = "Copy formatted JSON", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy JSON", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { onCopyRaw(resp.body) },
                            modifier = Modifier.testTag("copy_raw_button")
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy raw response", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Raw", fontSize = 11.sp)
                        }
                    }
                }
            }
        )

        if (!resp.body.isNullOrBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                JsonDisplayMode.entries.forEach { mode ->
                    FilterChip(
                        selected = displayMode == mode,
                        onClick = { onDisplayModeChange(mode) },
                        label = { Text(mode.label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.accentCyan.copy(alpha = 0.2f),
                            selectedLabelColor = colors.accentCyan
                        ),
                        modifier = Modifier.testTag("response_mode_${mode.name.lowercase()}")
                    )
                }
            }

            val matchCount = remember(resp.body, searchQuery, displayMode) {
                val target = if (displayMode == JsonDisplayMode.RAW) resp.body else JsonFormatter.prettyPrint(resp.body)
                countJsonMatches(target, searchQuery)
            }

            SearchFieldCompact(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = "Search inside JSON / body...",
                trailingText = if (searchQuery.isNotBlank()) "$matchCount hits" else null,
                testTag = "json_search_input"
            )
        }

        if (resp.bodyTruncated) {
            InfoBanner(text = "Response body truncated to configured max capture size.", isWarning = true)
        }
        if (resp.bodyOmittedReason != null) {
            InfoBanner(text = resp.bodyOmittedReason, isWarning = false)
        } else if (resp.body.isNullOrBlank()) {
            InfoBanner(text = "Empty response body", isWarning = false)
        } else {
            JsonPayloadViewer(
                rawBody = resp.body,
                contentType = resp.contentType,
                displayMode = displayMode,
                searchQuery = searchQuery
            )
        }
    }
}

@Composable
private fun TimingTabContent(tx: ApiTransaction) {
    val colors = LocalApiLensColors.current
    val timing = tx.timing
    val totalMs = timing.totalDurationMs.coerceAtLeast(1L)

    data class PhaseRow(val label: String, val durationMs: Long?, val color: Color)

    val phases = listOf(
        PhaseRow("DNS Lookup", timing.dnsLookupMs, colors.methodGet),
        PhaseRow("TCP Connection", timing.tcpConnectMs, colors.methodPut),
        PhaseRow("TLS Handshake", timing.tlsHandshakeMs, colors.methodPost),
        PhaseRow("Request Transmission", timing.requestTransferMs, colors.methodPatch),
        PhaseRow("Server Response (TTFB)", timing.serverResponseMs, colors.statusSuccess),
        PhaseRow("Response Download", timing.responseDownloadMs, colors.accentCyan)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!timing.hasDetailedTiming) {
            InfoBanner(
                text = "Detailed socket timing is unavailable because ApiLens.eventListenerFactory() was not attached to this OkHttpClient (or use OkHttpClient.Builder.attachApiLens()).",
                isWarning = true
            )
        } else if (timing.dnsLookupMs == null && timing.tcpConnectMs == null) {
            InfoBanner(
                text = "DNS and TCP connect phases are unavailable for this call (likely reused from OkHttp's HTTP connection pool). Unavailable phases are never reported as 0 ms.",
                isWarning = false
            )
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
            border = BorderStroke(1.dp, colors.borderSubtle),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Network Waterfall",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Total: ${timing.totalDurationMs} ms",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = colors.accentCyan
                    )
                }

                phases.forEach { phase ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = phase.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                            if (phase.durationMs != null) {
                                Text(
                                    text = "${phase.durationMs} ms",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = colors.textPrimary
                                )
                            } else {
                                Text(
                                    text = "Unavailable",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    color = colors.textMuted,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(colors.surfaceElevated)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(colors.surfaceElevated)
                        ) {
                            if (phase.durationMs != null) {
                                val fraction = (phase.durationMs.toFloat() / totalMs.toFloat())
                                    .coerceIn(0.02f, 1f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction)
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(phase.color)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeadersCard(
    title: String,
    headers: List<ApiHeader>
) {
    val colors = LocalApiLensColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
        border = BorderStroke(1.dp, colors.borderSubtle),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            if (headers.isEmpty()) {
                Text("No headers", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
            } else {
                headers.forEachIndexed { index, header ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = header.name,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = colors.jsonKey
                            )
                            if (header.isRedacted) {
                                RedactedBadge()
                            }
                        }
                        SelectionContainer {
                            Text(
                                text = header.value,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp,
                                color = if (header.isRedacted) colors.redactedFg else colors.textSecondary
                            )
                        }
                    }
                    if (index < headers.lastIndex) {
                        HorizontalDivider(color = colors.borderSubtle.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyValueCard(
    title: String,
    entries: List<Pair<String, String>>
) {
    val colors = LocalApiLensColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
        border = BorderStroke(1.dp, colors.borderSubtle),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            entries.forEachIndexed { idx, (key, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = key,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        modifier = Modifier.weight(0.42f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SelectionContainer(modifier = Modifier.weight(0.58f)) {
                        Text(
                            text = value,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp,
                            color = colors.textPrimary
                        )
                    }
                }
                if (idx < entries.lastIndex) {
                    HorizontalDivider(color = colors.borderSubtle.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderRow(
    title: String,
    trailing: @Composable () -> Unit
) {
    val colors = LocalApiLensColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        trailing()
    }
}

@Composable
private fun InfoBanner(text: String, isWarning: Boolean) {
    val colors = LocalApiLensColors.current
    val tint = if (isWarning) colors.slowWarning else colors.textSecondary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.1f))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (isWarning) colors.slowWarning else colors.textSecondary
        )
    }
}

@Composable
private fun SearchFieldCompact(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    trailingText: String? = null,
    testTag: String
) {
    val colors = LocalApiLensColors.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text(placeholder, fontSize = 13.sp, color = colors.textMuted) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "Search",
                tint = colors.textMuted,
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!trailingText.isNullOrEmpty()) {
                    Text(
                        text = trailingText,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = colors.accentCyan,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear search",
                            tint = colors.textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.accentCyan,
            unfocusedBorderColor = colors.borderSubtle,
            focusedContainerColor = colors.surfaceCard,
            unfocusedContainerColor = colors.surfaceCard,
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    )
}
