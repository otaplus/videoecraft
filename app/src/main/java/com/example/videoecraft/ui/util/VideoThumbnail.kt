package com.example.videoecraft.ui.util

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import coil3.request.ImageRequest
import coil3.toUri
import coil3.video.videoFrameMicros
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utilidades para las miniaturas de la timeline.
 *
 * La timeline muestra el fotograma de cada clip. Pedir a Coil la URI tal
 * cual devuelve el **primer** frame, que suele ser negro (muchos videos
 * empiezan con un fundido). Aquí se indica explícitamente qué instante
 * extraer, lo que produce miniaturas útiles de un vistazo.
 */
@UnstableApi
object VideoThumbnail {

    /**
     * Crea la petición de Coil para la miniatura de un clip.
     *
     * @param context contexto para construir la [ImageRequest].
     * @param uriUri `content://` del video.
     * @param offsetMs instante del video a extraer (por defecto 1 s).
     */
    fun request(
        context: Context,
        uriUri: String,
        offsetMs: Long = 1_000L,
    ): ImageRequest =
        ImageRequest.Builder(context)
            .data(uriUri.toUri())
            .videoFrameMicros(offsetMs * 1_000L)
            // Se pide al tamaño de la celda para no decodificar 4K en vano:
            // ahorra memoria y hace las miniaturas mucho más rápidas.
            .size(THUMBNAIL_SIZE_PX)
            .build()

    /**
     * Extrae el fotograma inicial de un video y lo guarda en caché como
     * archivo. Se usa cuando Coil no puede leer el `content://` directamente
     * (algunos proveedores de Photo Picker no exponen un `openFileDescriptor`
     * síncrono que Coil pueda consumir en su hilo de decodificación).
     *
     * @return ruta del archivo de miniatura o `null` si falla.
     */
    suspend fun extractFirstFrameToCache(
        context: Context,
        uriUri: String,
        cacheKey: String,
    ): String? = withContext(Dispatchers.IO) {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, Uri.parse(uriUri))
            val bitmap = retriever.getFrameAtTime(
                FRAME_TIME_US,
                android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            ) ?: return@withContext null

            val dir = java.io.File(context.cacheDir, "thumbnails").apply { mkdirs() }
            val file = java.io.File(dir, "$cacheKey.jpg")

            file.outputStream().use { output ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, output)
            }
            bitmap.recycle()
            file.absolutePath
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Tamaño al que se decodifican las miniaturas de la timeline. */
    private const val THUMBNAIL_SIZE_PX = 320

    /** Instante del primer fotograma extraíble (1 s), en microsegundos. */
    private const val FRAME_TIME_US = 1_000_000L
}
