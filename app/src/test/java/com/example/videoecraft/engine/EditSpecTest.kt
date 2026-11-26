package com.example.videoecraft.engine

import com.example.videoecraft.engine.model.AspectRatio
import com.example.videoecraft.engine.model.CanvasSpec
import com.example.videoecraft.engine.model.ClipSpec
import com.example.videoecraft.engine.model.EditSpec
import com.example.videoecraft.engine.model.ExportResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica los cálculos de duración y de tamaño de lienzo, que son la base
 * de la línea de tiempo (Fase 3) y del export.
 */
class EditSpecTest {

    @Test
    fun `la duracion del clip aplica el recorte`() {
        val clip = ClipSpec(
            uri = "content://video/1",
            sourceStartMs = 2_000L,
            sourceEndMs = 8_000L,
            sourceDurationMs = 10_000L,
        )
        assertEquals(6_000L, clip.trimmedDurationMs)
    }

    @Test
    fun `sin fin explicito se usa la duracion de origen`() {
        val clip = ClipSpec(
            uri = "content://video/1",
            sourceStartMs = 1_500L,
            sourceEndMs = -1L,
            sourceDurationMs = 10_000L,
        )
        assertEquals(8_500L, clip.trimmedDurationMs)
    }

    @Test
    fun `la velocidad reduce la duracion proporcionalmente`() {
        val clip = ClipSpec(
            uri = "content://video/1",
            sourceStartMs = 0L,
            sourceEndMs = 10_000L,
            sourceDurationMs = 10_000L,
            speed = PlaybackSpeed(2f),
        )
        // 10s de material a 2x duran 5s en el timeline.
        assertEquals(5_000L, clip.trimmedDurationMs)
    }

    @Test
    fun `la ralentizacion aumenta la duracion`() {
        val clip = ClipSpec(
            uri = "content://video/1",
            sourceStartMs = 0L,
            sourceEndMs = 4_000L,
            sourceDurationMs = 4_000L,
            speed = PlaybackSpeed(0.5f),
        )
        assertEquals(8_000L, clip.trimmedDurationMs)
    }

    @Test
    fun `la duracion del proyecto suma todos los clips`() {
        val spec = EditSpec(
            clips = listOf(
                ClipSpec("a", sourceEndMs = 3_000L, sourceDurationMs = 3_000L),
                ClipSpec("b", sourceEndMs = 2_000L, sourceDurationMs = 2_000L),
                ClipSpec("c", sourceEndMs = 1_000L, sourceDurationMs = 1_000L),
            ),
        )
        assertEquals(6_000L, spec.durationMs)
    }

    @Test
    fun `un spec sin clips no tiene contenido`() {
        assertFalse(EditSpec().hasContent)
        assertTrue(EditSpec(clips = listOf(ClipSpec("a"))).hasContent)
    }

    @Test
    fun `el lienzo vertical 9 16 produce ancho menor que alto`() {
        val canvas = CanvasSpec(
            aspectRatio = AspectRatio.VERTICAL,
            resolution = ExportResolution.P1080,
        )
        val (width, height) = canvas.sizePx
        assertEquals(1080, height)
        // 1080 * 9/16 = 607.5 → 607, luego se redondea al par inferior (606).
        assertEquals(606, width)
        assertTrue(width < height)
    }

    @Test
    fun `el lienzo cuadrado produce dimensiones iguales`() {
        val canvas = CanvasSpec(
            aspectRatio = AspectRatio.SQUARE,
            resolution = ExportResolution.P720,
        )
        val (width, height) = canvas.sizePx
        assertEquals(720, width)
        assertEquals(720, height)
    }

    @Test
    fun `el ancho siempre es par para los encoders H264`() {
        AspectRatio.entries.forEach { aspect ->
            ExportResolution.entries.forEach { resolution ->
                val (width, _) = CanvasSpec(aspect, resolution).sizePx
                assertEquals(
                    "el ancho debe ser par en $aspect/$resolution",
                    0,
                    width % 2,
                )
            }
        }
    }
}
