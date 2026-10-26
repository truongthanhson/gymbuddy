package com.gymbuddy.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// "Ember" palette, shared with iOS (ios/GymBuddy/Theme/Palette.swift): a warm orange accent on warm neutrals.

val Ember = Color(0xFFF2542D)
val EmberDark = Color(0xFFFF7A52)
val Teal = Color(0xFF0E9F8E)
val TealDark = Color(0xFF4FD1C0)
val Indigo = Color(0xFF5B5FE0)
val IndigoDark = Color(0xFF9EA2FF)
val Amber = Color(0xFFE8930C)
val AmberDark = Color(0xFFFFC14D)
val Pink = Color(0xFFE0457B)
val PinkDark = Color(0xFFFF7EA8)

internal val LightColors = lightColorScheme(
    primary = Ember,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3D9),
    onPrimaryContainer = Color(0xFF5A1A08),
    inversePrimary = EmberDark,
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2F3EE),
    onSecondaryContainer = Color(0xFF053F38),
    tertiary = Indigo,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE3E4FF),
    onTertiaryContainer = Color(0xFF1C1E6B),
    background = Color(0xFFF6F5F2),
    onBackground = Color(0xFF18171C),
    surface = Color(0xFFF6F5F2),
    onSurface = Color(0xFF18171C),
    surfaceVariant = Color(0xFFECE9E4),
    onSurfaceVariant = Color(0xFF6A6872),
    surfaceTint = Ember,
    inverseSurface = Color(0xFF2B2A30),
    inverseOnSurface = Color(0xFFF2F1EE),
    error = Color(0xFFD93636),
    onError = Color.White,
    errorContainer = Color(0xFFFFE1DE),
    onErrorContainer = Color(0xFF5C0A0A),
    outline = Color(0xFFC9C6C0),
    outlineVariant = Color(0xFFE5E2DC),
    scrim = Color.Black,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE4E1DB),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF0EEEA),
    surfaceContainerHigh = Color(0xFFEAE8E3),
    surfaceContainerHighest = Color(0xFFE4E1DB),
)

internal val DarkColors = darkColorScheme(
    primary = EmberDark,
    onPrimary = Color(0xFF3A0E00),
    primaryContainer = Color(0xFF5A2412),
    onPrimaryContainer = Color(0xFFFFDBCF),
    inversePrimary = Ember,
    secondary = TealDark,
    onSecondary = Color(0xFF00332D),
    secondaryContainer = Color(0xFF0F3F39),
    onSecondaryContainer = Color(0xFFB8F1E8),
    tertiary = IndigoDark,
    onTertiary = Color(0xFF151760),
    tertiaryContainer = Color(0xFF2B2E7A),
    onTertiaryContainer = Color(0xFFE3E4FF),
    background = Color(0xFF0F0F12),
    onBackground = Color(0xFFF2F1EE),
    surface = Color(0xFF0F0F12),
    onSurface = Color(0xFFF2F1EE),
    surfaceVariant = Color(0xFF24242A),
    onSurfaceVariant = Color(0xFFA3A1AA),
    surfaceTint = EmberDark,
    inverseSurface = Color(0xFFF2F1EE),
    inverseOnSurface = Color(0xFF18171C),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF450000),
    errorContainer = Color(0xFF5C1414),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF4A4851),
    outlineVariant = Color(0xFF2E2D33),
    scrim = Color.Black,
    surfaceBright = Color(0xFF2C2C33),
    surfaceDim = Color(0xFF0F0F12),
    surfaceContainerLowest = Color(0xFF0A0A0C),
    surfaceContainerLow = Color(0xFF17171B),
    surfaceContainer = Color(0xFF1C1C21),
    surfaceContainerHigh = Color(0xFF24242A),
    surfaceContainerHighest = Color(0xFF2C2C33),
)

/** Colors Material's scheme has no role for: completed sets, PRs and chart series. */
@Immutable
data class ExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    /** Personal records and Pro highlights. */
    val gold: Color,
    /** Series in order: primary, teal, indigo, amber, pink. */
    val chartSeries: List<Color>,
    val chartGrid: Color,
    val chartLabel: Color,
)

internal val LightExtendedColors = ExtendedColors(
    success = Color(0xFF1E9E5A),
    onSuccess = Color.White,
    successContainer = Color(0xFFD9F5E5),
    onSuccessContainer = Color(0xFF0A3D22),
    gold = Amber,
    chartSeries = listOf(Ember, Teal, Indigo, Amber, Pink),
    chartGrid = LightColors.outlineVariant,
    chartLabel = LightColors.onSurfaceVariant,
)

internal val DarkExtendedColors = ExtendedColors(
    success = Color(0xFF3DD68C),
    onSuccess = Color(0xFF00391C),
    successContainer = Color(0xFF103B26),
    onSuccessContainer = Color(0xFFC4F5D9),
    gold = AmberDark,
    chartSeries = listOf(EmberDark, TealDark, IndigoDark, AmberDark, PinkDark),
    chartGrid = DarkColors.outlineVariant,
    chartLabel = DarkColors.onSurfaceVariant,
)
