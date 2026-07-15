package top.lvziwang.ntfyhub.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import top.lvziwang.ntfyhub.model.ThemePreset

private val OceanLightColors = lightColorScheme()
private val OceanDarkColors = darkColorScheme()
private val SunsetLightColors = lightColorScheme(
    primary = Color(0xFF9A3412),
    secondary = Color(0xFFB45309),
    tertiary = Color(0xFFBE185D),
    background = Color(0xFFFFF7ED),
    surface = Color(0xFFFFFBF7)
)
private val SunsetDarkColors = darkColorScheme(
    primary = Color(0xFFF97316),
    secondary = Color(0xFFF59E0B),
    tertiary = Color(0xFFF472B6),
    background = Color(0xFF1C1917),
    surface = Color(0xFF292524)
)
private val ForestLightColors = lightColorScheme(
    primary = Color(0xFF166534),
    secondary = Color(0xFF15803D),
    tertiary = Color(0xFF0F766E),
    background = Color(0xFFF6FFF8),
    surface = Color(0xFFF8FFFA)
)
private val ForestDarkColors = darkColorScheme(
    primary = Color(0xFF4ADE80),
    secondary = Color(0xFF34D399),
    tertiary = Color(0xFF2DD4BF),
    background = Color(0xFF0B1410),
    surface = Color(0xFF13201A)
)

@Composable
fun HubTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    themePreset: ThemePreset = ThemePreset.OCEAN,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        themePreset == ThemePreset.SUNSET && darkTheme -> SunsetDarkColors
        themePreset == ThemePreset.SUNSET -> SunsetLightColors
        themePreset == ThemePreset.FOREST && darkTheme -> ForestDarkColors
        themePreset == ThemePreset.FOREST -> ForestLightColors
        darkTheme -> OceanDarkColors
        else -> OceanLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
