package com.example.calculators.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// Strict Light Theme Only as specified in Phase 1 requirements
private val LightBillingColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = OnPrimaryWhite,
    primaryContainer = PrimaryContainerBlue,
    onPrimaryContainer = OnPrimaryContainerBlue,
    secondary = SecondarySlate,
    onSecondary = OnSecondaryWhite,
    secondaryContainer = SecondaryContainerSlate,
    onSecondaryContainer = OnSecondaryContainerSlate,
    tertiary = TertiaryTeal,
    onTertiary = OnTertiaryWhite,
    tertiaryContainer = TertiaryContainerTeal,
    onTertiaryContainer = OnTertiaryContainerTeal,
    background = BackgroundLight,
    onBackground = OnBackgroundSlate,
    surface = SurfaceWhite,
    onSurface = OnSurfaceSlate,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantSlate,
    outline = OutlineBorder,
    outlineVariant = OutlineVariantDivider,
    error = ErrorRed,
    onError = OnErrorWhite,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerRed
)

val CalculatorsShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun CalculatorsTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightBillingColorScheme,
        typography = CalculatorsTypography,
        shapes = CalculatorsShapes,
        content = content
    )
}
