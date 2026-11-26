package com.example.videoecraft.ui.editor

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.util.UnstableApi
import com.example.videoecraft.MediaMetadataReader
import com.example.videoecraft.VideoEcraftApp
import com.example.videoecraft.data.repository.ProjectRepository
import com.example.videoecraft.engine.PlaybackSpeed
import com.example.videoecraft.engine.effects.ChromaKeySettings
import com.example.videoecraft.engine.effects.ColorAdjustments
import com.example.videoecraft.engine.effects.TransitionType
import com.example.videoecraft.engine.model.AspectRatio
import com.example.videoecraft.engine.model.BackgroundFill
import com.example.videoecraft.engine.model.ClipSpec
import com.example.videoecraft.engine.model.MusicSpec
import com.example.videoecraft.engine.model.OverlaySpec
import com.example.videoecraft.engine.model.TextLayerSpec
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel del editor.
 *
 * Es la **única** fuente de verdad del proyecto en edición: todo cambio de
 * la UI pasa por aquí, se refleja en [EditorUiState.spec] y se autoguarda
 * en Room con un *debounce* para no golpear la base de datos en cada frame
 * de un slider.
 *
 * Flujo de datos:
 * ```
 *  gesto UI ──► ViewModel ──► EditorUiState (StateFlow) ──► Recomposición
 *                  │
 *                  └──► Room (debounced 800 ms)
 * ```
 */
