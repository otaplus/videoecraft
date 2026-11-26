package com.example.videoecraft.engine

import android.content.Context
import android.graphics.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import androidx.core.net.toUri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.example.videoecraft.engine.effects.ChromaKeyEffect
import com.example.videoecraft.engine.effects.ColorFilterEffect
import com.example.videoecraft.engine.model.ClipSpec
import com.example.videoecraft.engine.model.EditSpec
import com.example.videoecraft.engine.model.ExportBitrate
import com.example.videoecraft.engine.model.ExportFps
import com.example.videoecraft.engine.model.MusicSpec
import com.example.videoecraft.engine.model.OverlaySpec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/** Progreso del export emitido hacia la UI. */
@UnstableApi
sealed interface ExportProgress {
    /** Avance normalizado 0..1 y estimación de tiempo restante. */
    data class Running(val progress: Float, val etaMs: Long? = null) : ExportProgress

    /** Exportación completada; [file] vive en `cacheDir/exports/`. */
    data class Completed(val file: File, val durationMs: Long) : ExportProgress

    /** Error irrecuperable. */
    data class Failed(val message: String, val cause: Throwable? = null) : ExportProgress
}

/**
 * Motor de exportación de VideoEcraft.
 *
 * Traduce un [EditSpec] (modelo puro y testeable) a una [Composition] de
 * Media3 y la ejecuta con [Transformer]. Toda la composición se hace en GPU
 * con los efectos de [com.example.videoecraft.engine.effects]:
 *
 * ```
 *  EditSpec
 *     │
 *     ├─ clips    → EditedMediaItemSequence #0   (pista principal)
 *     ├─ overlays → EditedMediaItemSequence #1…N (PIP / croma)
 *     └─ music    → EditedMediaItemSequence #N+1 (audio de fondo en loop)
 *                          │
 *                          ▼
 *                      Composition ──► Transformer ──► MP4 en caché
 * ```
 *
 * ### Decisiones de diseño
 * - **Executor propio de un hilo**: `Transformer` levanta su propio GlThread;
 *   separarlo evita bloquear `Dispatchers.Default` durante exports 4K.
 * - **`callbackFlow`**: el progreso viaja como `Flow`; cancelar la
 *   recolección aborta el export vía `Transformer.cancel()` en `awaitClose`.
 * - **Salida en caché**: el MP4 se escribe en `cacheDir/exports/`. La
 *   publicación en `Movies/VideoEcraft` la hace `MediaStoreSaver`, lo que
 *   permite reintentar sin re-codificar.
 */
