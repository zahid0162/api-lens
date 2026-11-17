package com.apilens.presentation.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apilens.core.ApiTransaction
import com.apilens.presentation.components.HttpMethodBadge
import com.apilens.presentation.components.StatusIndicator
import com.apilens.presentation.theme.JetBrainsMonoFontFamily
import com.apilens.presentation.theme.LocalApiLensColors
import com.apilens.utils.RequestFormatter
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResponseComparisonSheet(
    left: ApiTransaction,
    right: ApiTransaction,
    onDismiss: () -> Unit
) {
    val colors = LocalApiLensColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val diffKeys = remember(left.id, right.id, left.response?.body, right.response?.body) {
        computeTopLevelJsonDiff(left.response?.body, right.response?.body)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceCard
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Response Comparison",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CompareHeaderTile(label = "Request A", tx = left, modifier = Modifier.weight(1f))
                CompareHeaderTile(label = "Request B", tx = right, modifier = Modifier.weight(1f))
            }

            val latencyDelta = right.timing.totalDurationMs - left.timing.totalDurationMs
            val sizeLeft = left.response?.sizeBytes ?: 0L
            val sizeRight = right.response?.sizeBytes ?: 0L
            val sizeDelta = sizeRight - sizeLeft

            Card(
                colors = CardDefaults.cardColors(containerColor = colors.canvas),
                border = BorderStroke(1.dp, colors.borderSubtle),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Metrics Delta (B vs A)", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    CompareMetricRow(
                        label = "Duration",
                        leftValue = "${left.timing.totalDurationMs} ms",
                        rightValue = "${right.timing.totalDurationMs} ms",
                        delta = "${if (latencyDelta >= 0) "+" else ""}$latencyDelta ms"
                    )
                    CompareMetricRow(
                        label = "Response Size",
                        leftValue = RequestFormatter.formatBytes(sizeLeft),
                        rightValue = RequestFormatter.formatBytes(sizeRight),
                        delta = "${if (sizeDelta >= 0) "+" else ""}$sizeDelta B"
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = colors.canvas),
                border = BorderStroke(1.dp, colors.borderSubtle),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("JSON Payload Diff", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    if (diffKeys.isEmpty()) {
                        Text(
                            text = "No differing top-level JSON keys detected (or non-JSON payloads).",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    } else {
                        diffKeys.forEachIndexed { idx, entry ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = entry.key,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = colors.accentCyan
                                )
                                Text(
                                    text = "A: ${entry.leftValue}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    color = colors.statusError
                                )
                                Text(
                                    text = "B: ${entry.rightValue}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    color = colors.statusSuccess
                                )
                            }
                            if (idx < diffKeys.lastIndex) {
                                HorizontalDivider(color = colors.borderSubtle.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CompareHeaderTile(
    label: String,
    tx: ApiTransaction,
    modifier: Modifier = Modifier
) {
    val colors = LocalApiLensColors.current
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = colors.canvas),
        border = BorderStroke(1.dp, colors.borderSubtle),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.textMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HttpMethodBadge(method = tx.request.method)
                StatusIndicator(transaction = tx)
            }
            Text(
                text = tx.request.path,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 11.sp,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CompareMetricRow(
    label: String,
    leftValue: String,
    rightValue: String,
    delta: String
) {
    val colors = LocalApiLensColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        Text(
            text = "$leftValue  ->  $rightValue ($delta)",
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 11.sp,
            color = colors.textPrimary
        )
    }
}

private data class JsonKeyDiff(
    val key: String,
    val leftValue: String,
    val rightValue: String
)

private fun computeTopLevelJsonDiff(leftBody: String?, rightBody: String?): List<JsonKeyDiff> {
    if (leftBody.isNullOrBlank() || rightBody.isNullOrBlank()) return emptyList()
    return runCatching {
        val leftObj = JSONObject(leftBody)
        val rightObj = JSONObject(rightBody)
        val allKeys = (leftObj.keys().asSequence().toSet() + rightObj.keys().asSequence().toSet()).sorted()
        allKeys.mapNotNull { key ->
            val l = if (leftObj.has(key)) leftObj.opt(key)?.toString() ?: "null" else "<missing>"
            val r = if (rightObj.has(key)) rightObj.opt(key)?.toString() ?: "null" else "<missing>"
            if (l != r) {
                JsonKeyDiff(key = key, leftValue = l.take(120), rightValue = r.take(120))
            } else {
                null
            }
        }
    }.getOrDefault(emptyList())
}
