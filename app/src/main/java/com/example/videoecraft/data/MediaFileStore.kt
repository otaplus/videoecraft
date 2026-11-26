package com.example.videoecraft.data

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Gestiona los archivos de medios importados que viven en el sandbox de la app.
 *
 * Centraliza dónde se guardan (`filesDir/editor/`) y **cuándo se borran**.
 * Sin esta pieza, cada importación dejaba MP4 en disco para siempre: el
 * usuario borraba el proyecto desde Home y el archivo seguía ocupando sitio
 * sin ninguna vía de recuperarlo.
 *
 * Se separa de [MediaImporter] porque son responsabilidades distintas:
 * importar es "copiar al entrar", esto es "liberar al salir".
 */
@UnstableApi
object MediaFileStore {

    /** Subcarpeta dentro de `filesDir` donde viven los medios importados. */
    const val EDITOR_DIR = "editor"

    /**
     * Ruta base del almacenamiento de medios.
     *
     * Se fija una sola vez al primer uso y **no cambia en toda la vida del
     * proceso**. Una función `var` permite que los tests inyecten un
     * directorio temporal propio, eliminando la dependencia del `filesDir`
     * de Robolectric (que puede devolver rutas distintas entre llamadas).
     *
     * En producción nunca se modifica: `filesDir/editor/`.
     */
    @Volatile
    private var baseDir: File? = null

    /**
     * Carpeta de medios del editor. Se garantiza que existe.
     *
     * @param context solo se usa la primera vez, para resolver `filesDir`.
     */
    fun directory(context: Context): File {
        baseDir?.let { if (it.exists()) return it }
        return synchronized(this) {
            baseDir?.takeIf { it.exists() } ?: run {
                val dir = File(context.applicationContext.filesDir, EDITOR_DIR)
                dir.mkdirs()
                baseDir = dir
                dir
            }
        }
    }

    /**
     * Sobrescribe la carpeta de medios. **Solo para tests.**
     *
     * @param dir carpeta a usar, o `null` para volver a `filesDir/editor/`.
     */
    @androidx.annotation.VisibleForTesting
    internal fun overrideDirectoryForTest(dir: File?) {
        synchronized(this) {
            baseDir = dir?.also { it.mkdirs() }
        }
    }

    /**
     * Borra [uri] **solo si apunta a un archivo dentro de nuestro sandbox**.
     *
     * Es una guarda de seguridad: nunca se debe borrar un archivo del
     * usuario que viva en almacenamiento compartido o en otra app. Si la
     * URI no es `file://` dentro de `filesDir/editor/`, se ignora.
     *
     * @return `true` si la URI era válida y el archivo **ya no existe** al
     *   terminar (se borró ahora o ya no estaba). Un archivo ya ausente no
     *   es un error: el objetivo —liberar espacio— está cumplido, y así el
     *   recuento de limpieza refleja lo que realmente se ha resuelto.
     */
    suspend fun deleteIfSandboxed(context: Context, uri: String): Boolean =
        withContext(Dispatchers.IO) {
            val file = fileFromUri(uri) ?: return@withContext false
            val editorDir = directory(context)

            if (!isInside(file, editorDir)) return@withContext false

            runCatching {
                if (!file.exists()) return@runCatching true
                file.delete()
            }.getOrDefault(false)
        }

    /**
     * Convierte una URI `file://` en [File] sin depender de [Uri.path].
     *
     * Se evita `Uri.parse(uri).path` porque **pierde la letra de unidad en
     * Windows** (`file://C:\dir\a.mp4` → `\dir\a.mp4`), lo que hacía que
     * archivos válidos no se reconocieran como propios en los tests. En
     * Android real el bug no se manifiesta (rutas Unix), pero el código no
     * debe depender de esa casualidad.
     *
     * Devuelve `null` si la URI no es `file://` (ahí es donde se bloquea el
     * borrado de recursos ajenos: `content://`, `http://`, etc.).
     */
    private fun fileFromUri(uri: String): File? {
        if (!uri.startsWith(FILE_SCHEME)) return null
        val rawPath = uri.removePrefix(FILE_SCHEME)
        // Una URI `file://` sin ruta resolvería a `File("")`, que es el
        // directorio de trabajo: su padre podría coincidir con la carpeta
        // del editor y provocar borrados erróneos. Se descarta de raíz.
        if (rawPath.isBlank()) return null
        val file = File(rawPath)
        return file.takeIf { it.path.isNotBlank() && it.isAbsolute }
    }

