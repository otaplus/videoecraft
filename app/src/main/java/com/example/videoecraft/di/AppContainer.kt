package com.example.videoecraft.di

import android.content.Context
import com.example.videoecraft.data.db.VideoEcraftDatabase
import com.example.videoecraft.data.repository.ProjectRepository

/**
 * Contenedor de dependencias de la aplicación (inyección manual).
 *
 * VideoEcraft no usa Hilt a propósito: con ~10 dependencias, un contenedor
 * manual es más ligero, no requiere KSP extra en el build y mantiene el
 * proyecto fácil de leer para quien llegue desde GitHub. Si el grafo crece
 * en la Fase 4, migrar a Hilt es mecánico.
 *
 * Vive en `Application` y dura lo que dura el proceso.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** Base de datos Room (singleton por proceso). */
    private val database: VideoEcraftDatabase by lazy {
        VideoEcraftDatabase.getInstance(appContext)
    }

    /** Repositorio de proyectos/borradores. */
    val projectRepository: ProjectRepository by lazy {
        ProjectRepository(database.projectDao())
    }
}
