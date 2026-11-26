package com.example.videoecraft.ui.editor

import androidx.media3.common.util.UnstableApi
import com.example.videoecraft.engine.PlaybackSpeed
import com.example.videoecraft.engine.effects.ChromaKeySettings
import com.example.videoecraft.engine.effects.ColorAdjustments
import com.example.videoecraft.engine.effects.TransitionType
import com.example.videoecraft.engine.model.ClipSpec
import com.example.videoecraft.engine.model.EditSpec
import com.example.videoecraft.engine.model.OverlaySpec
import com.example.videoecraft.engine.model.TextLayerSpec

/** Herramientas de la barra inferior del editor. */
enum class EditorTool(val label: String) {
    TRIM("Recortar"),
    TRANSITIONS("Transiciones"),
    CHROMA("Croma"),
    SPEED("Velocidad"),
    AUDIO("Audio"),
    TEXT("Texto"),
    FILTERS("Filtros"),
    CANVAS("Fondo"),
    NONE(""),
}

/** Estado completo de la pantalla del editor. */
@UnstableApi
data class EditorUiState(
    val projectId: Long = 0L,
    val projectName: String = "",
    val spec: EditSpec = EditSpec(),

    /** Clip actualmente seleccionado en la timeline (índice de [EditSpec.clips]). */
    val selectedClipIndex: Int = 0,

    /** Herramienta abierta en el panel inferior. */
    val activeTool: EditorTool = EditorTool.NONE,

    /** Posición del cabezal en ms. */
    val playheadMs: Long = 0L,

    /** Si el reproductor está reproduciendo. */
    val isPlaying: Boolean = false,

    /** Zoom de la timeline: px por segundo. */
    val timelineZoom: Float = 60f,

    val isLoading: Boolean = true,
    val isSaving: Boolean = false,

    /** Mensaje efímero para el Snackbar. */
    val message: String? = null,
) {
    /** Clip seleccionado, o `null` si el proyecto está vacío. */
    val selectedClip: ClipSpec?
        get() = spec.clips.getOrNull(selectedClipIndex)

    /** Duración total del proyecto. */
    val totalDurationMs: Long get() = spec.durationMs

    /** Relación de aspecto legible para el visor. */
    val aspectRatio: Float get() = spec.canvas.aspectRatio.ratio

    /** Ajustes de color del clip seleccionado. */
    val selectedColor: ColorAdjustments
        get() = selectedClip?.color ?: ColorAdjustments.NEUTRAL

    /** Velocidad del clip seleccionado. */
    val selectedSpeed: PlaybackSpeed
        get() = selectedClip?.speed ?: PlaybackSpeed.NORMAL

    /** Transición de entrada del clip seleccionado. */
    val selectedTransition: TransitionType
        get() = selectedClip?.transitionIn ?: TransitionType.NONE

    /** Primer overlay, si existe (panel de Croma opera sobre él). */
    val selectedOverlay: OverlaySpec?
        get() = spec.overlays.firstOrNull()

    /** Ajustes de croma del overlay, o los de fábrica. */
    val chromaSettings: ChromaKeySettings
        get() = selectedOverlay?.chromaKey ?: ChromaKeySettings.GREEN

    /** Capa de texto activa, si la hay. */
    val activeTextLayer: TextLayerSpec?
        get() = spec.texts.firstOrNull()

    /** Volumen de la música relativo. */
    val musicVolume: Float get() = spec.musicVolume

    /** Volumen del audio original. */
    val originalVolume: Float get() = spec.originalVolume

    /** `true` si el proyecto puede exportarse. */
    val canExport: Boolean get() = spec.hasContent && !isLoading
}
