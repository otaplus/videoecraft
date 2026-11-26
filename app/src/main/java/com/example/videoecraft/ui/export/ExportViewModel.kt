package com.example.videoecraft.ui.export

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.util.UnstableApi
import com.example.videoecraft.VideoEcraftApp
import com.example.videoecraft.data.repository.ProjectRepository
import com.example.videoecraft.engine.model.EditSpec
import com.example.videoecraft.engine.model.ExportBitrate
import com.example.videoecraft.engine.model.ExportFps
import com.example.videoecraft.engine.model.ExportResolution
import com.example.videoecraft.export.ExportProgressHolder
import com.example.videoecraft.export.ExportRequest
import com.example.videoecraft.export.ExportService
import com.example.videoecraft.export.ExportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Opciones de exportación elegidas por el usuario.
 *
 * @param resolution resolución de salida.
 * @param fps frames por segundo.
 * @param bitrate calidad de codificación.
 */
@UnstableApi
data class ExportOptions(
    val resolution: ExportResolution = ExportResolution.P1080,
    val fps: ExportFps = ExportFps.FPS30,
    val bitrate: ExportBitrate = ExportBitrate.MEDIUM,
)

/** Estado de la pantalla de exportación. */
@UnstableApi
data class ExportUiState(
    val projectName: String = "",
    val options: ExportOptions = ExportOptions(),
    val durationMs: Long = 0L,
    val clipCount: Int = 0,
    val isLoading: Boolean = true,
) {
    /** Tamaño estimado del archivo en MB, aproximado por bitrate × duración. */
    val estimatedSizeMb: Float
        get() {
            val bits = options.bitrate.bitsPerSecond.toLong() * (durationMs / 1000L)
            // 8 bits por byte, 1024² para MB.
            return bits / 8f / (1024f * 1024f)
        }
}

/**
 * ViewModel de la pantalla de exportación.
 *
 * Puente entre la UI y [ExportService]:
 *  - Carga el proyecto y aplica las opciones de exportación al `EditSpec`.
 *  - Arranca el servicio de primer plano con [ExportService.start].
 *  - Observa [ExportProgressHolder] y lo expone como [StateFlow].
 */
@UnstableApi
class ExportViewModel(
    application: Application,
    private val projectId: Long,
    private val repository: ProjectRepository,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    /** Estado del export en curso, observado desde el servicio. */
    val exportState: StateFlow<ExportState> = ExportProgressHolder.state
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ExportProgressHolder.state.value,
        )

    /** Spec del proyecto, cargado una vez. */
    private var loadedSpec: EditSpec? = null

    init {
        viewModelScope.launch {
            val project = repository.getProject(projectId)
            loadedSpec = project?.spec
            _uiState.update { state ->
                state.copy(
                    projectName = project?.name ?: "Proyecto",
                    durationMs = project?.spec?.durationMs ?: 0L,
                    clipCount = project?.spec?.clips?.size ?: 0,
                    isLoading = false,
                )
            }
        }
    }

    /** Cambia la resolución de exportación. */
    fun setResolution(resolution: ExportResolution) {
        _uiState.update { state ->
            state.copy(
                options = state.options.copy(resolution = resolution),
                // La resolución forma parte del lienzo del spec.
            )
        }
        applyResolutionToSpec(resolution)
    }

    /** Cambia los FPS de salida. */
    fun setFps(fps: ExportFps) {
        _uiState.update { it.copy(options = it.options.copy(fps = fps)) }
    }

    /** Cambia la calidad/bitrate. */
    fun setBitrate(bitrate: ExportBitrate) {
        _uiState.update { it.copy(options = it.options.copy(bitrate = bitrate)) }
    }

    /**
     * Aplica la resolución al [EditSpec] cargado.
     * La resolución vive en `CanvasSpec`, así que se persiste en el proyecto.
     */
    private fun applyResolutionToSpec(resolution: ExportResolution) {
        val spec = loadedSpec ?: return
        val updated = spec.copy(canvas = spec.canvas.copy(resolution = resolution))
        loadedSpec = updated
        _uiState.update { it.copy(options = it.options.copy(resolution = resolution)) }

        viewModelScope.launch {
            runCatching { repository.saveProject(projectId, updated) }
        }
    }

    /** Arranca la exportación en primer plano. */
    fun startExport() {
        val spec = loadedSpec ?: return
        val state = _uiState.value
        val context = getApplication<Application>()

        // Se reaplica la resolución elegida por si cambió sin guardar.
        val finalSpec = spec.copy(canvas = spec.canvas.copy(resolution = state.options.resolution))

        ExportService.start(
            context = context,
            request = ExportRequest(
                spec = finalSpec,
                fps = state.options.fps,
                bitrate = state.options.bitrate,
            ),
        )
    }

    /** Cancela el export en curso. */
    fun cancelExport() {
        ExportService.cancel(getApplication())
    }

    /** Limpia el estado del export (tras cerrar el diálogo de resultado). */
    fun resetExportState() {
        ExportProgressHolder.reset()
    }

    companion object {
        fun factory(projectId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as VideoEcraftApp
                ExportViewModel(
                    application = app,
                    projectId = projectId,
                    repository = app.container.projectRepository,
                )
            }
        }
    }
}
