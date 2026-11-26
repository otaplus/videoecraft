package com.example.videoecraft.engine

import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.StaticOverlaySettings
import com.example.videoecraft.engine.model.BackgroundFill
import com.example.videoecraft.engine.model.EditSpec
import com.example.videoecraft.engine.model.OverlaySpec

/**
 * Layout multicapa de VideoEcraft para el `Transformer`.
 *
 * Media3 compone las secuencias de una `Composition` en orden: la #0 es el
 * **fondo** y las siguientes se dibujan encima. Esta clase decide, para
 * cada secuencia, su tamaño de salida y cómo se superpone:
 *
 * | Secuencia | Contenido            | Presentación                              |
 * |-----------|----------------------|-------------------------------------------|
 * | #0        | Pista principal      | Fondo a pantalla completa (scale-to-fit)  |
 * | #1..#N    | Overlays (PIP/croma) | Escalados y desplazados, respetando alfa  |
 * | #N+1..    | Música               | Sin video: no participa en la composición |
 *
 * > [!NOTE]
 * > `VideoCompositorSettings` es **Beta** en Media3 1.11. La firma que se
 * > usa aquí (`getOutputSize` + `getOverlaySettings`) es la estable dentro
 * > de la estabilidad Beta y sólo se usa en el camino de exportación.
 *
 * @param spec edición completa; determina qué overlays hay y su posición.
 * @param fallbackFps fps de salida, para el `Composition` cuando no hay
 *   overlays y Media3 necesita un tamaño único.
 */
@UnstableApi
class VideoEcraftCompositorSettings(
    private val spec: EditSpec,
    @Suppress("UNUSED_PARAMETER") private val fallbackFps: Int,
) : VideoCompositorSettings {

    /** Tamaño del lienzo de salida, calculado a partir del [EditSpec.canvas]. */
    private val outputSize: Size
        get() = spec.canvas.sizePx.let { (w, h) -> Size(w, h) }

    /**
     * Tamaño de cada secuencia de entrada.
     *
     * Todas se declaran al tamaño del lienzo para que el compositor no
     * reescale implícitamente: el escalado real lo hacen los efectos de
     * presentación por clip, que sí respetan la relación de aspecto.
     */
    override fun getOutputSize(inputSizes: List<Size>): Size = outputSize

    /**
     * Cómo se dibuja la secuencia [inputId] en el frame.
     *
     * @param inputId índice de la secuencia en la `Composition`.
     * @param presentationTimeUs instante del frame procesado.
     */
    override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
        // Secuencia #0: fondo a pantalla completa, opaco.
        if (inputId == FIRST_OVERLAY_INDEX - 1) {
            return StaticOverlaySettings.Builder()
                .setAlphaScale(1f)
                .setOverlayFrameAnchor(0f, 0f)
                .setBackgroundFrameAnchor(0f, 0f)
                .build()
        }

        // Overlays: se busca el modelo correspondiente (inputId - 1).
        val overlay: OverlaySpec = spec.overlays.getOrNull(inputId - FIRST_OVERLAY_INDEX)
            // Secuencia de audio (música): no tiene vídeo, alfa 0.
            ?: return StaticOverlaySettings.Builder().setAlphaScale(0f).build()

        return StaticOverlaySettings.Builder()
            .setAlphaScale(overlay.alpha)
            .setScale(overlay.scale, overlay.scale)
            .setRotationDegrees(overlay.rotationDegrees)
            // Ancla el centro del overlay al centro del frame…
            .setOverlayFrameAnchor(0f, 0f)
            // …y lo desplaza a su posición normalizada.
            .setBackgroundFrameAnchor(overlay.positionX, overlay.positionY)
            .build()
    }

    /**
     * Color de relleno del lienzo cuando el video no cubre todo el frame.
     *
     * Devuelve `null` para "desenfoque del propio video": en ese caso el
     * relleno se resuelve con [com.example.videoecraft.engine.effects.BlurBackgroundEffect]
     * aplicado a la secuencia principal.
     */
    internal fun backgroundColor(): Int? = when (val fill = spec.canvas.background) {
        is BackgroundFill.Solid -> fill.colorArgb
        BackgroundFill.Black -> 0xFF000000.toInt()
        is BackgroundFill.Blur -> null
    }

    companion object {
        /**
         * Índice de la primera secuencia de overlay. La #0 siempre es la
         * pista principal, tal y como la construye `VideoProcessor`.
         */
        const val FIRST_OVERLAY_INDEX = 1
    }
}
