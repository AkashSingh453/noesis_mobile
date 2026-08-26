package com.noesis.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Noesis Brand Palette — Glacial & Granite
val GlacialBlue = Color(0xFF598392)
val WeatheredGranite = Color(0xFF7A8B99)
val BgLight = Color(0xFFF7F7F4)
val BgDark = Color(0xFF1C1D1C)
val SurfaceLight = Color(0xFFEAEBE6)
val SurfaceDark = Color(0xFF2A2C2B)
val CopperBrown = Color(0xFFA0735E)
val BadgeBg = Color(0xFFE5E7EB) // Or GlacialBlue with opacity, let's use a soft gray or primary color. The screenshot shows a pill with blue/gray. We'll define it inline or here.
val AvatarBadge = GlacialBlue

// Keep existing vars for compatibility where used specifically, or alias them
val NoesisPurple = GlacialBlue
val NoesisPurpleLight = GlacialBlue.copy(alpha = 0.8f)
val NoesisPurpleDark = GlacialBlue
val NoesisBlue = WeatheredGranite
val NoesisTeal = GlacialBlue
// Map aliases to LIGHT mode colors to force light mode across the app
val NoesisBg = BgLight
val NoesisSurface = Color.White
val NoesisSurface2 = SurfaceLight
val NoesisBorder = Color(0xFFE5E7EB)
val NoesisText = Color(0xFF111111)
val NoesisTextMuted = Color(0xFF6B7280)

private val DarkColorScheme = darkColorScheme(
    primary = GlacialBlue,
    onPrimary = Color.White,
    primaryContainer = GlacialBlue,
    secondary = WeatheredGranite,
    onSecondary = Color.White,
    tertiary = GlacialBlue,
    background = BgDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceDark,
    onBackground = Color(0xFFE2E8F0),
    onSurface = Color(0xFFE2E8F0),
    outline = NoesisBorder,
    error = Color(0xFFF87171),
)

private val LightColorScheme = lightColorScheme(
    primary = GlacialBlue,
    onPrimary = Color.White,
    primaryContainer = GlacialBlue,
    secondary = WeatheredGranite,
    onSecondary = Color.White,
    tertiary = GlacialBlue,
    background = BgLight,
    surface = SurfaceLight,
    surfaceVariant = SurfaceLight,
    onBackground = Color(0xFF111111),
    onSurface = Color(0xFF111111),
    outline = Color(0xFFCCCCCC),
    error = Color(0xFFB3261E),
)

@Composable
fun NoesisTheme(
    darkTheme: Boolean = false, // Force light mode
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
