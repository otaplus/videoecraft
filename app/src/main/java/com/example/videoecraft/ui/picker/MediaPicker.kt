package com.example.videoecraft.ui.picker

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Photo Picker de Android (múltiple).
 *
 * Usa `ActivityResultContracts.PickMultipleVisualMedia`, que delega en el
 * selector del sistema. Ventajas frente a pedir `READ_MEDIA_VIDEO`:
 *
 *  - **No requiere ningún permiso**: el usuario elige qué archivos concede.
 *  - Funciona igual en Android 11+ (picker nativo) y en versiones
 *    anteriores (fallback a DocumentsUI), sin código condicional.
 *  - Cumple la política de Play sobre acceso a fotos y videos.
 *
 * Se piden máximo [maxItems] elementos; el límite lo impone el contrato.
 */
@Composable
fun rememberMediaPicker(
    maxItems: Int = 20,
    onMediaPicked: (List<Uri>) -> Unit,
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems),
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) onMediaPicked(uris)
    }

    return remember(launcher) {
        {
            launcher.launch(
                PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.VideoOnly,
                ),
            )
        }
    }
}

/**
 * Selector de audio (para la pista de música).
 *
 * Los archivos de audio no están cubiertos por Photo Picker, así que se usa
 * `OpenDocument`, que tampoco requiere permisos y da acceso persistente al
 * `content://` elegido.
 */
@Composable
fun rememberAudioPicker(
    onAudioPicked: (Uri) -> Unit,
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) onAudioPicked(uri)
    }

    return remember(launcher) {
        { launcher.launch(arrayOf("audio/*")) }
    }
}