    /** Expuesto para tests: resuelve una URI a su ruta canónica o `null`. */
    internal fun resolveForTest(uri: String): String? =
        fileFromUri(uri)?.let { runCatching { it.canonicalPath }.getOrNull() }

    /** Prefijo de las URIs de archivo local. */
    private const val FILE_SCHEME = "file://"

    /**
     * `true` si [file] está dentro de [dir].
     *
     * Implementación basada en [File.getCanonicalFile], que resuelve `..`,
     * `.` y enlaces simbólicos. Se eligió frente a [File.getCanonicalPath]
     * por simetría: ambos lados se canonicalizan igual, así que no hay
     * falsos negativos por separadores distintos (Android vs JVM).
     */
    private fun isInside(file: File, dir: File): Boolean = runCatching {
        val canonicalFile = file.canonicalFile
        val canonicalDir = dir.canonicalFile

        // Debe ser un hijo directo de la carpeta del editor: se compara el
        // directorio padre en lugar de usar `startsWith` sobre cadenas, que
        // daría falsos positivos con nombres que comparten prefijo.
        canonicalFile.parentFile == canonicalDir
    }.getOrDefault(false)

    /**
     * Borra todos los archivos cuyo nombre empiece por [projectKey].
     *
     * Los archivos se nombran `p<timestamp>_<uuid>.mp4`, así que el prefijo
     * identifica la importación (y por tanto el proyecto).
     */
    suspend fun deleteByProjectKey(context: Context, projectKey: String): Int =
        withContext(Dispatchers.IO) {
            directory(context)
                .listFiles()
                ?.filter { it.name.startsWith("${projectKey}_") }
                ?.count { runCatching { it.delete() }.getOrDefault(false) }
                ?: 0
        }

    /**
     * Tamaño total ocupado por los medios importados, en bytes.
     * Útil para mostrar el uso de disco al usuario.
     */
    suspend fun totalBytes(context: Context): Long = withContext(Dispatchers.IO) {
        directory(context).listFiles()?.sumOf { it.length() } ?: 0L
    }

    /**
     * Borra archivos huérfanos: los que no aparecen en [referencedUris].
     *
     * Se ejecuta al arrancar la app para limpiar lo que dejaron versiones
     * anteriores o importaciones interrumpidas. Es la red de seguridad que
     * garantiza que la fuga no vuelva por otra vía.
     *
     * @return número de archivos eliminados.
     */
    suspend fun purgeOrphans(context: Context, referencedUris: Set<String>): Int =
        withContext(Dispatchers.IO) {
            // Guarda de seguridad: si todavía no hay ningún proyecto en la
            // base de datos (primer arranque tras una reinstalación, o la
            // purga lanzada antes de que Room esté listo), **no se borra
            // nada**. Sin esto, un fallo de momento del arranque podía
            // vaciar todo el directorio de medios del usuario.
            //
            // El llamador debe pasar el conjunto de URIs referenciadas; si
            // está vacío porque de verdad no hay proyectos, la limpieza se
            // hará en el siguiente arranque con datos reales.
            if (referencedUris.isEmpty()) return@withContext 0

            // Se resuelve cada URI a ruta canónica con la misma función que
            // `deleteIfSandboxed`, para que ambos lados de la comparación
            // usen exactamente el mismo formato.
            val referencedPaths = referencedUris
                .mapNotNull { uri -> fileFromUri(uri)?.canonicalPath }
                .toSet()

            directory(context)
                .listFiles()
                ?.filter { it.canonicalPath !in referencedPaths }
                ?.count { runCatching { it.delete() }.getOrDefault(false) }
                ?: 0
        }
}
