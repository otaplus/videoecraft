package com.example.videoecraft.export

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado observable de la exportación en curso.
 *
 * [ExportService] corre en un servicio (sin ViewModel) pero la UI necesita
 * ver el progreso. Este singleton hace de puente con un [StateFlow] que el
 * `ExportViewModel` colecta directamente, sin `bindService()` ni AIDL.
 *
 * Es un `object` a propósito: hay un único export activo por proceso, así
 * que el estado global es el modelo correcto y evita duplicar la fuente de
 * verdad entre servicio y UI.
 */
@UnstableApi
object ExportProgressHolder {

    private val _state = MutableStateFlow<ExportState>(ExportState.Idle)
    val state: StateFlow<ExportState> = _state.asStateFlow()

    /** Marca el inicio (o reinicia) de una exportación. */
    fun markRunning(progress: Float = 0f) {
        _state.value = ExportState.Running(progress.coerceIn(0f, 1f))
    }

    /** Marca el export como completado y expone el URI en la galería. */
    fun markCompleted(uri: Uri) {
        _state.value = ExportState.Completed(uri)
    }

    /** Marca un fallo con el mensaje a mostrar en la UI. */
    fun markFailed(message: String) {
        _state.value = ExportState.Failed(message)
    }

    /** El usuario canceló el export. */
    fun markCancelled() {
        _state.value = ExportState.Cancelled
    }

    /** Vuelve a reposo (tras consumir el resultado en la UI). */
    fun reset() {
        _state.value = ExportState.Idle
    }
}

/** Estados de la máquina de exportación observables por Compose. */
@UnstableApi
sealed interface ExportState {
    /** Sin exportación en curso. */
    data object Idle : ExportState

    /** Exportando; [progress] va de 0 a 1. */
    data class Running(val progress: Float) : ExportState

    /** Terminado con éxito; [uri] apunta al video en `Movies/VideoEcraft`. */
    data class Completed(val uri: Uri) : ExportState

    /** Error; [message] es apto para mostrar al usuario. */
    data class Failed(val message: String) : ExportState

    /** Cancelado por el usuario. */
    data object Cancelled : ExportState
}
