package com.example.videoecraft.engine.effects

import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram

/**
 * Base común para todos los shaders de VideoEcraft.
 *
 * Envuelve [GlProgram] y resuelve el *boilerplate* que exige
 * [BaseGlShaderProgram]:
 *
 *  1. Compilar el programa en [configure] (donde el contexto GL ya existe).
 *  2. Registrar `aFramePosition` y `aTexSamplingCoord` en cada frame.
 *  3. Fijar `uTexSampler` a la unidad de textura 0.
 *  4. Dibujar el quad como `GL_TRIANGLE_STRIP` de 4 vértices.
 *
 * Las subclases solo implementan [setUniforms] para enviar sus parámetros.
 *
 * @param fragmentShader GLSL del fragment shader (se combina con [Shaders.VERTEX]).
 */
@UnstableApi
internal abstract class BaseVideoShaderProgram(
    private val fragmentShader: String,
    useHighPrecisionColorComponents: Boolean = false,
    texturePoolCapacity: Int = 1,
) : BaseGlShaderProgram(useHighPrecisionColorComponents, texturePoolCapacity) {

    private var glProgram: GlProgram? = null

    /** Ancho de entrada del último [configure]; útil para uniforms dependientes del tamaño. */
    protected var inputWidth: Int = 0
        private set

    /** Alto de entrada del último [configure]. */
    protected var inputHeight: Int = 0
        private set

    @Throws(VideoFrameProcessingException::class)
    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        this.inputWidth = inputWidth
        this.inputHeight = inputHeight

        if (glProgram == null) {
            glProgram = try {
                GlProgram(Shaders.VERTEX, fragmentShader)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e)
            }
        }
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val program = glProgram ?: return
        try {
            program.use()

            // Quad unitario: 4 vértices en triangle strip.
            program.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                /* size= */ 4,
            )
            program.setBufferAttribute(
                "aTexSamplingCoord",
                GlUtil.getTextureCoordinateBounds(),
                /* size= */ 4,
            )
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0)

            setUniforms(program, presentationTimeUs)
            program.bindAttributesAndUniforms()

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4)
            GlUtil.checkGlError()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    /**
     * Envía los uniforms propios del efecto. Se invoca en cada frame,
     * después de bindear atributos y sampler, y antes del `drawArrays`.
     */
    protected abstract fun setUniforms(program: GlProgram, presentationTimeUs: Long)

    override fun release() {
        try {
            glProgram?.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        } finally {
            glProgram = null
        }
        super.release()
    }
}
