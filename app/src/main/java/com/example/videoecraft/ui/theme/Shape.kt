package com.example.videoecraft.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Formas "Craft": esquinas generosas y consistentes.
 * Las tarjetas de proyecto y los clips de la timeline usan [Shapes.medium]
 * y [Shapes.large] para mantener el lenguaje visual unificado.
 */
val CraftShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