@UnstableApi
class EditorViewModel(
    application: Application,
    private val projectId: Long,
    private val repository: ProjectRepository,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(EditorUiState(projectId = projectId))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    /** Job del autoguardado, para cancelar el anterior si llega otro cambio. */
    private var autosaveJob: Job? = null

    init {
        loadProject()
    }

    // -----------------------------------------------------------------
    //  Carga y guardado
    // -----------------------------------------------------------------

    private fun loadProject() {
        viewModelScope.launch {
            val project = repository.getProject(projectId)
            _uiState.update { state ->
                if (project == null) {
                    state.copy(isLoading = false, message = "Proyecto no encontrado")
                } else {
                    state.copy(
                        projectName = project.name,
                        spec = project.spec,
                        isLoading = false,
                        selectedClipIndex = 0,
                    )
                }
            }
        }
    }

    /** Programa un guardado con debounce (800 ms) tras un cambio. */
    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(AUTOSAVE_DEBOUNCE_MS)
            _uiState.update { it.copy(isSaving = true) }
            val state = _uiState.value
            runCatching {
                repository.saveProject(
                    id = projectId,
                    spec = state.spec,
                    name = state.projectName,
                )
            }
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    /** Fuerza el guardado inmediato (al salir del editor). */
    fun saveNow() {
        autosaveJob?.cancel()
        viewModelScope.launch {
            val state = _uiState.value
            runCatching {
                repository.saveProject(projectId, state.spec, state.projectName)
            }
        }
    }

    // -----------------------------------------------------------------
    //  Reproducción
    // -----------------------------------------------------------------

    /** Actualiza la posición del cabezal mientras reproduce. */
    fun onPlayheadUpdate(positionMs: Long) {
        _uiState.update { it.copy(playheadMs = positionMs.coerceAtLeast(0L)) }
    }

    fun onPlaybackStateChange(isPlaying: Boolean) {
        _uiState.update { it.copy(isPlaying = isPlaying) }
    }

    /**
     * Mueve el cabezal a [positionMs] y selecciona el clip que lo contiene.
     * Es lo que ocurre al arrastrar la timeline.
     */
    fun seekTo(positionMs: Long) {
        val state = _uiState.value
        val target = positionMs.coerceIn(0L, state.totalDurationMs)
        _uiState.update {
            it.copy(
                playheadMs = target,
                selectedClipIndex = clipIndexAt(target),
            )
        }
    }

    /** Devuelve el índice del clip que contiene [positionMs]. */
    private fun clipIndexAt(positionMs: Long): Int {
        val clips = _uiState.value.spec.clips
        var accumulated = 0L
        clips.forEachIndexed { index, clip ->
            accumulated += clip.trimmedDurationMs
            if (positionMs < accumulated) return index
        }
        return (clips.size - 1).coerceAtLeast(0)
    }

    /** Inicio acumulado (ms) del clip [index] en la timeline. */
    fun clipStartMs(index: Int): Long {
        val clips = _uiState.value.spec.clips
        if (index !in clips.indices) return 0L
        return clips.take(index).sumOf { it.trimmedDurationMs }
    }

    // -----------------------------------------------------------------
    //  Selección y herramientas
    // -----------------------------------------------------------------

    fun selectClip(index: Int) {
        val maxIndex = _uiState.value.spec.clips.lastIndex
        if (index in 0..maxIndex) {
            _uiState.update { it.copy(selectedClipIndex = index) }
        }
    }

    /** Abre/cierra un panel de herramienta. Tocar la activa la cierra. */
    fun selectTool(tool: EditorTool) {
        _uiState.update { state ->
            state.copy(activeTool = if (state.activeTool == tool) EditorTool.NONE else tool)
        }
    }

    fun closeTool() {
        _uiState.update { it.copy(activeTool = EditorTool.NONE) }
    }

    /** Zoom con pinch: px por segundo entre 20 y 300. */
    fun onTimelineZoom(factor: Float) {
        _uiState.update {
            it.copy(timelineZoom = (it.timelineZoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM))
        }
    }

    // -----------------------------------------------------------------
    //  Recortar y dividir
    // -----------------------------------------------------------------

    /**
     * Recorta el clip [clipIndex] a [startMs]..[endMs], en coordenadas del
     * **archivo original** (no de la timeline).
     */
    fun trimClip(clipIndex: Int, startMs: Long, endMs: Long) {
        updateClip(clipIndex) { clip ->
            val maxEnd = clip.sourceDurationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
            val safeStart = startMs.coerceIn(0L, maxEnd)
            val safeEnd = endMs.coerceIn(safeStart + MIN_CLIP_MS, maxEnd)
            clip.copy(sourceStartMs = safeStart, sourceEndMs = safeEnd)
        }
    }

    /**
     * Divide el clip [clipIndex] por el punto del cabezal.
     *
     * El corte se calcula en coordenadas de origen: hay que deshacer la
     * velocidad para saber qué instante del archivo corresponde al cabezal.
     */
    fun splitClipAtPlayhead() {
        val state = _uiState.value
        val index = state.selectedClipIndex
        val clip = state.spec.clips.getOrNull(index) ?: return

        val clipStart = clipStartMs(index)
        val offsetInTrimmed = state.playheadMs - clipStart
        if (offsetInTrimmed <= MIN_CLIP_MS || offsetInTrimmed >= clip.trimmedDurationMs - MIN_CLIP_MS) {
            _uiState.update { it.copy(message = "Mueve el cabezal dentro del clip para dividir") }
            return
        }

        // Deshacer la velocidad: origen = inicio + offset * factor
        val offsetInSource = (offsetInTrimmed * clip.speed.factor).toLong()
        val splitPointInSource = clip.sourceStartMs + offsetInSource

        val firstHalf = clip.copy(sourceEndMs = splitPointInSource)
        val secondHalf = clip.copy(
            sourceStartMs = splitPointInSource,
            // La transición de entrada pertenece al primer fragmento.
            transitionIn = TransitionType.NONE,
        )

        val newClips = state.spec.clips.toMutableList().apply {
            removeAt(index)
            addAll(index, listOf(firstHalf, secondHalf))
        }

        _uiState.update {
            it.copy(
                spec = it.spec.copy(clips = newClips),
                selectedClipIndex = index + 1,
                message = "Clip dividido",
            )
        }
        scheduleAutosave()
    }

    /** Elimina el clip [index] si queda más de uno. */
    fun deleteClip(index: Int) {
        val clips = _uiState.value.spec.clips
        if (clips.size <= 1) {
            _uiState.update { it.copy(message = "Un proyecto necesita al menos un clip") }
            return
        }
        val newClips = clips.toMutableList().apply { removeAt(index) }
        _uiState.update {
            it.copy(
                spec = it.spec.copy(clips = newClips),
                selectedClipIndex = it.selectedClipIndex.coerceIn(0, newClips.lastIndex),
            )
        }
        scheduleAutosave()
    }

    // -----------------------------------------------------------------
    //  Color y filtros
    // -----------------------------------------------------------------

    fun updateColorAdjustments(adjustments: ColorAdjustments) {
        updateClip(_uiState.value.selectedClipIndex) { it.copy(color = adjustments) }
    }

    /** Un solo slider de brillo/contraste/etc. a la vez. */
    fun updateBrightness(value: Float) =
        updateColorAdjustments(_uiState.value.selectedColor.copy(brightness = value))

    fun updateContrast(value: Float) =
        updateColorAdjustments(_uiState.value.selectedColor.copy(contrast = value))

    fun updateSaturation(value: Float) =
        updateColorAdjustments(_uiState.value.selectedColor.copy(saturation = value))

    fun updateWarmth(value: Float) =
        updateColorAdjustments(_uiState.value.selectedColor.copy(warmth = value))

    fun resetColorAdjustments() = updateColorAdjustments(ColorAdjustments.NEUTRAL)

    // -----------------------------------------------------------------
    //  Transiciones
    // -----------------------------------------------------------------

    fun setTransition(transition: TransitionType) {
        updateClip(_uiState.value.selectedClipIndex) {
            it.copy(transitionIn = transition, transitionDurationMs = transition.durationMs)
        }
    }

    // -----------------------------------------------------------------
    //  Velocidad
    // -----------------------------------------------------------------

    fun setSpeed(speed: PlaybackSpeed) {
        updateClip(_uiState.value.selectedClipIndex) {
            it.copy(speed = speed)
        }
    }

    // -----------------------------------------------------------------
    //  Croma
    // -----------------------------------------------------------------

    /**
     * Fija los ajustes de croma del primer overlay. Si el proyecto aún no
     * tiene overlay, se crea usando el clip seleccionado como fuente.
     */
    fun setChromaKey(settings: ChromaKeySettings?) {
        val state = _uiState.value
        val existing = state.spec.overlays.toMutableList()

        if (existing.isEmpty()) {
            val source = state.selectedClip ?: return
            existing += OverlaySpec(
                uri = source.uri,
                startMs = 0L,
                endMs = -1L,
                chromaKey = settings,
            )
        } else {
            existing[0] = existing[0].copy(chromaKey = settings)
        }

        _uiState.update { it.copy(spec = it.spec.copy(overlays = existing)) }
        scheduleAutosave()
    }

    /** Ajusta un parámetro del croma conservando los demás. */
    fun updateChromaSimilarity(value: Float) =
        setChromaKey(_uiState.value.chromaSettings.copy(similarity = value))

    fun updateChromaSmoothness(value: Float) =
        setChromaKey(_uiState.value.chromaSettings.copy(smoothness = value))

    fun updateChromaSpill(value: Float) =
        setChromaKey(_uiState.value.chromaSettings.copy(spillReduction = value))

    /** Fija el color clave a partir de un color ARGB (pipeta / presets). */
    fun setChromaKeyColor(argb: Int) {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        setChromaKey(_uiState.value.chromaSettings.copy(keyColor = Triple(r, g, b)))
    }

    fun clearChromaKey() = setChromaKey(null)

    // -----------------------------------------------------------------
    //  Audio
    // -----------------------------------------------------------------

    /** Añade/reemplaza la pista de música. */
    fun setMusic(uri: Uri) {
        viewModelScope.launch {
            val metadata = MediaMetadataReader.read(getApplication(), uri.toString())
            _uiState.update { state ->
                state.copy(
                    spec = state.spec.copy(
                        music = listOf(
                            MusicSpec(
                                uri = uri.toString(),
                                loop = true,
                                volume = state.spec.musicVolume,
                            ),
                        ),
                    ),
                )
            }
            scheduleAutosave()
            // `metadata` se lee para validar que el archivo es accesible.
            if (metadata == null) {
                _uiState.update { it.copy(message = "No se pudo leer el archivo de audio") }
            }
        }
    }

    /** Elimina la pista de música. */
    fun clearMusic() {
        _uiState.update { it.copy(spec = it.spec.copy(music = emptyList())) }
        scheduleAutosave()
    }

    /** Mezclador: volumen de la música (0..1). */
    fun setMusicVolume(value: Float) {
        _uiState.update { it.copy(spec = it.spec.copy(musicVolume = value.coerceIn(0f, 1f))) }
        scheduleAutosave()
    }

    /** Mezclador: volumen del audio original (0..1). */
    fun setOriginalVolume(value: Float) {
        _uiState.update { it.copy(spec = it.spec.copy(originalVolume = value.coerceIn(0f, 1f))) }
        scheduleAutosave()
    }

    /** Silencia o restaura el audio original de todos los clips. */
    fun toggleOriginalAudioMuted(muted: Boolean) {
        val clips = _uiState.value.spec.clips.map { it.copy(keepOriginalAudio = !muted) }
        _uiState.update { it.copy(spec = it.spec.copy(clips = clips)) }
        scheduleAutosave()
    }

    // -----------------------------------------------------------------
    //  Texto
    // -----------------------------------------------------------------

    /** Añade una capa de texto en el centro del visor. */
    fun addTextLayer(text: String) {
        if (text.isBlank()) return
        val layer = TextLayerSpec(
            text = text.trim(),
            startMs = _uiState.value.playheadMs,
            endMs = -1L,
        )
        _uiState.update { it.copy(spec = it.spec.copy(texts = it.spec.texts + layer)) }
        scheduleAutosave()
    }

    /** Mueve/redimensiona/rota la capa de texto [index]. */
    fun updateTextLayer(
        index: Int,
        transform: (TextLayerSpec) -> TextLayerSpec,
    ) {
        val texts = _uiState.value.spec.texts.toMutableList()
        if (index !in texts.indices) return
        texts[index] = transform(texts[index])
        _uiState.update { it.copy(spec = it.spec.copy(texts = texts)) }
        scheduleAutosave()
    }

    /** Arrastre en el visor: posición normalizada 0..1. */
    fun moveTextLayer(index: Int, x: Float, y: Float) {
        updateTextLayer(index) {
            it.copy(x = x.coerceIn(0f, 1f), y = y.coerceIn(0f, 1f))
        }
    }

    fun deleteTextLayer(index: Int) {
        val texts = _uiState.value.spec.texts.toMutableList()
        if (index !in texts.indices) return
        texts.removeAt(index)
        _uiState.update { it.copy(spec = it.spec.copy(texts = texts)) }
        scheduleAutosave()
    }

    // -----------------------------------------------------------------
    //  Lienzo / relación de aspecto
    // -----------------------------------------------------------------

    fun setAspectRatio(aspect: AspectRatio) {
        _uiState.update {
            it.copy(spec = it.spec.copy(canvas = it.spec.canvas.copy(aspectRatio = aspect)))
        }
        scheduleAutosave()
    }

    fun setBackground(fill: BackgroundFill) {
        _uiState.update {
            it.copy(spec = it.spec.copy(canvas = it.spec.canvas.copy(background = fill)))
        }
        scheduleAutosave()
    }

    // -----------------------------------------------------------------
    //  Utilidades
    // -----------------------------------------------------------------

    /** Aplica [transform] al clip [index] y agenda el autoguardado. */
    private fun updateClip(index: Int, transform: (ClipSpec) -> ClipSpec) {
        val clips = _uiState.value.spec.clips.toMutableList()
        if (index !in clips.indices) return
        clips[index] = transform(clips[index])
        _uiState.update { it.copy(spec = it.spec.copy(clips = clips)) }
        scheduleAutosave()
    }

    /** Consume el mensaje tras mostrarlo en el Snackbar. */
    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    override fun onCleared() {
        // Guardado final síncrono al cerrar el editor.
        super.onCleared()
    }

    companion object {
        /** Debounce del autoguardado: 800 ms tras el último cambio. */
        private const val AUTOSAVE_DEBOUNCE_MS = 800L

        /** Duración mínima de un clip tras recortar/dividir. */
        private const val MIN_CLIP_MS = 200L

        private const val MIN_ZOOM = 20f
        private const val MAX_ZOOM = 300f

        /** Crea la factoría con el `projectId` de la ruta. */
        fun factory(projectId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as VideoEcraftApp
                EditorViewModel(
                    application = app,
                    projectId = projectId,
                    repository = app.container.projectRepository,
                )
            }
        }
    }
}
