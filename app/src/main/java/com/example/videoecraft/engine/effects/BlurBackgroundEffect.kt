package com.example.videoecraft.engine.effects

import android.content.Context
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/**
 * Efecto "Fondo · Desenfoque del video" del lienzo.
 *
 * Cuando el clip no cubre toda la relación de aspecto elegida (por ejemplo
 * un video horizontal en un lienzo 9:16 para Reels), YouCut rellena los
 * bordes con una copia desenfocada del propio video. Este efecto hace
 * exactamente eso con **dos pasadas separables** en la GPU:
 *
 * ```
 *  entrada ──► [blur H] ──► [blur V] ──► salida desenfocada
 * ```
 *
 * Dos pasadas 1D son O(2N) frente a O(N²) de un blur 2D en un solo pase,
 * y el resultado visual es indistinguible para un fondo.
 *
 * @param radius radio del kernel en texeles (8..40 da un fondo suave).
 */
@UnstableApi
class BlurBackgroundEffect(
    private val radius: Float = 20f,
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        BlurShaderProgram(
            radius = radius,
            direction = 1f to 0f, // pasada horizontal
            useHighPrecisionColorComponents = useHdr,
        )
}

/**
 * Variante de dos pasadas: la primera se aplica horizontalmente y la
 * segunda verticalmente. Se exponen como dos `GlEffect` para que el
 * `Transformer` las encadene dentro del mismo `Effects`.
 */
@UnstableApi
fun createSeparableBlur(radius: Float = 20f): List<GlEffect> = listOf(
    TwoPassBlurEffect(radius, horizontal = true),
    TwoPassBlurEffect(radius, horizontal = false),
)

/** Un único pase de blur, horizontal o vertical. */
@UnstableApi
private class TwoPassBlurEffect(
    private val radius: Float,
    private val horizontal: Boolean,
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        BlurShaderProgram(
            radius = radius,
            direction = if (horizontal) 1f to 0f else 0f to 1f,
            useHighPrecisionColorComponents = useHdr,
        )
}

/** [GlShaderProgram] que ejecuta [Shaders.BLUR]. */
@UnstableApi
private class BlurShaderProgram(
    private val radius: Float,
    private val direction: Pair<Float, Float>,
    useHighPrecisionColorComponents: Boolean,
) : BaseVideoShaderProgram(
    fragmentShader = Shaders.BLUR,
    useHighPrecisionColorComponents = useHighPrecisionColorComponents,
    texturePoolCapacity = 1,
) {
    override fun setUniforms(program: GlProgram, presentationTimeUs: Long) {
        // Tamaño de un texel en UV, necesario para muestrear vecinos.
        val texelWidth = if (inputWidth > 0) 1f / inputWidth else 0f
        val texelHeight = if (inputHeight > 0) 1f / inputHeight else 0f

        program.setFloatsUniform("uTexelSize", floatArrayOf(texelWidth, texelHeight))
        program.setFloatsUniform("uDirection", floatArrayOf(direction.first, direction.second))
        program.setFloatUniform("uRadius", radius)
    }
}
