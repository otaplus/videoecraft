package com.example.videoecraft.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de datos local de VideoEcraft.
 *
 * Guarda los proyectos y borradores. El esquema se exporta a `app/schemas/`
 * (configurado en `build.gradle.kts`) para poder escribir migraciones
 * versionadas y testearlas con `MigrationTestHelper`.
 */
@Database(
    entities = [ProjectEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class VideoEcraftDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao

    companion object {
        private const val DATABASE_NAME = "videoecraft.db"

        @Volatile
        private var instance: VideoEcraftDatabase? = null

        /** Instancia única del proceso. */
        fun getInstance(context: Context): VideoEcraftDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): VideoEcraftDatabase =
            Room.databaseBuilder(context, VideoEcraftDatabase::class.java, DATABASE_NAME)
                // `fallbackToDestructiveMigration` sólo mientras estemos en v1;
                // al publicar la 1.0 se sustituye por migraciones reales.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
