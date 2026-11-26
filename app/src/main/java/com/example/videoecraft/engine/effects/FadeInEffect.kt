package com.example.videoecraft.engine.effects

import android.content.Context
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/**
 * Fundido de entrada dependiente del tiempo.
 *
 * Se usa como "transición" al inicio de un clip entrante:
 *
 *  - [fromBlack] = `true` → **Fundido a negro**: el RGB se multiplica por
 *    un factor que va de 0 a 1, simulando salir de negro. No toca el alfa.
 *  - [fromBlack] = `false` → **Disolución**: se anima el canal alfa de 0 a 1,
 *    dejando que la secuencia inferior (el clip anterior) se vea a través.
 *
 * Ambas ramas comparten shader; el uniforme `uFadeFromBlack` selecciona
 * el modo en la GPU sin recompilar programas distintos.
 *
 * @param durationUs duración del fundido en microsegundos.
 * @param fromBlack  modo negro (RGB) vs. modo alfa (disolución).
 */
@UnstableApi
class FadeInEffect(
    private val durationUs: Long,
    private val fromBlack: Boolean,
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        FadeInShaderProgram(
            durationUs = durationUs,
            fromBlack = fromBlack,
            useHighPrecisionColorComponents = useHdr,
        )
}

/** [GlShaderProgram] que ejecuta [Shaders.FADE]. */
@UnstableApi
private class FadeInShaderProgram(
    private val durationUs: Long,
    private val fromBlack: Boolean,
    useHighPrecisionColorComponents: Boolean,
) : BaseVideoShaderProgram(
    fragmentShader = Shaders.FADE,
    useHighPrecisionColorComponents = useHighPrecisionColorComponents,
    texturePoolCapacity = 1,
) {
    override fun setUniforms(program: GlProgram, presentationTimeUs: Long) {
        val progress = if (durationUs <= 0L) {
            1f
        } else {
            (presentationTimeUs.toFloat() / durationUs.toFloat()).coerceIn(0f, 1f)
        }
        program.setFloatUniform("uProgress", progress)
        program.setFloatUniform("uFromBlack", if (fromBlack) 1f else 0f)
    }
}
