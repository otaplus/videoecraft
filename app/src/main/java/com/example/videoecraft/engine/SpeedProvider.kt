package com.example.videoecraft.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi

/**
 * Factor de velocidad aplicable a un clip (`0.2x` … `4.0x`).
 *
 * Se modela como value class para validar el rango una sola vez y para
 * que el editor pueda persistirlo en Room sin ambigüedad.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@JvmInline
value class PlaybackSpeed(val factor: Float) {

    init {
        require(factor in MIN..MAX) { "PlaybackSpeed debe estar entre $MIN y $MAX (fue $factor)" }
    }

    val isNormal: Boolean get() = factor == 1f

    /** Etiqueta para la UI: "1x", "0.25x", "2x". */
    val label: String
        get() = if (factor == factor.toInt().toFloat()) "${factor.toInt()}x" else "${factor}x"

    companion object {
        const val MIN = 0.2f
        const val MAX = 4.0f
        val NORMAL = PlaybackSpeed(1f)

        /** Presets de la barra de Velocidad. */
        val PRESETS: List<PlaybackSpeed> = listOf(
            PlaybackSpeed(0.25f),
            PlaybackSpeed(0.5f),
            PlaybackSpeed(1f),
            PlaybackSpeed(1.5f),
            PlaybackSpeed(2f),
            PlaybackSpeed(3f),
            PlaybackSpeed(4f),
        )
    }
}

/**
 * [SpeedProvider] de Media3 que acelera o ralentiza un clip.
 *
 * Combina dos piezas:
 *
 *  1. **Video** — `SpeedProvider.getSpeed(timeUs)` informa al `Transformer`
 *     del factor vigente en cada instante. Aunque hoy la velocidad es
 *     constante por clip, la implementación es *time-aware*: en la Fase 3
 *     se podrán añadir rampas de velocidad (`getNextSpeedChangeTimeUs`)
 *     sin cambiar esta clase.
 *
 *  2. **Audio** — [SonicAudioProcessor] hace *time-stretching* sobre el PCM
 *     manteniendo el tono original. Sin esto, 2x sonaría a "chipmunk".
 *
 * @param speed factor de velocidad del clip.
 * @param preserveAudioPitch `true` mantiene el tono (recomendado);
 *   `false` aplica también el cambio de tono, como una cinta acelerada.
 */
@UnstableApi
class ClipSpeedProvider(
    val speed: PlaybackSpeed,
    private val preserveAudioPitch: Boolean = true,
) : SpeedProvider {

    override fun getSpeed(timeUs: Long): Float = speed.factor

    /**
     * Velocidad constante ⇒ no hay más cambios. Devolver
     * [C.TIME_UNSET] le indica a Media3 que cachee el valor.
     */
    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET

    /**
     * Audio processors que implementan el cambio de velocidad.
     *
     * `Transformer` los ejecuta antes de codificar el AAC. Con
     * [preserveAudioPitch] el pitch queda en 1.0 (tono intacto).
     */
    fun audioProcessors(): List<AudioProcessor> {
        if (speed.isNormal) return emptyList()

        return listOf(
            SonicAudioProcessor().apply {
                setSpeed(speed.factor)
                setPitch(if (preserveAudioPitch) 1f else speed.factor)
            },
        )
    }
}
