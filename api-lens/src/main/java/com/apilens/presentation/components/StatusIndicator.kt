package com.apilens.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apilens.core.ApiErrorType
import com.apilens.core.ApiTransaction
import com.apilens.presentation.theme.JetBrainsMonoFontFamily
import com.apilens.presentation.theme.LocalApiLensColors

@Composable
fun HttpMethodBadge(
    method: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalApiLensColors.current
    val tint = when (method.uppercase()) {
        "GET" -> colors.methodGet
        "POST" -> colors.methodPost
        "PUT" -> colors.methodPut
        "PATCH" -> colors.methodPatch
        "DELETE" -> colors.methodDelete
        else -> colors.textSecondary
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = 0.14f))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = method.uppercase(),
            color = tint,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
    }
}

@Composable
fun StatusIndicator(
    transaction: ApiTransaction,
    modifier: Modifier = Modifier
) {
    val colors = LocalApiLensColors.current
    val (label, tint) = when {
        transaction.isPending -> "RUN" to colors.statusPending
        transaction.response != null -> {
            val code = transaction.response.statusCode
            val c = when (code) {
                in 200..299 -> colors.statusSuccess
                in 300..399 -> colors.statusRedirect
                else -> colors.statusError
            }
            code.toString() to c
        }
        transaction.errorType != ApiErrorType.NONE -> {
            val short = when (transaction.errorType) {
                ApiErrorType.SOCKET_TIMEOUT, ApiErrorType.CONNECTION_TIMEOUT -> "TIME"
                ApiErrorType.UNKNOWN_HOST -> "DNS"
                ApiErrorType.SSL_ERROR -> "SSL"
                else -> "ERR"
            }
            short to colors.statusError
        }
        else -> "ERR" to colors.statusError
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(tint)
        )
        Text(
            text = label,
            color = tint,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
    }
}

@Composable
fun SlowApiBadge(
    modifier: Modifier = Modifier
) {
    val colors = LocalApiLensColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.slowWarning.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Bolt,
            contentDescription = "Slow request",
            tint = colors.slowWarning,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = "SLOW",
            color = colors.slowWarning,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}

@Composable
fun RedactedBadge(modifier: Modifier = Modifier) {
    val colors = LocalApiLensColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(colors.redactedBg)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = "Redacted",
            tint = colors.redactedFg,
            modifier = Modifier.size(10.dp)
        )
        Text(
            text = "REDACTED",
            color = colors.redactedFg,
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp
        )
    }
}
