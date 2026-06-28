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

// ==========================================
// DARK THEMES
// ==========================================

// Cosmic Charcoal (Standard Dark)
private val CosmicCharcoalScheme = darkColorScheme(
    primary = Color(0xFF80DEEA), // Soft Cyan
    onPrimary = Color(0xFF00363A),
    primaryContainer = Color(0xFF004D40),
    onPrimaryContainer = Color(0xFFE0F2F1),
    secondary = Color(0xFFB0BEC5), // Cool Slate Gray
    onSecondary = Color(0xFF1B2A30),
    tertiary = Color(0xFFEF5350), // Coral Accent
    onTertiary = Color(0xFF4B0000),
    background = Color(0xFF121212),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF1E1E1E),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF2C2C2C),
    onSurfaceVariant = Color(0xFFE0E0E0),
    outline = Color(0xFF757575)
)

// Midnight Indigo
private val MidnightIndigoScheme = darkColorScheme(
    primary = Color(0xFF38BDF8), // Electric Sky Blue
    onPrimary = Color(0xFF00354E),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = Color(0xFFC084FC), // Lavender Purple
    onSecondary = Color(0xFF3B0764),
    tertiary = Color(0xFFF472B6), // Neon Pink
    onTertiary = Color(0xFF4C0519),
    background = Color(0xFF0B0C1E),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF131535),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF1E214A),
    onSurfaceVariant = Color(0xFFE2E8F0),
    outline = Color(0xFF64748B)
)

// Forest Emerald
private val ForestEmeraldScheme = darkColorScheme(
    primary = Color(0xFF34D399), // Emerald Green
    onPrimary = Color(0xFF064E3B),
    primaryContainer = Color(0xFF065F46),
    onPrimaryContainer = Color(0xFFD1FAE5),
    secondary = Color(0xFFA7F3D0), // Soft Sage
    onSecondary = Color(0xFF022C22),
    tertiary = Color(0xFF6EE7B7), // Mint
    onTertiary = Color(0xFF044E35),
    background = Color(0xFF06140E),
    onBackground = Color(0xFFECFDF5),
    surface = Color(0xFF0F261C),
    onSurface = Color(0xFFECFDF5),
    surfaceVariant = Color(0xFF183B2C),
    onSurfaceVariant = Color(0xFFD1FAE5),
    outline = Color(0xFF34D399)
)

// Sunset Obsidian
private val SunsetObsidianScheme = darkColorScheme(
    primary = Color(0xFFF59E0B), // Vibrant Amber
    onPrimary = Color(0xFF451A03),
    primaryContainer = Color(0xFF78350F),
    onPrimaryContainer = Color(0xFFFEF3C7),
    secondary = Color(0xFFFB7185), // Sunset Rose
    onSecondary = Color(0xFF4C0519),
    tertiary = Color(0xFFFDA4AF), // Soft Coral
    onTertiary = Color(0xFF4C0519),
    background = Color(0xFF110507),
    onBackground = Color(0xFFFFF1F2),
    surface = Color(0xFF1E0A0E),
    onSurface = Color(0xFFFFF1F2),
    surfaceVariant = Color(0xFF2D1217),
    onSurfaceVariant = Color(0xFFFFE4E6),
    outline = Color(0xFFF43F5E)
)

// ==========================================
// LIGHT THEMES
// ==========================================

// Snowy Lavender
private val SnowyLavenderScheme = lightColorScheme(
    primary = Color(0xFF7C3AED), // Lavender Violet
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF5F3FA),
    onPrimaryContainer = Color(0xFF4C1D95),
    secondary = Color(0xFF9333EA), // Deep Amethyst
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFFC084FC), // Orchid Accent
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFCFBFF),
    onBackground = Color(0xFF1A1523),
    surface = Color(0xFFF6F3FB),
    onSurface = Color(0xFF1A1523),
    surfaceVariant = Color(0xFFECE9F4),
    onSurfaceVariant = Color(0xFF3A3544),
    outline = Color(0xFF7C3AED)
)

// Mint Cream
private val MintCreamScheme = lightColorScheme(
    primary = Color(0xFF0D9488), // Ocean Teal
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6F4EA),
    onPrimaryContainer = Color(0xFF115E59),
    secondary = Color(0xFF14B8A6), // Pale Teal
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF059669), // Emerald
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF4FAF7),
    onBackground = Color(0xFF0A1813),
    surface = Color(0xFFECFDF5),
    onSurface = Color(0xFF0A1813),
    surfaceVariant = Color(0xFFD1FAE5),
    onSurfaceVariant = Color(0xFF1E3F31),
    outline = Color(0xFF0D9488)
)

// Peach Blossom
private val PeachBlossomScheme = lightColorScheme(
    primary = Color(0xFFE11D48), // Rose Pink
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFF1F2),
    onPrimaryContainer = Color(0xFF881337),
    secondary = Color(0xFFF43F5E), // Coral Peach
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFFFB7185), // Soft Peach
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFDFD),
    onBackground = Color(0xFF20090C),
    surface = Color(0xFFFFF1F2),
    onSurface = Color(0xFF20090C),
    surfaceVariant = Color(0xFFFEE2E2),
    onSurfaceVariant = Color(0xFF4A1F21),
    outline = Color(0xFFE11D48)
)

// Ocean Breeze
private val OceanBreezeScheme = lightColorScheme(
    primary = Color(0xFF0284C7), // Sky Blue
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = Color(0xFF0EA5E9), // Marine Blue
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF38BDF8), // Cyan
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF3FAFF),
    onBackground = Color(0xFF051522),
    surface = Color(0xFFE0F2FE),
    onSurface = Color(0xFF051522),
    surfaceVariant = Color(0xFFBAE6FD),
    onSurfaceVariant = Color(0xFF1E3E53),
    outline = Color(0xFF0284C7)
)

@Composable
fun MyApplicationTheme(
    colorTheme: String = "dark_charcoal",
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (colorTheme) {
        // Dark themes
        "dark_charcoal" -> CosmicCharcoalScheme
        "dark_indigo" -> MidnightIndigoScheme
        "dark_emerald" -> ForestEmeraldScheme
        "dark_obsidian" -> SunsetObsidianScheme

        // Light themes
        "light_lavender" -> SnowyLavenderScheme
        "light_mint" -> MintCreamScheme
        "light_peach" -> PeachBlossomScheme
        "light_ocean" -> OceanBreezeScheme

        // Fallback or dynamic
        else -> {
            if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val context = LocalContext.current
                if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (isSystemInDarkTheme()) CosmicCharcoalScheme else SnowyLavenderScheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
