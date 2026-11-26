package com.example.videoecraft.engine

import com.example.videoecraft.engine.effects.ColorAdjustments
import com.example.videoecraft.engine.effects.VideoFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica la composición de la matriz de color 4x4.
 *
 * Como la matriz es puramente matemática, estos tests se ejecutan en la
 * JVM sin emulador ni contexto GL.
 */
class ColorAdjustmentsTest {

    @Test
    fun `ajustes neutros se detectan como neutros`() {
        assertTrue(ColorAdjustments.NEUTRAL.isNeutral)
        assertTrue(VideoFilter.NONE.adjustments.isNeutral)
    }

    @Test
    fun `un solo ajuste no neutro rompe la neutralidad`() {
        assertFalse(ColorAdjustments(brightness = 0.1f).isNeutral)
        assertFalse(ColorAdjustments(contrast = 1.2f).isNeutral)
        assertFalse(ColorAdjustments(saturation = 0.5f).isNeutral)
        assertFalse(ColorAdjustments(warmth = 0.1f).isNeutral)
        assertFalse(ColorAdjustments(tint = 0.1f).isNeutral)
    }

    @Test
    fun `la matriz neutra es la identidad`() {
        val identity = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        )
        assertArrayEquals(identity, ColorAdjustments.NEUTRAL.toColorMatrix(), EPSILON)
    }

    @Test
    fun `saturacion cero produce pesos de luminancia Rec601`() {
        // Con saturation = 0, todas las filas RGB deben colapsar a los
        // coeficientes de luminancia (0.299, 0.587, 0.114).
        val matrix = ColorAdjustments(saturation = 0f).toColorMatrix()

        // Fila R (índices 0,4,8,12 en column-major)
        assertEquals(0.299f, matrix[0], EPSILON)
        assertEquals(0.587f, matrix[4], EPSILON)
        assertEquals(0.114f, matrix[8], EPSILON)
    }

    @Test
    fun `warmth positivo aumenta el rojo y reduce el azul`() {
        val matrix = ColorAdjustments(warmth = 1f).toColorMatrix()
        // Diagonal R (índice 0) y diagonal B (índice 10).
        assertTrue("el rojo debe subir", matrix[0] > 1f)
        assertTrue("el azul debe bajar", matrix[10] < 1f)
    }

    @Test
    fun `brightness anade un offset constante en la fila de traslacion`() {
        val matrix = ColorAdjustments(brightness = 0.25f).toColorMatrix()
        // Última fila (traslación), índices 12, 13, 14.
        assertEquals(0.25f, matrix[12], EPSILON)
        assertEquals(0.25f, matrix[13], EPSILON)
        assertEquals(0.25f, matrix[14], EPSILON)
    }

    @Test
    fun `todos los presets generan matrices de 16 elementos`() {
        VideoFilter.entries.forEach { filter ->
            assertEquals(
                "el filtro ${filter.name} debe producir 16 floats",
                16,
                filter.colorMatrix.size,
            )
        }
    }

    private fun assertArrayEquals(
        expected: FloatArray,
        actual: FloatArray,
        delta: Float,
    ) = org.junit.Assert.assertArrayEquals(expected, actual, delta)

    private companion object {
        const val EPSILON = 1e-4f
    }
}
