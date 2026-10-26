package com.gymbuddy.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// System font with bolder, tighter headings; numbers line up thanks to tabular figures.
private const val TABULAR = "tnum"

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.em,
    fontFeatureSettings = TABULAR,
)

internal val GymBuddyTypography = Typography(
    displayLarge = style(52, 60, FontWeight.Bold, -0.02),
    displayMedium = style(42, 50, FontWeight.Bold, -0.02),
    displaySmall = style(34, 40, FontWeight.Bold, -0.02),
    headlineLarge = style(30, 36, FontWeight.Bold, -0.015),
    headlineMedium = style(26, 32, FontWeight.Bold, -0.01),
    headlineSmall = style(22, 28, FontWeight.Bold, -0.01),
    titleLarge = style(20, 26, FontWeight.SemiBold, -0.005),
    titleMedium = style(16, 22, FontWeight.SemiBold),
    titleSmall = style(14, 20, FontWeight.SemiBold),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    bodySmall = style(12, 16, FontWeight.Normal, 0.005),
    labelLarge = style(14, 20, FontWeight.SemiBold),
    labelMedium = style(12, 16, FontWeight.Medium, 0.01),
    labelSmall = style(11, 14, FontWeight.Medium, 0.02),
)
