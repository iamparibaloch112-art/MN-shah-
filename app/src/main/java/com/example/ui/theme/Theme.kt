package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val MnShahDarkColorScheme = darkColorScheme(
    primary = NeonYellow,
    onPrimary = ObsidianVoid,
    primaryContainer = NeonYellowSoft,
    onPrimaryContainer = NeonYellow,
    secondary = CyberCyan,
    onSecondary = ObsidianVoid,
    secondaryContainer = CyberPanel,
    onSecondaryContainer = CyberCyan,
    tertiary = ElectricAmber,
    onTertiary = ObsidianVoid,
    background = ObsidianVoid,
    onBackground = TextPrimary,
    surface = CarbonSurface,
    onSurface = TextPrimary,
    surfaceVariant = CyberPanel,
    onSurfaceVariant = TextSecondary,
    outline = SlateBorder,
    outlineVariant = CyberElevated,
    error = BearishCrimson,
    onError = TextPrimary
)

val MnShahShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MnShahTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MnShahDarkColorScheme,
        typography = MnShahTypography,
        shapes = MnShahShapes,
        content = content
    )
}
