package com.example.videoecraft.engine.effects

import android.content.Context
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/**
 * Configuración inmutable de un Chroma Key.
 *
 * Los tres parámetros replican (y amplían) los controles de YouCut:
 *
 * @param keyColor        color a eliminar, en [0f, 1f] por canal RGB.
 * @param similarity      umbral de tolerancia (0..1). Valores bajos recortan
 *                        solo el color exacto; altos recortan sombras del
 *                        fondo. Mapeado al slider "*Similitud*".
 * @param smoothness      suavizado del borde (0..1). Mapeado a "*Suavizado*".
 * @param spillReduction  (0..1) cuánto se neutraliza el tinte residual del
 *                        color clave sobre el sujeto ("spill suppression").
 */
@androidx.annotation.OptIn(UnstableApi::class)
data class ChromaKeySettings(
    val keyColor: Triple<Float, Float, Float> = Triple(0f, 1f, 0f), // verde puro
    val similarity: Float = 0.35f,
    val smoothness: Float = 0.08f,
    val spillReduction: Float = 0.60f,
) {
    init {
        require(similarity in 0f..1f) { "similarity debe estar en 0..1" }
        require(smoothness in 0f..1f) { "smoothness debe estar en 0..1" }
        require(spillReduction in 0f..1f) { "spillReduction debe estar en 0..1" }
    }

    val keyColorArray: FloatArray
        get() = floatArrayOf(keyColor.first, keyColor.second, keyColor.third)

    companion object {
        /** Verde de pantalla croma estándar (#00FF00). */
        val GREEN: ChromaKeySettings = ChromaKeySettings(
            keyColor = Triple(0f, 1f, 0f),
        )

        /** Azul de pantalla croma (#0000FF). */
        val BLUE: ChromaKeySettings = ChromaKeySettings(
            keyColor = Triple(0f, 0f, 1f),
        )
    }
}

/**
 * Efecto Chroma Key de VideoEcraft.
 *
 * Implementa [GlEffect] para que pueda usarse indistintamente en:
 *  - **Previsualización**: `ExoPlayer.setVideoEffects(listOf(effect))`
 *  - **Exportación**: `Effects(audioProcessors, listOf(effect))`
 *
 * El fragment shader hace las tres pasadas clásicas de un keyer
 * profesional (ver [Shaders.CHROMA_KEY]):
 *  1. **Detección** por distancia de croma en YUV (ignora luminancia).
 *  2. **Alfa continuo** con `smoothstep` → sin dientes de sierra.
 *  3. **Spill suppression** → sin halos verdes en el pelo/bordes.
 */
@UnstableApi
class ChromaKeyEffect(
    private val settings: ChromaKeySettings,
) : GlEffect {

    constructor() : this(ChromaKeySettings.GREEN)

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        ChromaKeyShaderProgram(settings, useHighPrecisionColorComponents = useHdr)
}

/** [GlShaderProgram] que ejecuta [Shaders.CHROMA_KEY]. */
@UnstableApi
private class ChromaKeyShaderProgram(
    private val settings: ChromaKeySettings,
    useHighPrecisionColorComponents: Boolean,
) : BaseVideoShaderProgram(
    fragmentShader = Shaders.CHROMA_KEY,
    useHighPrecisionColorComponents = useHighPrecisionColorComponents,
    texturePoolCapacity = 1,
) {
    private val keyColor = settings.keyColorArray

    override fun setUniforms(program: GlProgram, presentationTimeUs: Long) {
        program.setFloatsUniform("uKeyColor", keyColor)
        program.setFloatUniform("uSimilarity", settings.similarity)
        program.setFloatUniform("uSmoothness", settings.smoothness)
        program.setFloatUniform("uSpillReduction", settings.spillReduction)
    }
}
