package com.example.videoecraft.data.repository

import android.content.Context
import androidx.media3.common.util.UnstableApi
import com.example.videoecraft.data.MediaFileStore
import com.example.videoecraft.data.db.ProjectDao
import com.example.videoecraft.data.db.ProjectEntity
import com.example.videoecraft.engine.model.EditSpec
import com.example.videoecraft.export.EditSpecCodec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Fuente de verdad de los proyectos.
 *
 * Traduce entre [ProjectEntity] (fila de Room, con el spec serializado) y
 * [EditSpec] (modelo del motor), para que ni la UI ni el motor conozcan el
 * formato de almacenamiento.
 */
@UnstableApi
class ProjectRepository(
    private val projectDao: ProjectDao,
    /**
     * Dispatcher de las operaciones de disco y base de datos.
     *
     * Es inyectable para que los tests puedan pasar un dispatcher
     * determinista: `Dispatchers.IO` no lo controla `runTest`, así que las
     * corrutinas podían quedar en vuelo cuando el test terminaba y el
     * `@After` borraba su directorio temporal.
     */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /** Proyectos recientes ya deserializados, listos para pintar. */
    fun observeProjects(): Flow<List<Project>> =
        projectDao.observeRecentProjects().map { entities ->
            entities.mapNotNull { it.toProject() }
        }

    /** Número de proyectos guardados (para el estado vacío de Home). */
    fun observeProjectCount(): Flow<Int> = projectDao.observeCount()

    /** Carga un proyecto completo por id, o `null` si no existe. */
    suspend fun getProject(id: Long): Project? = withContext(ioDispatcher) {
        projectDao.getProject(id)?.toProject()
    }

    /**
     * Crea un proyecto nuevo a partir de un [EditSpec] y devuelve su id.
     *
     * @param name nombre visible en Home.
     * @param spec edición inicial (normalmente los clips importados).
     */
    suspend fun createProject(name: String, spec: EditSpec): Long =
        withContext(ioDispatcher) {
            projectDao.insert(
                ProjectEntity(
                    name = name,
                    thumbnailUri = spec.clips.firstOrNull()?.uri,
                    durationMs = spec.durationMs,
                    clipCount = spec.clips.size,
                    editSpecJson = EditSpecCodec.encode(spec),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }

    /**
     * Guarda el estado actual del editor (autoguardado y "Guardar borrador").
     *
     * Actualiza también las columnas denormalizadas (miniatura, duración,
     * número de clips) para que Home las pinte sin deserializar el spec.
     */
    suspend fun saveProject(id: Long, spec: EditSpec, name: String? = null) =
        withContext(ioDispatcher) {
            val current = projectDao.getProject(id) ?: return@withContext
            projectDao.update(
                current.copy(
                    name = name ?: current.name,
                    thumbnailUri = spec.clips.firstOrNull()?.uri ?: current.thumbnailUri,
                    durationMs = spec.durationMs,
                    clipCount = spec.clips.size,
                    editSpecJson = EditSpecCodec.encode(spec),
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            Unit
        }

    /**
     * Elimina un proyecto y **todos los archivos de medios** que importó.
     *
     * El orden importa: se lee la lista de archivos del spec *antes* de
     * borrar la fila de Room, porque después ya no habría forma de saber
     * qué MP4 pertenecían al proyecto y quedarían huérfanos en el sandbox
     * consumiendo espacio del usuario para siempre.
     *
     * @return número de archivos de medios eliminados.
     */
    suspend fun deleteProjectWithMedia(context: Context, id: Long): Int =
        withContext(ioDispatcher) {
            // 1) Leer el spec para conocer todos los archivos referenciados.
            val project = projectDao.getProject(id)
            val mediaUris = project?.let { mediaUrisOf(it) }.orEmpty()

            // 2) Borrar la fila.
            projectDao.deleteById(id)

            // 3) Borrar los archivos del sandbox.
            var deleted = 0
            mediaUris.forEach { uri ->
                if (MediaFileStore.deleteIfSandboxed(context, uri)) deleted++
            }
            deleted
        }

    /** Elimina solo la fila (mantiene los archivos). Uso interno/tests. */
    suspend fun deleteProject(id: Long) = withContext(ioDispatcher) {
        projectDao.deleteById(id)
    }

    /**
     * Extrae todas las URIs de medios de un proyecto: clips, overlays y música.
     * Tolerante a JSON corrupto (`EditSpecCodec.decode` devuelve `null`).
     */
    private fun mediaUrisOf(entity: ProjectEntity): List<String> {
        val spec = EditSpecCodec.decode(entity.editSpecJson) ?: return emptyList()
        return buildList {
            spec.clips.mapTo(this) { it.uri }
            spec.overlays.mapTo(this) { it.uri }
            spec.music.mapTo(this) { it.uri }
        }.distinct()
    }

    /**
     * Borra archivos de medios huérfanos del sandbox.
     *
     * Recorre todos los specs guardados, reúne las URIs referenciadas y
     * elimina cualquier archivo de `filesDir/editor/` que no aparezca en
     * esa lista. Es la red de seguridad contra fugas: cubre importaciones
     * interrumpidas, versiones anteriores y borrados que fallaron a medias.
     *
     * Se ejecuta al arrancar la app, fuera del camino crítico de la UI.
     *
     * @return número de archivos eliminados.
     */
    suspend fun purgeOrphanMedia(context: Context): Int = withContext(ioDispatcher) {
        val referenced = projectDao.getAllEditSpecs()
            .flatMap { json ->
                val spec = EditSpecCodec.decode(json) ?: return@flatMap emptyList()
                buildList {
                    spec.clips.mapTo(this) { it.uri }
                    spec.overlays.mapTo(this) { it.uri }
                    spec.music.mapTo(this) { it.uri }
                }
            }
            .toSet()

        MediaFileStore.purgeOrphans(context, referenced)
    }

    /** Espacio ocupado por los medios importados, en bytes. */
    suspend fun importedMediaBytes(context: Context): Long =
        MediaFileStore.totalBytes(context)

    /** Renombra un proyecto sin tocar el spec. */
    suspend fun renameProject(id: Long, newName: String) = withContext(ioDispatcher) {
        val current = projectDao.getProject(id) ?: return@withContext
        projectDao.update(current.copy(name = newName, updatedAt = System.currentTimeMillis()))
        Unit
    }
}

/**
 * Proyecto con el spec ya deserializado, listo para la UI.
 *
 * @param id identificador en Room.
 * @param name nombre visible.
 * @param thumbnailUri URI de la miniatura para la tarjeta de Home.
 * @param durationMs duración total, para mostrar en la tarjeta.
 * @param clipCount número de clips.
 * @param updatedAt epoch millis de la última edición.
 * @param spec edición completa cargada en memoria.
 */
data class Project(
    val id: Long,
    val name: String,
    val thumbnailUri: String?,
    val durationMs: Long,
    val clipCount: Int,
    val updatedAt: Long,
    val spec: EditSpec,
)

/** Convierte la fila de Room al modelo de dominio; `null` si el JSON está corrupto. */
private fun ProjectEntity.toProject(): Project? {
    val spec = EditSpecCodec.decode(editSpecJson) ?: return null
    return Project(
        id = id,
        name = name,
        thumbnailUri = thumbnailUri,
        durationMs = durationMs,
        clipCount = clipCount,
        updatedAt = updatedAt,
        spec = spec,
    )
}
