package com.example.videoecraft.data

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Importa los medios elegidos por el usuario al almacenamiento privado de la app.
 *
 * ### Por qué es imprescindible
 *
 * Las URIs del Photo Picker (`content://media/picker/.../media/123`) son
 * **concesiones temporales de lectura**. Caducan cuando:
 *  - el proceso de la app muere y se recrea, o
 *  - el usuario reinicia el dispositivo.
 *
 * Si el proyecto guarda esa URI en Room, al día siguiente la edición apunta
 * a un recurso que ya no se puede abrir y tanto la previsualización como la
 * exportación fallan con `SecurityException: Calling uid does not have
 * permission to access picker uri`.
 *
 * Copiar el archivo a `filesDir/editor/` en el momento de la importación
 * resuelve el problema de raíz: la app es dueña del archivo, el permiso
 * nunca caduca y el borrador sigue siendo válido semanas después.
 *
 * El coste es espacio en disco; por eso el ciclo de vida de esos archivos
 * lo gestiona [MediaFileStore], que los borra al eliminar el proyecto.
 */
@UnstableApi
object MediaImporter {

    /**
     * Copia [sourceUri] al sandbox de la app y devuelve el `file://` URI
     * del destino, listo para guardarse en un [ClipSpec]/[MusicSpec].
     *
     * @param projectKey identificador para agrupar archivos del proyecto.
     * @param extension extensión del archivo, incluido el punto.
     * @return la URI local, o `null` si la copia falla.
     */
    suspend fun import(
        context: Context,
        sourceUri: String,
        projectKey: String,
        extension: String = ".mp4",
    ): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = MediaFileStore.directory(context)

            // Nombre único: proyecto + uuid, para no colisionar entre clips
            // ni entre importaciones repetidas del mismo archivo.
            val target = File(dir, "${projectKey}_${UUID.randomUUID()}$extension")

            context.contentResolver.openInputStream(Uri.parse(sourceUri))?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null

            // Verificación mínima de integridad antes de dar el archivo por bueno.
            if (target.length() <= 0L) {
                target.delete()
                return@runCatching null
            }

            Uri.fromFile(target).toString()
        }.getOrNull()
    }

    /**
     * `true` si [uri] necesita copiarse al sandbox antes de guardarse.
     *
     * No se intenta detectar el Photo Picker por el prefijo de la URI:
     * el formato cambia entre versiones de Android, OEMs y el fallback de
     * DocumentsUI (`content://com.android.providers.media.documents/...`).
     * Heurística equivocada ⇒ el clip se queda con una concesión
     * temporal ⇒ el proyecto se rompe días después. Por eso solo se
     * considera seguro lo que ya es un `file://` dentro de nuestra app.
     */
    fun needsImport(uri: String): Boolean {
        if (!uri.startsWith("file://")) return true

        // Un file:// fuera del sandbox también se copia para garantizar que
        // sobrevive a limpiezas de caché o borrados del usuario.
        //
        // La comparación normaliza los separadores a '/': en Android las
        // rutas son Unix, pero en tests JVM (Robolectric sobre Windows) son
        // `C:\...\editor\...`. Buscar solo "/editor/" daba falsos positivos
        // y volvía a copiar archivos que ya estaban en el sandbox.
        val normalized = uri.replace('\\', '/')
        val editorSegment = "/${MediaFileStore.EDITOR_DIR}/"
        return !normalized.contains(editorSegment)
    }
}
