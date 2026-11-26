package com.example.videoecraft.ui.home

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
import com.example.videoecraft.data.MediaImporter
import com.example.videoecraft.data.repository.Project
import com.example.videoecraft.data.repository.ProjectRepository
import com.example.videoecraft.engine.model.ClipSpec
import com.example.videoecraft.engine.model.EditSpec
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Estado completo de la pantalla de inicio. */
@UnstableApi
data class HomeUiState(
    val projects: List<Project> = emptyList(),
    val isLoading: Boolean = true,
    /** `true` mientras se leen metadatos de los videos recién importados. */
    val isImporting: Boolean = false,
) {
    /** El estado vacío sólo aplica cuando ya cargó y no hay nada. */
    val isEmpty: Boolean get() = !isLoading && projects.isEmpty()
}

/**
 * ViewModel de la pantalla Home.
 *
 * Responsabilidades:
 *  - Observar la lista de proyectos de Room (cuadrícula reactiva).
 *  - Converter los `Uri` del Photo Picker en un [EditSpec] inicial,
 *    leyendo la duración real de cada video.
 *  - Crear el proyecto en Room y avisar a la UI para navegar al editor.
 */
@UnstableApi
class HomeViewModel(
    application: Application,
    private val repository: ProjectRepository,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** Eventos de un solo uso hacia la UI (navegar al editor). */
    private val _events = Channel<HomeEvent>(Channel.BUFFERED)
    val events: Flow<HomeEvent> = _events.receiveAsFlow()

    init {
        // La cuadrícula se repinta automáticamente al guardar desde el editor.
        viewModelScope.launch {
            repository.observeProjects().collect { projects ->
                _uiState.value = _uiState.value.copy(
                    projects = projects,
                    isLoading = false,
                )
            }
        }
    }

    /**
     * Procesa la selección del Photo Picker.
     *
     * Lee la duración de cada video para poder construir una timeline válida
     * y, si todo sale bien, crea el proyecto y emite [HomeEvent.OpenEditor].
     */
    fun onMediaPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true)
            try {
                val context = getApplication<Application>()

                // Clave estable de esta importación: agrupa los archivos
                // copiados al sandbox para poder borrarlos con el proyecto.
                val projectKey = "p${System.currentTimeMillis()}"

                // Se procesan en serie: abrir decenas de
                // MediaMetadataRetriever en paralelo satura los codecs.
                val clips = uris.mapNotNull { uri ->
                    val sourceUri = uri.toString()

                    // Las URIs del Photo Picker son concesiones temporales
                    // que caducan al morir el proceso. Se copian al sandbox
                    // para que el borrador siga siendo válido en el futuro.
                    val mediaUri = if (MediaImporter.needsImport(sourceUri)) {
                        MediaImporter.import(
                            context = context,
                            sourceUri = sourceUri,
                            projectKey = projectKey,
                        ) ?: return@mapNotNull null
                    } else {
                        sourceUri
                    }

                    val metadata = MediaMetadataReader.read(context, mediaUri)
                        ?: return@mapNotNull null

                    if (!metadata.isVideo || metadata.durationMs <= 0L) {
                        // Archivo copiado pero inválido: no dejamos basura.
                        deleteImportedFile(mediaUri)
                        return@mapNotNull null
                    }

                    ClipSpec(
                        uri = mediaUri,
                        sourceStartMs = 0L,
                        sourceEndMs = -1L,
                        sourceDurationMs = metadata.durationMs,
                    )
                }

                if (clips.isEmpty()) {
                    _events.send(HomeEvent.ShowError("No se pudo leer ningún video válido."))
                    return@launch
                }

                val spec = EditSpec(clips = clips)
                val projectId = repository.createProject(
                    name = defaultProjectName(projectIndex = _uiState.value.projects.size + 1),
                    spec = spec,
                )

                _events.send(HomeEvent.OpenEditor(projectId))
            } catch (t: Throwable) {
                _events.send(
                    HomeEvent.ShowError(t.message ?: "Error al importar los videos."),
                )
            } finally {
                _uiState.value = _uiState.value.copy(isImporting = false)
            }
        }
    }

    /** Borra un archivo copiado al sandbox cuando el clip resulta inválido. */
    private fun deleteImportedFile(uri: String) {
        runCatching {
            val path = Uri.parse(uri).path ?: return@runCatching
            java.io.File(path).takeIf { it.exists() }?.delete()
        }
    }

    /**
     * Elimina un proyecto **y sus archivos de medios**.
     *
     * Borrar solo la fila de Room dejaría los MP4 importados en el sandbox
     * para siempre, consumiendo espacio del usuario sin ninguna forma de
     * recuperarlo desde la interfaz. Por eso se usa
     * [ProjectRepository.deleteProjectWithMedia].
     */
    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            runCatching {
                repository.deleteProjectWithMedia(getApplication(), projectId)
            }.onFailure {
                _events.send(HomeEvent.ShowError("No se pudo borrar el proyecto."))
            }
        }
    }

    /** Renombra un proyecto existente. */
    fun renameProject(projectId: Long, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.renameProject(projectId, trimmed) }
        }
    }

    /** Nombre por defecto legible: "Proyecto 3". */
    private fun defaultProjectName(projectIndex: Int): String = "Proyecto $projectIndex"

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as VideoEcraftApp
                HomeViewModel(
                    application = app,
                    repository = app.container.projectRepository,
                )
            }
        }
    }
}

/** Eventos de un solo uso de Home. */
@UnstableApi
sealed interface HomeEvent {
    /** Navegar al editor del proyecto recién creado. */
    data class OpenEditor(val projectId: Long) : HomeEvent

    /** Mostrar un error (Snackbar). */
    data class ShowError(val message: String) : HomeEvent
}
