@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.aura.player

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Inter (SIL Open Font License 1.1), bundled as one variable font so every weight comes from a single file.
 * Variable-font axes need API 26+; minSdk is 29.
 */
val AuraFont = FontFamily(
    Font(R.font.inter, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.inter, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.inter, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.inter, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800)))
)

private fun TextStyle.inter() = copy(fontFamily = AuraFont)

val AuraTypography: Typography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.inter(),
        displayMedium = t.displayMedium.inter(),
        displaySmall = t.displaySmall.inter(),
        headlineLarge = t.headlineLarge.inter(),
        headlineMedium = t.headlineMedium.inter(),
        headlineSmall = t.headlineSmall.inter(),
        titleLarge = t.titleLarge.inter(),
        titleMedium = t.titleMedium.inter(),
        titleSmall = t.titleSmall.inter(),
        bodyLarge = t.bodyLarge.inter().copy(fontSize = 16.sp, letterSpacing = 0.sp),
        bodyMedium = t.bodyMedium.inter().copy(letterSpacing = 0.sp),
        bodySmall = t.bodySmall.inter(),
        labelLarge = t.labelLarge.inter(),
        labelMedium = t.labelMedium.inter(),
        labelSmall = t.labelSmall.inter()
    )
}
