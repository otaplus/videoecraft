package com.example.videoecraft

import android.app.Application
import android.util.Log
import androidx.media3.common.util.UnstableApi
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import com.example.videoecraft.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Punto de entrada de proceso de VideoEcraft.
 *
 * Configura:
 *  - El [ImageLoader] global de Coil con [VideoFrameDecoder], para que la
 *    timeline y la cuadrícula de proyectos muestren fotogramas extraídos
 *    directamente del video.
 *  - El [AppContainer] con la base de datos y los repositorios.
 *  - La **purga de medios huérfanos** al arrancar (ver [purgeOrphanMedia]).
 */
@UnstableApi
class VideoEcraftApp : Application(), SingletonImageLoader.Factory {

    /** Grafo de dependencias de la app (inyección manual). */
    lateinit var container: AppContainer
        private set

    /**
     * Scope de vida del proceso para tareas de mantenimiento.
     * No se cancela nunca: vive lo que vive la app.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        purgeOrphanMedia()
    }

    /**
     * Borra archivos de `filesDir/editor/` que ya no referencia ningún proyecto.
     *
     * Se lanza en segundo plano y **nunca debe bloquear el arranque**. Cubre
     * las fugas históricas: importaciones interrumpidas, versiones anteriores
     * sin borrado, o eliminaciones que fallaron a medias.
     */
    private fun purgeOrphanMedia() {
        appScope.launch {
            runCatching {
                val deleted = container.projectRepository.purgeOrphanMedia(this@VideoEcraftApp)
                if (deleted > 0) {
                    Log.i(TAG, "Purga de medios: $deleted archivo(s) huérfano(s) eliminados")
                }
            }.onFailure { error ->
                // Un fallo de limpieza no debe afectar al uso normal de la app.
                Log.w(TAG, "No se pudo purgar los medios huérfanos", error)
            }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()

    private companion object {
        const val TAG = "VideoEcraftApp"
    }
}
