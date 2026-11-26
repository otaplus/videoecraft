package com.example.videoecraft.engine.model

import com.example.videoecraft.engine.PlaybackSpeed
import com.example.videoecraft.engine.effects.ColorAdjustments
import com.example.videoecraft.engine.effects.ChromaKeySettings
import com.example.videoecraft.engine.effects.TransitionType

/**
 * Especificación completa de la edición, lista para `VideoProcessor`.
 *
 * Es el contrato entre la capa de UI/Room (Fase 3) y el motor multimedia:
 * no expone tipos de Media3 en su superficie pública, así que se puede
 * testear en JVM puro.
 */
data class EditSpec(
    /** Resolución y relación de aspecto del lienzo de salida. */
    val canvas: CanvasSpec = CanvasSpec(),
    /** Pista principal: clips de video en orden, con sus cortes. */
    val clips: List<ClipSpec> = emptyList(),
    /** Superposiciones (PIP / croma) que se dibujan encima de la principal. */
    val overlays: List<OverlaySpec> = emptyList(),
    /** Música/audio de fondo mezclado con el audio original. */
    val music: List<MusicSpec> = emptyList(),
    /** Capas de texto dibujadas encima del video. */
    val texts: List<TextLayerSpec> = emptyList(),
    /** Relación de volumen música / audio original (0..1). */
    val musicVolume: Float = 0.6f,
    /** Relación de volumen del audio original (0..1). */
    val originalVolume: Float = 1f,
) {
    /** Duración total del proyecto en milisegundos. */
    val durationMs: Long
        get() = clips.sumOf { it.trimmedDurationMs }

    val hasContent: Boolean get() = clips.isNotEmpty()
}

/**
 * Configuración del lienzo de salida.
 *
 * @param aspectRatio relación de aspecto elegida en el visor.
 * @param resolution resolución de exportación.
 * @param background relleno cuando el clip no cubre todo el lienzo.
 */
data class CanvasSpec(
    val aspectRatio: AspectRatio = AspectRatio.ORIGINAL,
    val resolution: ExportResolution = ExportResolution.P1080,
    val background: BackgroundFill = BackgroundFill.Blur(),
) {
    /** Tamaño de cuadro (ancho, alto) en píxeles según la relación elegida. */
    val sizePx: Pair<Int, Int>
        get() {
            val height = resolution.heightPx
            val width = (height * aspectRatio.ratio).toInt()
            // Ancho siempre par: exigido por la mayoría de encoders H.264.
            return (width - width % 2) to height
        }
}

/** Relaciones de aspecto ofrecidas en el visor. */
enum class AspectRatio(val label: String, val ratio: Float) {
    ORIGINAL("Original", 16f / 9f),
    YOUTUBE("16:9", 16f / 9f),
    VERTICAL("9:16", 9f / 16f),
    SQUARE("1:1", 1f),
}

/** Resoluciones de exportación. */
enum class ExportResolution(val label: String, val heightPx: Int) {
    P720("720p", 720),
    P1080("1080p", 1080),
    P2160("4K", 2160),
}

/** Frames por segundo del export. */
enum class ExportFps(val label: String, val fps: Int) {
    FPS30("30 fps", 30),
    FPS60("60 fps", 60),
}

/** Calidad / bitrate del export. */
enum class ExportBitrate(val label: String, val bitsPerSecond: Int) {
    LOW("Baja", 4_000_000),
    MEDIUM("Media", 8_000_000),
    HIGH("Alta", 16_000_000),
}

/**
 * Relleno del lienzo cuando el video no cubre toda la relación de aspecto.
 *
 * Equivale a la herramienta "Fondo (Canvas)" de YouCut.
 */
sealed interface BackgroundFill {
    /** Desenfoque del propio video (por defecto, el más elegante). */
    data class Blur(val radius: Float = 20f) : BackgroundFill

    /** Color sólido en ARGB. */
    data class Solid(val colorArgb: Int) : BackgroundFill

    /** Negro puro: la opción más rápida de codificar. */
    data object Black : BackgroundFill
}

/**
 * Clip de la pista principal.
 *
 * @param uri local (`content://` o `file://`) del video.
 * @param sourceStartMs inicio del recorte dentro del archivo original.
 * @param sourceEndMs fin del recorte (-1 = hasta el final del archivo).
 * @param speed factor de velocidad aplicado al clip.
 * @param color ajustes de color / filtro.
 * @param transitionIn transición que se aplica al entrar en este clip.
 * @param keepOriginalAudio si el audio del clip se conserva.
 */
data class ClipSpec(
    val uri: String,
    val sourceStartMs: Long = 0L,
    val sourceEndMs: Long = -1L,
    val speed: PlaybackSpeed = PlaybackSpeed.NORMAL,
    val color: ColorAdjustments = ColorAdjustments.NEUTRAL,
    val transitionIn: TransitionType = TransitionType.NONE,
    val transitionDurationMs: Long = 500L,
    val keepOriginalAudio: Boolean = true,
    /** Duración del archivo original, necesaria para calculos de timeline. */
    val sourceDurationMs: Long = 0L,
) {
    /** Duración efectiva del clip tras recorte y velocidad. */
    val trimmedDurationMs: Long
        get() {
            val end = if (sourceEndMs >= 0L) sourceEndMs else sourceDurationMs
            val raw = (end - sourceStartMs).coerceAtLeast(0L)
            return (raw / speed.factor).toLong()
        }
}

/**
 * Superposición de video (PIP o croma).
 *
 * @param chromaKey `null` = superposición normal con alfa; si se define,
 *   se aplica el shader de Chroma Key antes de componer.
 */
data class OverlaySpec(
    val uri: String,
    val startMs: Long = 0L,
    val endMs: Long = -1L,
    val chromaKey: ChromaKeySettings? = null,
    val color: ColorAdjustments = ColorAdjustments.NEUTRAL,
    /** Escala relativa al lienzo (1 = cubrir todo el frame). */
    val scale: Float = 0.4f,
    /** Posición normalizada del centro (-1..1) en X / Y. */
    val positionX: Float = 0.6f,
    val positionY: Float = 0.6f,
    val rotationDegrees: Float = 0f,
    val alpha: Float = 1f,
)

/**
 * Pista de música/audio de fondo.
 *
 * @param loop si debe repetirse hasta cubrir la duración del video.
 * @param volume ganancia lineal 0..1.
 */
data class MusicSpec(
    val uri: String,
    val startMs: Long = 0L,
    val endMs: Long = -1L,
    val loop: Boolean = true,
    val volume: Float = 0.6f,
    /** Recorte del propio archivo de música. */
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = -1L,
)

/**
 * Capa de texto arrastrable sobre el visor.
 *
 * @param text contenido (puede ser multilinea).
 * @param startMs / endMs ventana temporal en la que se muestra.
 * @param x / y posición normalizada del centro (0..1).
 * @param rotation rotación en grados.
 * @param fontSizeSp tamaño en sp; se escala al exportar.
 * @param colorArgb color del texto en ARGB.
 */
data class TextLayerSpec(
    val text: String,
    val startMs: Long = 0L,
    val endMs: Long = -1L,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val rotation: Float = 0f,
    val fontSizeSp: Float = 24f,
    val colorArgb: Int = 0xFFFFFFFF.toInt(),
    val backgroundColorArgb: Int = 0x00000000,
    val bold: Boolean = true,
)
