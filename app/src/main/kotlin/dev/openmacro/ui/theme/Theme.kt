package dev.openmacro.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** The three rule-element colours: triggers red, actions blue, constraints green. */
@Immutable
data class SectionPalette(val trigger: Color, val action: Color, val constraint: Color)

private val LightSections = SectionPalette(
    trigger = Color(0xFFC62828),
    action = Color(0xFF1565C0),
    constraint = Color(0xFF2E7D32),
)

private val DarkSections = SectionPalette(
    trigger = Color(0xFFEF5350),
    action = Color(0xFF64B5F6),
    constraint = Color(0xFF81C784),
)

val LocalSectionPalette = staticCompositionLocalOf { LightSections }

@Composable
fun OpenMacroTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    CompositionLocalProvider(LocalSectionPalette provides if (darkTheme) DarkSections else LightSections) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}
