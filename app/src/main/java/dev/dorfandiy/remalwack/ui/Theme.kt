package dev.dorfandiy.remalwack.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** AdAway-style palette: red accent on a near-black surface. */
private val RedDark = darkColorScheme(
    primary = Color(0xFFFF5449), onPrimary = Color(0xFF410002),
    primaryContainer = Color(0xFF93000A), onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFE7BDB8), onSecondary = Color(0xFF442926),
    secondaryContainer = Color(0xFF5D3F3B), onSecondaryContainer = Color(0xFFFFDAD6),
    tertiary = Color(0xFFE5C18D), onTertiary = Color(0xFF422C05),
    tertiaryContainer = Color(0xFF5B4219), onTertiaryContainer = Color(0xFFFFDEA9),
    background = Color(0xFF0F0D0D), onBackground = Color(0xFFECE0DE),
    surface = Color(0xFF0F0D0D), onSurface = Color(0xFFECE0DE),
    surfaceVariant = Color(0xFF534341), onSurfaceVariant = Color(0xFFD8C2BF),
    surfaceContainerLowest = Color(0xFF0A0808), surfaceContainerLow = Color(0xFF181414),
    surfaceContainer = Color(0xFF1D1818), surfaceContainerHigh = Color(0xFF282222),
    surfaceContainerHighest = Color(0xFF332D2C),
    outline = Color(0xFFA08C8A), outlineVariant = Color(0xFF534341),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
)

private val RedLight = lightColorScheme(
    primary = Color(0xFFC00014), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6), onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF775652), secondaryContainer = Color(0xFFFFDAD6), onSecondaryContainer = Color(0xFF2C1512),
    tertiaryContainer = Color(0xFFFFDEA9), onTertiaryContainer = Color(0xFF281900),
    background = Color(0xFFFFF8F7), surface = Color(0xFFFFF8F7),
    surfaceContainer = Color(0xFFFCEAE8), surfaceContainerHigh = Color(0xFFF6E4E2), surfaceContainerHighest = Color(0xFFF0DEDC),
    outline = Color(0xFF857370),
)

/** theme: 0 = dark, 1 = follow system, 2 = light. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(theme: Int, content: @Composable () -> Unit) {
    val dark = when (theme) { 0 -> true; 2 -> false; else -> isSystemInDarkTheme() }
    MaterialExpressiveTheme(
        colorScheme = if (dark) RedDark else RedLight,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
