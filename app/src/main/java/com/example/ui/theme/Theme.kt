package com.example.ui.theme

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

private val DarkColorScheme = darkColorScheme(
    primary = VivvyoTealLight,
    onPrimary = VivvyoSlate900,
    primaryContainer = VivvyoTealDark,
    onPrimaryContainer = Color.White,
    secondary = VivvyoEmeraldLight,
    onSecondary = VivvyoSlate900,
    secondaryContainer = VivvyoEmeraldDark,
    onSecondaryContainer = Color.White,
    tertiary = VivvyoAmber,
    background = Color(0xFF090D16),
    surface = VivvyoSlate900,
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = VivvyoSlate800,
    onSurfaceVariant = Color(0xFFCBD5E1),
    outline = VivvyoSlate700
)

private val LightColorScheme = lightColorScheme(
    primary = VivvyoTeal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = VivvyoTealDark,
    secondary = VivvyoEmerald,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = VivvyoEmeraldDark,
    tertiary = VivvyoAmber,
    background = VivvyoSlate50,
    surface = Color.White,
    onBackground = VivvyoSlate900,
    onSurface = VivvyoSlate900,
    surfaceVariant = VivvyoSlate100,
    onSurfaceVariant = Color(0xFF475569),
    outline = VivvyoSlate200
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use our brand colors for consistent polish
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
