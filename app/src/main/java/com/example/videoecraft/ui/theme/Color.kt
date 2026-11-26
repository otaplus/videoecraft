package com.example.videoecraft.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// =====================================================================
//  Paleta "Craft" — modo oscuro profundo + acentos naranja neón / coral
// =====================================================================

/** Fondo base de toda la app: casi negro, ligeramente azulado. */
val CraftVoid = Color(0xFF101014)

/** Superficie de tarjetas elevadas. */
val CraftSurface = Color(0xFF1C1C24)

/** Superficie de tarjetas aún más elevadas (hojas, diálogos, chips). */
val CraftSurfaceHigh = Color(0xFF262630)

/** Bordes y divisores sutiles. */
val CraftOutline = Color(0xFF32323E)

/** Acento inicial del degradado: Naranja Neón. */
val CraftAccentStart = Color(0xFFFF5722)

/** Punto medio del degradado. */
val CraftAccentMid = Color(0xFFFF7043)

/** Acento final del degradado: Coral. */
val CraftAccentEnd = Color(0xFFFF8A65)

/** Texto principal. */
val CraftTextPrimary = Color(0xFFF5F5F7)

/** Texto secundario / metadatos. */
val CraftTextSecondary = Color(0xFF9A9AA8)

/** Error / estado destructivo. */
val CraftError = Color(0xFFFF5370)

/** Éxito / exportación completada. */
val CraftSuccess = Color(0xFF3DDC97)

/** Degradado de acento reutilizable (botones, cabeceras, timeline playhead). */
val CraftAccentGradient: Brush
    get() = Brush.linearGradient(listOf(CraftAccentStart, CraftAccentEnd))

/** Degradado vertical suave para fondos de tarjetas destacadas. */
val CraftSurfaceGradient: Brush
    get() = Brush.verticalGradient(listOf(CraftSurfaceHigh, CraftSurface))
