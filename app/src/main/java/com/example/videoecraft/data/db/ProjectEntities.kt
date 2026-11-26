package com.example.videoecraft.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Proyecto / borrador de edición.
 *
 * [editSpecJson] guarda el [com.example.videoecraft.engine.model.EditSpec]
 * completo serializado con `EditSpecCodec`. Se eligió una sola columna de
 * texto frente a normalizar clips/overlays/textos en tablas separadas
 * porque el spec **siempre se lee y escribe completo** (el editor carga el
 * proyecto entero); normalizarlo sólo añadiría JOINs y complejidad de
 * migración sin ningún beneficio real.
 *
 * @param id clave primaria autogenerada.
 * @param name nombre visible en la cuadrícula de Home.
 * @param thumbnailUri URI de la miniatura (primer clip) para la tarjeta.
 * @param durationMs duración total, denormalizada para pintar la tarjeta
 *   sin deserializar el spec.
 * @param clipCount número de clips, ídem para la tarjeta.
 * @param editSpecJson el `EditSpec` serializado.
 * @param updatedAt epoch millis de la última edición; ordena la cuadrícula.
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "thumbnail_uri")
    val thumbnailUri: String? = null,

    @ColumnInfo(name = "duration_ms")
    val durationMs: Long = 0L,

    @ColumnInfo(name = "clip_count")
    val clipCount: Int = 0,

    @ColumnInfo(name = "edit_spec_json")
    val editSpecJson: String,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
)
