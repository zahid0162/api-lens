package com.apilens.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apilens.core.ApiTransaction
import com.apilens.presentation.theme.JetBrainsMonoFontFamily
import com.apilens.presentation.theme.LocalApiLensColors
import com.apilens.utils.RequestFormatter

@Composable
fun RequestCard(
    transaction: ApiTransaction,
    slowThresholdMs: Long,
    isSelected: Boolean,
    isMarkedForCompare: Boolean,
    onClick: () -> Unit,
    onToggleCompare: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalApiLensColors.current
    val isSlow = !transaction.isPending && transaction.timing.totalDurationMs >= slowThresholdMs

    val borderColor = when {
        isMarkedForCompare -> colors.methodPost
        isSelected -> colors.accentCyan
        transaction.isFailure -> colors.statusError.copy(alpha = 0.35f)
        else -> colors.borderSubtle
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("request_card_${transaction.id}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) colors.surfaceElevated else colors.surfaceCard
        ),
        border = BorderStroke(if (isSelected || isMarkedForCompare) 1.5.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HttpMethodBadge(method = transaction.request.method)
                Text(
                    text = transaction.request.path,
                    color = colors.textPrimary,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                StatusIndicator(transaction = transaction)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val indicatorIcon = when {
                    transaction.isPending -> Icons.Rounded.HourglassTop
                    transaction.isSuccess -> Icons.Rounded.CheckCircle
                    else -> Icons.Rounded.ErrorOutline
                }
                val indicatorColor = when {
                    transaction.isPending -> colors.statusPending
                    transaction.isSuccess -> colors.statusSuccess
                    else -> colors.statusError
                }
                Icon(
                    imageVector = indicatorIcon,
                    contentDescription = if (transaction.isSuccess) "Succeeded" else "Failed",
                    tint = indicatorColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = transaction.request.host,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (isSlow) {
                    SlowApiBadge()
                    Spacer(modifier = Modifier.width(6.dp))
                }

                Text(
                    text = if (transaction.isPending) "..." else "${transaction.timing.totalDurationMs} ms",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSlow) colors.slowWarning else colors.textPrimary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = RequestFormatter.formatShortTime(transaction.request.timestampMs),
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    color = colors.textMuted
                )

                IconButton(
                    onClick = onToggleCompare,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(32.dp)
                        .testTag("compare_toggle_${transaction.id}")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.CompareArrows,
                        contentDescription = "Select for comparison",
                        tint = if (isMarkedForCompare) colors.methodPost else colors.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            if (transaction.isFailure && !transaction.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${transaction.errorType.label}: ${transaction.errorMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.statusError,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
