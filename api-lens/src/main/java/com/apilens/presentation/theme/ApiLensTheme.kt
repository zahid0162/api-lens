package com.apilens.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.apilens.ApiLensTheme
import com.apilens.R

@Immutable
data class ApiLensColors(
    val isDark: Boolean,
    val canvas: Color,
    val surfaceCard: Color,
    val surfaceElevated: Color,
    val borderSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accentCyan: Color,
    val statusSuccess: Color,
    val statusRedirect: Color,
    val statusError: Color,
    val statusPending: Color,
    val slowWarning: Color,
    val methodGet: Color,
    val methodPost: Color,
    val methodPut: Color,
    val methodPatch: Color,
    val methodDelete: Color,
    val jsonKey: Color,
    val jsonString: Color,
    val jsonNumber: Color,
    val jsonBoolean: Color,
    val jsonNull: Color,
    val redactedBg: Color,
    val redactedFg: Color
)

private val DarkApiLensColors = ApiLensColors(
    isDark = true,
    canvas = Color(0xFF0B0F17),
    surfaceCard = Color(0xFF131A26),
    surfaceElevated = Color(0xFF1B2434),
    borderSubtle = Color(0xFF263248),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF94A3B8),
    textMuted = Color(0xFF64748B),
    accentCyan = Color(0xFF38BDF8),
    statusSuccess = Color(0xFF10B981),
    statusRedirect = Color(0xFFF59E0B),
    statusError = Color(0xFFEF4444),
    statusPending = Color(0xFF60A5FA),
    slowWarning = Color(0xFFF59E0B),
    methodGet = Color(0xFF38BDF8),
    methodPost = Color(0xFFA78BFA),
    methodPut = Color(0xFFFBBF24),
    methodPatch = Color(0xFF2DD4BF),
    methodDelete = Color(0xFFF87171),
    jsonKey = Color(0xFF7DD3FC),
    jsonString = Color(0xFF6EE7B7),
    jsonNumber = Color(0xFFFCD34D),
    jsonBoolean = Color(0xFFC4B5FD),
    jsonNull = Color(0xFFFCA5A5),
    redactedBg = Color(0xFF3B1D2A),
    redactedFg = Color(0xFFFB7185)
)

private val LightApiLensColors = ApiLensColors(
    isDark = false,
    canvas = Color(0xFFF8FAFC),
    surfaceCard = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFF1F5F9),
    borderSubtle = Color(0xFFE2E8F0),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    textMuted = Color(0xFF64748B),
    accentCyan = Color(0xFF0284C7),
    statusSuccess = Color(0xFF059669),
    statusRedirect = Color(0xFFD97706),
    statusError = Color(0xFFDC2626),
    statusPending = Color(0xFF2563EB),
    slowWarning = Color(0xFFD97706),
    methodGet = Color(0xFF0284C7),
    methodPost = Color(0xFF7C3AED),
    methodPut = Color(0xFFD97706),
    methodPatch = Color(0xFF0D9488),
    methodDelete = Color(0xFFDC2626),
    jsonKey = Color(0xFF0369A1),
    jsonString = Color(0xFF047857),
    jsonNumber = Color(0xFFB45309),
    jsonBoolean = Color(0xFF6D28D9),
    jsonNull = Color(0xFFB91C1C),
    redactedBg = Color(0xFFFFE4E6),
    redactedFg = Color(0xFFBE123C)
)

val LocalApiLensColors = staticCompositionLocalOf { DarkApiLensColors }

val SpaceGroteskFontFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.SemiBold),
    Font(R.font.space_grotesk, FontWeight.Bold)
)

val InterFontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold),
    Font(R.font.inter, FontWeight.Bold)
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

private val ApiLensTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = SpaceGroteskFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
)

@Composable
fun ApiLensMaterialTheme(
    themeMode: ApiLensTheme,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ApiLensTheme.DARK -> true
        ApiLensTheme.LIGHT -> false
        ApiLensTheme.SYSTEM -> isSystemInDarkTheme()
    }
    val lensColors = if (isDark) DarkApiLensColors else LightApiLensColors
    val m3Scheme = if (isDark) {
        darkColorScheme(
            primary = lensColors.accentCyan,
            onPrimary = Color(0xFF041E2B),
            secondary = lensColors.methodPost,
            background = lensColors.canvas,
            onBackground = lensColors.textPrimary,
            surface = lensColors.surfaceCard,
            onSurface = lensColors.textPrimary,
            surfaceVariant = lensColors.surfaceElevated,
            onSurfaceVariant = lensColors.textSecondary,
            outline = lensColors.borderSubtle,
            error = lensColors.statusError
        )
    } else {
        lightColorScheme(
            primary = lensColors.accentCyan,
            onPrimary = Color.White,
            secondary = lensColors.methodPost,
            background = lensColors.canvas,
            onBackground = lensColors.textPrimary,
            surface = lensColors.surfaceCard,
            onSurface = lensColors.textPrimary,
            surfaceVariant = lensColors.surfaceElevated,
            onSurfaceVariant = lensColors.textSecondary,
            outline = lensColors.borderSubtle,
            error = lensColors.statusError
        )
    }

    CompositionLocalProvider(LocalApiLensColors provides lensColors) {
        MaterialTheme(
            colorScheme = m3Scheme,
            typography = ApiLensTypography,
            content = content
        )
    }
}
