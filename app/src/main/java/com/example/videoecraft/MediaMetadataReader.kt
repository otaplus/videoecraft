package com.example.videoecraft

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lee metadatos de un archivo de video/audio (duración, dimensiones).
 *
 * Hace falta porque al importar clips necesitamos `sourceDurationMs` para
 * calcular la duración del proyecto y pintar la timeline **antes** de que
 * ExoPlayer cargue el archivo. `MediaMetadataRetriever` es más ligero que
 * montar un `ExoPlayer` sólo para leer la duración.
 */
@UnstableApi
object MediaMetadataReader {

    /** Metadatos mínimos de un medio importado. */
    data class MediaMetadata(
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val isVideo: Boolean,
    )

    /**
     * Lee los metadatos de [uri].
     *
     * Devuelve `null` en lugar de lanzar si el archivo no se puede abrir:
     * un clip corrupto no debe tumbar la importación de los demás.
     */
    suspend fun read(context: Context, uri: String): MediaMetadata? =
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, Uri.parse(uri))

                val duration = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?: 0L

                val width = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull()
                    ?: 0

                val height = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull()
                    ?: 0

                val hasVideo = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) == "yes"

                MediaMetadata(
                    durationMs = duration,
                    width = width,
                    height = height,
                    isVideo = hasVideo,
                )
            } catch (e: Exception) {
                null
            } finally {
                runCatching { retriever.release() }
            }
        }
}
