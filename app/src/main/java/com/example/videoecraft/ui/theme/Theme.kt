package com.example.videoecraft.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Esquema de color "Craft": dark-only por decisión de producto.
 *
 * No se usa color dinámico (Material You) a propósito: la identidad de
 * VideoEcraft depende de la paleta naranja neón → coral y de las superficies
 * `#101014` / `#1C1C24`. Permitir el color dinámico rompería la lectura de
 * los degradados de la línea de tiempo y de los indicadores de pista.
 */
private val CraftDarkColorScheme = darkColorScheme(
    primary = CraftAccentStart,
    onPrimary = CraftVoid,
    primaryContainer = CraftAccentMid,
    onPrimaryContainer = CraftVoid,

    secondary = CraftAccentEnd,
    onSecondary = CraftVoid,
    secondaryContainer = CraftSurfaceHigh,
    onSecondaryContainer = CraftTextPrimary,

    tertiary = CraftSuccess,
    onTertiary = CraftVoid,

    background = CraftVoid,
    onBackground = CraftTextPrimary,

    surface = CraftSurface,
    onSurface = CraftTextPrimary,
    surfaceVariant = CraftSurfaceHigh,
    onSurfaceVariant = CraftTextSecondary,

    surfaceContainer = CraftSurface,
    surfaceContainerHigh = CraftSurfaceHigh,
    surfaceContainerHighest = CraftSurfaceHigh,
    surfaceContainerLow = CraftVoid,
    surfaceContainerLowest = CraftVoid,

    outline = CraftOutline,
    outlineVariant = CraftOutline,

    error = CraftError,
    onError = CraftVoid,
)

/**
 * Tema de VideoEcraft. Siempre oscuro: no expone parámetro `darkTheme`
 * porque la identidad visual "Craft" depende del esquema oscuro.
 */
@Composable
fun VideoECraftTheme(
    content: @Composable () -> Unit,
) {
    // darkTheme se acepta pero no altera el esquema: app dark-only.
    MaterialTheme(
        colorScheme = CraftDarkColorScheme,
        typography = CraftTypography,
        shapes = CraftShapes,
        content = content,
    )
}
