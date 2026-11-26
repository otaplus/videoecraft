package com.example.videoecraft.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Acceso a los proyectos/borradores.
 *
 * Todas las consultas reactivas devuelven [Flow] para que Home repinte la
 * cuadrícula automáticamente al guardar un borrador desde el editor.
 */
@Dao
interface ProjectDao {

    /** Proyectos recientes, los más nuevos primero. Alimenta la cuadrícula. */
    @Query("SELECT * FROM projects ORDER BY updated_at DESC")
    fun observeRecentProjects(): Flow<List<ProjectEntity>>

    /** Igual que [observeRecentProjects] pero como consulta puntual. */
    @Query("SELECT * FROM projects ORDER BY updated_at DESC")
    suspend fun getRecentProjects(): List<ProjectEntity>

    /** Proyecto por id, o `null` si ya fue borrado. */
    @Query("SELECT * FROM projects WHERE id = :id")
    fun observeProject(id: Long): Flow<ProjectEntity?>

    /** Lectura puntual de un proyecto. */
    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProject(id: Long): ProjectEntity?

    /** Inserta un proyecto nuevo y devuelve su id autogenerado. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(project: ProjectEntity): Long

    /** Actualiza un proyecto existente (guardado de borrador). */
    @Update
    suspend fun update(project: ProjectEntity)

    /** Inserta o reemplaza. Útil para el autoguardado del editor. */
    @Upsert
    suspend fun upsert(project: ProjectEntity): Long

    /** Borra un proyecto concreto. */
    @Delete
    suspend fun delete(project: ProjectEntity)

    /** Borra por id, sin necesidad de cargar la fila antes. */
    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Cuántos proyectos hay (para el estado vacío de Home). */
    @Query("SELECT COUNT(*) FROM projects")
    fun observeCount(): Flow<Int>

    /**
     * Todos los `edit_spec_json` guardados.
     *
     * Lo usa la purga de archivos huérfanos al arrancar: se necesita saber
     * qué medios siguen referenciados por algún proyecto antes de borrar
     * archivos del sandbox que no aparezcan en esa lista.
     */
    @Query("SELECT edit_spec_json FROM projects")
    suspend fun getAllEditSpecs(): List<String>
}