@UnstableApi
class VideoProcessor(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * Hilo dedicado con **Looper** para el `Transformer`.
     *
     * Media3 exige que el `Transformer` se use siempre desde el hilo de su
     * `Looper`. Un `Executors.newSingleThreadExecutor` **no** tiene Looper,
     * así que construir el Transformer ahí lanza
     * `"Transformer is accessed on the wrong thread."`.
     *
     * `HandlerThread` sí prepara un Looper, y `asCoroutineDispatcher()` lo
     * entrega como dispatcher de corrutinas: así `start`, `getProgress`,
     * `cancel` y `release` ocurren todos en el mismo hilo con Looper.
     */
    private val transformerThread = HandlerThread("videoecraft-transformer").apply {
        start()
    }

    private val transformerExecutor: CoroutineDispatcher =
        Handler(transformerThread.looper).asCoroutineDispatcher()

    private val activeTransformer = AtomicReference<Transformer?>(null)

    /**
     * Exporta la edición descrita por [spec] y emite progreso.
     *
     * El flujo termina con [ExportProgress.Completed] o
     * [ExportProgress.Failed]. Cancelar la recolección aborta el export.
     */
    fun export(
        spec: EditSpec,
        fps: ExportFps = ExportFps.FPS30,
        @Suppress("UNUSED_PARAMETER") bitrate: ExportBitrate = ExportBitrate.MEDIUM,
    ): Flow<ExportProgress> = callbackFlow {
        if (!spec.hasContent) {
            trySend(ExportProgress.Failed("El proyecto no tiene clips para exportar."))
            close()
            return@callbackFlow
        }

        val outputFile = createOutputFile()

        launch {
            withContext(transformerExecutor) {
                val composition = buildComposition(spec, fps)

                val listener = object : Transformer.Listener {
                    override fun onCompleted(
                        composition: Composition,
                        exportResult: ExportResult,
                    ) {
                        trySend(
                            ExportProgress.Completed(
                                file = outputFile,
                                durationMs = exportResult.durationMs,
                            ),
                        )
                        close()
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        trySend(
                            ExportProgress.Failed(
                                message = exportException.message ?: "Error de exportación",
                                cause = exportException,
                            ),
                        )
                        close()
                    }
                }

                val transformer = Transformer.Builder(context)
                    // El Builder admite un Looper explícito; si se omite usa
                    // el del hilo que construye, que es justo el que
                    // necesitamos para que `getProgress` y `cancel` sean
                    // válidos desde el mismo hilo.
                    .setLooper(Looper.myLooper() ?: Looper.getMainLooper())
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(listener)
                    .build()

                activeTransformer.set(transformer)

                //
                // IMPORTANTE: Media3 exige que TODAS las llamadas a un
                // `Transformer` ocurran en el mismo hilo que su Looper.
                // Por eso el sondeo de progreso se hace también desde
                // este dispatcher y NO desde `ioDispatcher`:
                // de lo contrario lanza "Transformer is accessed on the
                // wrong thread."
                //
                val progressPump = launch(transformerExecutor) {
                    val holder = ProgressHolder()
                    var last = -1
                    while (true) {
                        val state = transformer.getProgress(holder)
                        if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                            val p = holder.progress
                            if (p != last) {
                                last = p
                                trySend(
                                    ExportProgress.Running(
                                        progress = p / 100f,
                                        etaMs = estimateEta(spec.durationMs, p),
                                    ),
                                )
                            }
                        }
                        delay(PROGRESS_POLL_MS)
                    }
                }

                try {
                    // `start` es asíncrono: el listener de arriba cierra el
                    // canal cuando termina o falla.
                    transformer.start(composition, outputFile.absolutePath)
                    awaitCompletion(outputFile)
                } finally {
                    progressPump.cancel()
                }
            }
        }

        awaitClose {
            // `cancel()` debe ejecutarse en el hilo del Looper del Transformer
            // (el mismo donde se construyó), no en el hilo que cierra el flujo.
            val transformer = activeTransformer.getAndSet(null) ?: return@awaitClose
            CoroutineScope(transformerExecutor).launch {
                runCatching { transformer.cancel() }
            }
        }
    }.flowOn(ioDispatcher)

    // -----------------------------------------------------------------
    //  Construcción de la Composition
    // -----------------------------------------------------------------

    /** Convierte el [EditSpec] en la [Composition] que consume el Transformer. */
    private fun buildComposition(spec: EditSpec, fps: ExportFps): Composition {
        val sequences = mutableListOf<EditedMediaItemSequence>()

        // Pista principal (#0)
        sequences += buildMainSequence(spec)

        // Superposiciones (PIP / croma)
        spec.overlays.forEach { sequences += buildOverlaySequence(it) }

        // Audio de fondo en loop
        spec.music.forEach { sequences += buildMusicSequence(it) }

        // Efectos a nivel de Composition: fija el tamaño y el fps finales
        // del lienzo, independientemente de la resolución de cada clip.
        val (width, height) = spec.canvas.sizePx
        val compositionEffects = Effects(
            emptyList(),
            listOf<Effect>(
                Presentation.createForWidthAndHeight(
                    width,
                    height,
                    Presentation.LAYOUT_SCALE_TO_FIT,
                ),
            ),
        )

        val builder = Composition.Builder(sequences).setEffects(compositionEffects)

        //
        // `VideoCompositorSettings` SOLO es válido cuando la Composition tiene
        // más de una secuencia de VÍDEO (PIP/croma): ahí Media3 monta el
        // video graph multi-entrada y sí aplica el compositor.
        //
        // Con una sola secuencia de video Media3 usa `SingleInputVideoGraph`,
        // que no tiene compositor y lanza:
        //   IllegalArgumentException: SingleInputVideoGraph does not use
        //   VideoCompositor, and therefore cannot apply VideoCompositorSettings
        //
        // Por eso se calcula si hay varias secuencias que aporten vídeo y,
        // solo entonces, se registra el compositor.
        //
        val hasMultipleVideoSequences = spec.overlays.isNotEmpty()
        if (hasMultipleVideoSequences) {
            builder.setVideoCompositorSettings(
                VideoEcraftCompositorSettings(spec, fallbackFps = fps.fps),
            )
        }

        return builder.build()
    }

    /** Secuencia #0: clips de video en orden con sus efectos por clip. */
    private fun buildMainSequence(spec: EditSpec): EditedMediaItemSequence {
        val items = spec.clips.map { clip -> buildMainItem(clip) }
        return EditedMediaItemSequence.Builder(items).build()
    }
    /**
     * Construye el `EditedMediaItem` de un clip principal aplicando:
     *  1. Recorte (`ClippingConfiguration`).
     *  2. Ajustes de color / filtro.
     *  3. Transición de entrada.
     *  4. Velocidad (video) + velocidad con pitch correction (audio).
     */
    private fun buildMainItem(clip: ClipSpec): EditedMediaItem {
        val mediaItem = MediaItem.Builder()
            .setUri(clip.uri.toUri())
            .apply { setClipping(clip.sourceStartMs, clip.sourceEndMs) }
            .build()

        val videoEffects = mutableListOf<Effect>()

        if (!clip.color.isNeutral) {
            videoEffects += ColorFilterEffect(clip.color)
        }
        videoEffects += clip.transitionIn.toEffects(clip.transitionDurationMs)

        val speedProvider = ClipSpeedProvider(clip.speed)

        return EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(speedProvider.audioProcessors(), videoEffects))
            .setSpeed(speedProvider)
            .setFrameRate(30)
            .setRemoveAudio(!clip.keepOriginalAudio)
            .build()
    }

    /**
     * Secuencia de superposición (PIP o croma).
     *
     * El orden de efectos importa: el Chroma Key debe ejecutarse **antes**
     * de escalar y posicionar, para que la detección de color trabaje sobre
     * los píxeles originales y no sobre los interpolados.
     */
    private fun buildOverlaySequence(overlay: OverlaySpec): EditedMediaItemSequence {
        val mediaItem = MediaItem.Builder()
            .setUri(overlay.uri.toUri())
            .apply { setClipping(overlay.startMs, overlay.endMs) }
            .build()

        val videoEffects = mutableListOf<Effect>()

        // 1) Chroma Key
        overlay.chromaKey?.let { videoEffects += ChromaKeyEffect(it) }

        // 2) Color
        if (!overlay.color.isNeutral) {
            videoEffects += ColorFilterEffect(overlay.color)
        }

        // 3) Escala + rotación
        videoEffects += ScaleAndRotateTransformation.Builder()
            .setScale(overlay.scale, overlay.scale)
            .setRotationDegrees(overlay.rotationDegrees)
            .build()

        // 4) Posición en el lienzo
        videoEffects += overlayTranslation(overlay)

        val item = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(emptyList<AudioProcessor>(), videoEffects))
            .setRemoveAudio(true)
            .build()

        return EditedMediaItemSequence.Builder(listOf(item)).build()
    }

    /** Traslación del overlay en coordenadas NDC mediante `MatrixTransformation`. */
    private fun overlayTranslation(overlay: OverlaySpec): MatrixTransformation =
        MatrixTransformation {
            Matrix().apply {
                // positionX/Y son -1..1; postTranslate trabaja en NDC.
                postTranslate(overlay.positionX, overlay.positionY)
            }
        }

    /**
     * Secuencia de audio en loop que cubre la duración del video.
     *
     * @param music pista de música con su volumen.
     */
    private fun buildMusicSequence(music: MusicSpec): EditedMediaItemSequence {
        val mediaItem = MediaItem.Builder()
            .setUri(music.uri.toUri())
            .apply { setClipping(music.trimStartMs, music.trimEndMs) }
            .build()

        val audioProcessors: List<AudioProcessor> = if (music.volume < 1f) {
            listOf(
                ChannelMixingAudioProcessor().apply {
                    putChannelMixingMatrix(
                        ChannelMixingMatrix.createForConstantGain(
                            /* inputChannelCount= */ 2,
                            /* outputChannelCount= */ 2,
                        ).scaleBy(music.volume),
                    )
                },
            )
        } else {
            emptyList()
        }

        val item = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(audioProcessors, emptyList<Effect>()))
            .setRemoveVideo(true)
            .build()

        return EditedMediaItemSequence.Builder(listOf(item))
            .setIsLooping(music.loop)
            .build()
    }

    // -----------------------------------------------------------------
    //  Utilidades
    // -----------------------------------------------------------------

    /** Aplica el recorte si alguno de los bordes está definido. */
    private fun MediaItem.Builder.setClipping(startMs: Long, endMs: Long): MediaItem.Builder {
        val hasStart = startMs > 0L
        val hasEnd = endMs >= 0L
        if (!hasStart && !hasEnd) return this

        return setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .apply {
                    if (hasStart) setStartPositionMs(startMs)
                    if (hasEnd) setEndPositionMs(endMs)
                }
                .build(),
        )
    }

    /** Crea un archivo de salida único en `cacheDir/exports/`. */
    private fun createOutputFile(): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        return File(dir, "videoecraft_${System.currentTimeMillis()}.mp4")
    }

    /** Estimación lineal del tiempo restante, en ms. */
    private fun estimateEta(totalDurationMs: Long, progressPercent: Int): Long? {
        if (progressPercent <= 0) return null
        return (totalDurationMs * (100 - progressPercent) / progressPercent).coerceAtLeast(0L)    }

    /**
     * Mantiene viva la corrutina hasta que el Transformer cierre el canal
     * o la colección se cancele.
     */
    private suspend fun awaitCompletion(outputFile: File) {
        while (activeTransformer.get() != null) {
            delay(PROGRESS_POLL_MS)
        }
        // Si el export falló, el archivo parcial no sirve.
        if (outputFile.exists() && outputFile.length() == 0L) {
            outputFile.delete()
        }
    }

    /** Cancela el export en curso (botón "Cancelar" de la UI). */
    fun cancel() {
        activeTransformer.getAndSet(null)?.let { transformer ->
            runCatching { transformer.cancel() }
        }
    }

    /** Libera el executor propio del procesador. */
    fun release() {
        cancel()
        transformerThread.quitSafely()
    }

    private companion object {
        const val PROGRESS_POLL_MS = 200L
    }
}
