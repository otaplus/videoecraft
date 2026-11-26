package com.example.videoecraft.engine

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.WorkerThread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Publica el MP4 exportado en la galería del usuario.
 *
 * Destino: **`Movies/VideoEcraft`** (visible en Google Fotos, Galería, etc.).
 *
 * Implementa los dos caminos que exige soportar Android 24+:
 *
 *  - **API 29+**: `MediaStore.Video` + `RELATIVE_PATH`. Es el camino
 *    *scoped storage*: no requiere permiso de escritura.
 *  - **API 24-28**: copia directa a `getExternalStoragePublicDirectory`
 *    seguida de `MediaScannerConnection.scanFile` para registrarlo en la
 *    galería. Usa `WRITE_EXTERNAL_STORAGE`, declarado con `maxSdkVersion=28`.
 */
object MediaStoreSaver {

    /** Carpeta destino dentro de `Movies/`. */
    const val ALBUM_NAME = "VideoEcraft"

    /**
     * Copia [source] a la galería y devuelve su `content://` URI.
     *
     * @param source MP4 en caché producido por `VideoProcessor`.
     * @param displayName nombre visible, incluida la extensión `.mp4`.
     * @return URI accesible por otras apps.
     * @throws IllegalStateException si no se puede crear la entrada.
     */
    @WorkerThread
    suspend fun save(
        context: Context,
        source: File,
        displayName: String,
    ): Uri = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveWithMediaStore(context, source, displayName)
        } else {
            saveLegacy(context, source, displayName)
        }
    }

    // -----------------------------------------------------------------
    //  API 29+ — Scoped storage
    // -----------------------------------------------------------------

    private fun saveWithMediaStore(
        context: Context,
        source: File,
        displayName: String,
    ): Uri {
        val resolver = context.contentResolver

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(
                MediaStore.Video.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_MOVIES}/$ALBUM_NAME",
            )
            // IS_PENDING evita que la galería muestre un archivo a medio copiar.
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }

        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val itemUri = resolver.insert(collection, values)
            ?: error("No se pudo crear la entrada en MediaStore")

        try {
            resolver.openOutputStream(itemUri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: error("No se pudo abrir el stream de salida en MediaStore")

            // Publica la entrada: ya es visible en la galería.
            val publish = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            resolver.update(itemUri, publish, null, null)

            return itemUri
        } catch (e: Exception) {
            // Si falla, no dejamos basura en la galería.
            runCatching { resolver.delete(itemUri, null, null) }
            throw e
        }
    }

    // -----------------------------------------------------------------
    //  API 24-28 — Almacenamiento externo clásico
    // -----------------------------------------------------------------

    @Suppress("DEPRECATION")
    private fun saveLegacy(
        context: Context,
        source: File,
        displayName: String,
    ): Uri {
        val moviesDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_MOVIES,
        )
        val albumDir = File(moviesDir, ALBUM_NAME).apply { mkdirs() }
        val target = File(albumDir, displayName)

        source.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }

        // Registra el archivo para que aparezca en la galería al instante.
        MediaScannerConnection.scanFile(
            context,
            arrayOf(target.absolutePath),
            arrayOf("video/mp4"),
        ) { _, _ -> /* callback asíncrono: no bloquea la copia */ }

        return Uri.fromFile(target)
    }

    /**
     * Nombre de archivo único y legible, con marca temporal.
     * Ej: `VideoEcraft_20260214_103512.mp4`.
     */
    fun buildDisplayName(): String {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        return "${ALBUM_NAME}_$stamp.mp4"
    }
}
