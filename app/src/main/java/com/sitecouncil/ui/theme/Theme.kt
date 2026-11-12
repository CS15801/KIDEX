package com.sitecouncil.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF1B3A5C),
    onPrimary = Color.White,
    secondary = Color(0xFF8A5A00),
    tertiary = Color(0xFF00695C),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC4F2),
    onPrimary = Color(0xFF0A2540),
    secondary = Color(0xFFF5C063),
    tertiary = Color(0xFF7FD6C8),
)

val ScoreGood = Color(0xFF2E7D32)
val ScoreMedium = Color(0xFFF9A825)
val ScoreBad = Color(0xFFC62828)

fun scoreColor(score: Int): Color = when {
    score >= 75 -> ScoreGood
    score >= 50 -> ScoreMedium
    else -> ScoreBad
}

@Composable
fun SiteCouncilTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
