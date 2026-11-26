package com.example.videoecraft.engine.effects

import android.content.Context
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram

/**
 * Ajustes manuales de color del panel "Filtros y Ajustes".
 *
 * Todos los valores son **neutros en 0f**, salvo [saturation] (1f = neutro),
 * de modo que [ColorAdjustments.NEUTRAL] no altera la imagen en absoluto.
 *
 * @param brightness  -1..1 · suma offset al RGB.
 * @param contrast    0..2 · 1 = neutro.
 * @param saturation  0..2 · 1 = neutro, 0 = escala de grises.
 * @param warmth      -1..1 · positivo = cálido (más rojo, menos azul).
 * @param tint        -1..1 · negativo = verde, positivo = magenta.
 */
@androidx.annotation.OptIn(UnstableApi::class)
data class ColorAdjustments(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val warmth: Float = 0f,
    val tint: Float = 0f,
) {
    /** `true` cuando los ajustes son neutros y el efecto puede omitirse. */
    val isNeutral: Boolean
        get() = brightness == 0f &&
            contrast == 1f &&
            saturation == 1f &&
            warmth == 0f &&
            tint == 0f

    /**
     * Compone todos los ajustes en una única matriz de color 4x4
     * (column-major) para enviarla al shader en un solo uniform.
     *
     * Construir una sola matriz en lugar de encadenar shaders reduce
     * drásticamente el número de pases GPU en la exportación.
     */
    fun toColorMatrix(): FloatArray {
        val s = saturation
        // Matriz de saturación según luminancia Rec.601.
        val sr = (1f - s) * 0.299f
        val sg = (1f - s) * 0.587f
        val sb = (1f - s) * 0.114f

        // Matriz de color RGBA en column-major.
        var m = floatArrayOf(
            sr + s, sr, sr, 0f,
            sg, sg + s, sg, 0f,
            sb, sb, sb + s, 0f,
            0f, 0f, 0f, 1f,
        )

        m = multiply(m, contrastMatrix(contrast))
        m = multiply(m, temperatureMatrix(warmth, tint))
        m = multiply(m, brightnessMatrix(brightness))
        return m
    }

    companion object {
        val NEUTRAL = ColorAdjustments()
    }
}

/** Presets de la fila de "Filtros". */
enum class VideoFilter(
    val displayName: String,
    val adjustments: ColorAdjustments,
) {
    NONE("Original", ColorAdjustments.NEUTRAL),

    /** Cálido cinematográfico: naranja en luces, sombras neutras. */
    CINEMA("Cine", ColorAdjustments(brightness = 0.02f, contrast = 1.15f, saturation = 1.10f, warmth = 0.22f)),

    /** Muy desaturado y frío, tipo thriller. */
    NOIR("Noir", ColorAdjustments(contrast = 1.30f, saturation = 0.10f, warmth = -0.12f)),

    /** Vibrante para redes sociales: saturación alta y contraste medio. */
    VIVID("Vívido", ColorAdjustments(contrast = 1.10f, saturation = 1.45f)),

    /** Vintage analógico: lavado, cálido y algo verdoso. */
    RETRO("Retro", ColorAdjustments(brightness = 0.06f, contrast = 0.90f, saturation = 0.78f, warmth = 0.18f, tint = -0.05f)),

    /** Frío y limpio para atardeceres/retratos. */
    BREEZE("Brisa", ColorAdjustments(brightness = 0.04f, contrast = 1.05f, saturation = 1.05f, warmth = -0.18f)),

    /** Blanco y negro con contraste de alto impacto. */
    MONO("Mono", ColorAdjustments(contrast = 1.25f, saturation = 0f)),
    ;

    val colorMatrix: FloatArray get() = adjustments.toColorMatrix()
}

/**
 * Efecto de color de VideoEcraft: aplica una matriz 4x4 sobre el RGB.
 *
 * Sustituye a `RgbMatrix` de Media3 para poder (a) componer todos los
 * ajustes en un solo pase y (b) reutilizar la misma infraestructura
 * de shaders que el Chroma Key.
 */
@UnstableApi
class ColorFilterEffect(
    private val adjustments: ColorAdjustments,
) : GlEffect {

    constructor(filter: VideoFilter) : this(filter.adjustments)

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        if (adjustments.isNeutral) {
            PassThroughShaderProgram()
        } else {
            ColorFilterShaderProgram(adjustments.toColorMatrix(), useHdr)
        }
}

/** Programa identidad: copia la entrada a la salida sin coste de color. */
@UnstableApi
private class PassThroughShaderProgram : BaseVideoShaderProgram(
    fragmentShader = Shaders.VERTEX_TO_FRAGMENT,
    useHighPrecisionColorComponents = false,
    texturePoolCapacity = 1,
) {
    override fun setUniforms(program: GlProgram, presentationTimeUs: Long) = Unit
}

/** [GlShaderProgram] que ejecuta [Shaders.COLOR_MATRIX]. */
@UnstableApi
private class ColorFilterShaderProgram(
    private val colorMatrix: FloatArray,
    useHighPrecisionColorComponents: Boolean,
) : BaseVideoShaderProgram(
    fragmentShader = Shaders.COLOR_MATRIX,
    useHighPrecisionColorComponents = useHighPrecisionColorComponents,
    texturePoolCapacity = 1,
) {
    override fun setUniforms(program: GlProgram, presentationTimeUs: Long) {
        program.setFloatsUniform("uColorMatrix", colorMatrix)
    }
}

// ---------------------------------------------------------------------
//  Composición de matrices 4x4 column-major
// ---------------------------------------------------------------------

private fun multiply(a: FloatArray, b: FloatArray): FloatArray {
    val out = FloatArray(16)
    for (col in 0 until 4) {
        for (row in 0 until 4) {
            var sum = 0f
            for (k in 0 until 4) {
                // a[row, k] * b[k, col] con layout column-major.
                sum += a[k * 4 + row] * b[col * 4 + k]
            }
            out[col * 4 + row] = sum
        }
    }
    return out
}

private fun brightnessMatrix(brightness: Float): FloatArray = floatArrayOf(
    1f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f,
    0f, 0f, 1f, 0f,
    brightness, brightness, brightness, 1f,
)

private fun contrastMatrix(contrast: Float): FloatArray {
    val translate = (1f - contrast) * 0.5f
    return floatArrayOf(
        contrast, 0f, 0f, 0f,
        0f, contrast, 0f, 0f,
        0f, 0f, contrast, 0f,
        translate, translate, translate, 1f,
    )
}

/**
 * Balance de blancos aproximado: [warmth] desplaza el eje rojo/azul y
 * [tint] el eje verde/magenta (equivalente a ajustar temperatura y tinte).
 */
private fun temperatureMatrix(warmth: Float, tint: Float): FloatArray = floatArrayOf(
    1f + warmth * 0.20f, 0f, 0f, 0f,
    0f, 1f + tint * 0.15f, 0f, 0f,
    0f, 0f, 1f - warmth * 0.20f, 0f,
    0f, 0f, 0f, 1f,
)
