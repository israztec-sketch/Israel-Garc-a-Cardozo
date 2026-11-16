package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class ArgonSemanticColors(
    val cockpitBg: Color,
    val panelSurface: Color,
    val panelElevated: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val safeGreen: Color,
    val dangerRed: Color,
    val warningAmber: Color,
    val tollPurple: Color,
    val borderSubtle: Color,
    val isNightMode: Boolean
)

val LocalArgonColors = staticCompositionLocalOf {
    ArgonSemanticColors(
        cockpitBg = ObsidianAsphalt,
        panelSurface = CarbonSlate,
        panelElevated = SteelPanel,
        accentPrimary = SafetyAmber,
        accentSecondary = HighwayCyan,
        safeGreen = ClearanceEmerald,
        dangerRed = RestrictionCrimson,
        warningAmber = CautionGold,
        tollPurple = TollViolet,
        borderSubtle = SlateBorder,
        isNightMode = false
    )
}

private val CockpitDarkScheme = darkColorScheme(
    primary = SafetyAmber,
    onPrimary = ObsidianAsphalt,
    primaryContainer = SteelPanel,
    onPrimaryContainer = SafetyAmber,
    secondary = HighwayCyan,
    onSecondary = ObsidianAsphalt,
    secondaryContainer = Color(0xFF0E2A38),
    onSecondaryContainer = HighwayCyan,
    tertiary = ClearanceEmerald,
    onTertiary = ObsidianAsphalt,
    background = ObsidianAsphalt,
    onBackground = Color(0xFFF1F5F9),
    surface = CarbonSlate,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = SteelPanel,
    onSurfaceVariant = Color(0xFF94A3B8),
    error = RestrictionCrimson,
    onError = Color.White,
    outline = SlateBorder
)

private val NightDrivingScheme = darkColorScheme(
    primary = NightAmber,
    onPrimary = NightVoid,
    primaryContainer = Color(0xFF1F1608),
    onPrimaryContainer = NightText,
    secondary = Color(0xFFD97706),
    onSecondary = NightVoid,
    tertiary = ClearanceEmerald,
    background = NightVoid,
    onBackground = NightText,
    surface = NightCarbon,
    onSurface = NightText,
    surfaceVariant = Color(0xFF171A21),
    onSurfaceVariant = Color(0xFFC29B61),
    error = RestrictionCrimson,
    onError = Color.White,
    outline = Color(0xFF2B2318)
)

private val DaylightCabScheme = lightColorScheme(
    primary = SafetyAmberDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFF3DC),
    onPrimaryContainer = Color(0xFF78350F),
    secondary = HighwayCyanMuted,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF075985),
    tertiary = ClearanceEmerald,
    onTertiary = Color.White,
    background = DayRoadSurface,
    onBackground = DayInk,
    surface = DayPanelWhite,
    onSurface = DayInk,
    surfaceVariant = DayPanelMuted,
    onSurfaceVariant = DayInkMuted,
    error = RestrictionCrimson,
    onError = Color.White,
    outline = Color(0xFFCBD5E1)
)

@Composable
fun ArgonTruckTheme(
    nightMode: Boolean = true,
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        nightMode -> NightDrivingScheme
        darkTheme -> CockpitDarkScheme
        else -> DaylightCabScheme
    }

    val semanticColors = when {
        nightMode -> ArgonSemanticColors(
            cockpitBg = NightVoid,
            panelSurface = NightCarbon,
            panelElevated = Color(0xFF171A21),
            accentPrimary = NightAmber,
            accentSecondary = Color(0xFFEAB308),
            safeGreen = ClearanceEmerald,
            dangerRed = RestrictionCrimson,
            warningAmber = CautionGold,
            tollPurple = TollViolet,
            borderSubtle = Color(0xFF2B2318),
            isNightMode = true
        )
        darkTheme -> ArgonSemanticColors(
            cockpitBg = ObsidianAsphalt,
            panelSurface = CarbonSlate,
            panelElevated = SteelPanel,
            accentPrimary = SafetyAmber,
            accentSecondary = HighwayCyan,
            safeGreen = ClearanceEmerald,
            dangerRed = RestrictionCrimson,
            warningAmber = CautionGold,
            tollPurple = TollViolet,
            borderSubtle = SlateBorder,
            isNightMode = false
        )
        else -> ArgonSemanticColors(
            cockpitBg = DayRoadSurface,
            panelSurface = DayPanelWhite,
            panelElevated = DayPanelMuted,
            accentPrimary = SafetyAmberDark,
            accentSecondary = HighwayCyanMuted,
            safeGreen = ClearanceEmerald,
            dangerRed = RestrictionCrimson,
            warningAmber = CautionGold,
            tollPurple = TollViolet,
            borderSubtle = Color(0xFFCBD5E1),
            isNightMode = false
        )
    }

    CompositionLocalProvider(LocalArgonColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
