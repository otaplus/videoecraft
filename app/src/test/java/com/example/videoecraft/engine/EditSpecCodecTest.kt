package com.example.videoecraft.engine

import com.example.videoecraft.engine.effects.ChromaKeySettings
import com.example.videoecraft.engine.effects.ColorAdjustments
import com.example.videoecraft.engine.effects.TransitionType
import com.example.videoecraft.engine.model.AspectRatio
import com.example.videoecraft.engine.model.BackgroundFill
import com.example.videoecraft.engine.model.CanvasSpec
import com.example.videoecraft.engine.model.ClipSpec
import com.example.videoecraft.engine.model.EditSpec
import com.example.videoecraft.engine.model.ExportResolution
import com.example.videoecraft.engine.model.MusicSpec
import com.example.videoecraft.engine.model.OverlaySpec
import com.example.videoecraft.engine.model.TextLayerSpec
import com.example.videoecraft.export.EditSpecCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Round-trip del códec de [EditSpec] usado para transportar el proyecto al
 * `ExportService` por un Intent.
 *
 * Se ejecuta con Robolectric porque `EditSpecCodec` depende de `org.json`
 * y `android.util.Base64`, que en la JVM pura son stubs y devuelven `null`.
 */
@RunWith(RobolectricTestRunner::class)
class EditSpecCodecTest {

    private val sampleSpec = EditSpec(
        canvas = CanvasSpec(
            aspectRatio = AspectRatio.VERTICAL,
            resolution = ExportResolution.P2160,
            background = BackgroundFill.Solid(0xFF123456.toInt()),
        ),
        clips = listOf(
            ClipSpec(
                uri = "content://media/external/video/1",
                sourceStartMs = 1_000L,
                sourceEndMs = 9_000L,
                sourceDurationMs = 12_000L,
                speed = PlaybackSpeed(2f),
                color = ColorAdjustments(contrast = 1.2f, saturation = 0.8f),
                transitionIn = TransitionType.ZOOM_IN,
                transitionDurationMs = 700L,
                keepOriginalAudio = false,
            ),
            ClipSpec(
                uri = "content://media/external/video/2",
                sourceDurationMs = 5_000L,
            ),
        ),
        overlays = listOf(
            OverlaySpec(
                uri = "content://media/external/video/3",
                startMs = 500L,
                endMs = 4_000L,
                chromaKey = ChromaKeySettings.GREEN.copy(similarity = 0.4f),
                scale = 0.35f,
                positionX = -0.5f,
                positionY = 0.5f,
                rotationDegrees = 15f,
                alpha = 0.9f,
            ),
        ),
        music = listOf(
            MusicSpec(uri = "content://media/external/audio/1", volume = 0.4f, loop = true),
        ),
        texts = listOf(
            TextLayerSpec(text = "Hola VideoEcraft", startMs = 0L, endMs = 3_000L),
        ),
        musicVolume = 0.4f,
        originalVolume = 0.8f,
    )

    @Test
    fun `round trip preserva todos los campos`() {
        val decoded = EditSpecCodec.decode(EditSpecCodec.encode(sampleSpec))

        assertNotNull("el spec decodificado no debe ser null", decoded)
        requireNotNull(decoded)

        // Canvas
        assertEquals(AspectRatio.VERTICAL, decoded.canvas.aspectRatio)
        assertEquals(ExportResolution.P2160, decoded.canvas.resolution)
        assertTrue(decoded.canvas.background is BackgroundFill.Solid)
        assertEquals(0xFF123456.toInt(), (decoded.canvas.background as BackgroundFill.Solid).colorArgb)

        // Clips
        assertEquals(2, decoded.clips.size)
        val first = decoded.clips[0]
        assertEquals(sampleSpec.clips[0].uri, first.uri)
        assertEquals(1_000L, first.sourceStartMs)
        assertEquals(9_000L, first.sourceEndMs)
        assertEquals(2f, first.speed.factor, 1e-4f)
        assertEquals(TransitionType.ZOOM_IN, first.transitionIn)
        assertEquals(700L, first.transitionDurationMs)
        assertEquals(false, first.keepOriginalAudio)
        assertEquals(1.2f, first.color.contrast, 1e-4f)
        assertEquals(0.8f, first.color.saturation, 1e-4f)

        // Overlay + croma
        assertEquals(1, decoded.overlays.size)
        val overlay = decoded.overlays[0]
        assertNotNull(overlay.chromaKey)
        assertEquals(0.4f, requireNotNull(overlay.chromaKey).similarity, 1e-4f)
        assertEquals(0.35f, overlay.scale, 1e-4f)
        assertEquals(-0.5f, overlay.positionX, 1e-4f)
        assertEquals(15f, overlay.rotationDegrees, 1e-4f)

        // Música y textos
        assertEquals(1, decoded.music.size)
        assertEquals(0.4f, decoded.music[0].volume, 1e-4f)
        assertEquals(1, decoded.texts.size)
        assertEquals("Hola VideoEcraft", decoded.texts[0].text)

        // Volúmenes globales
        assertEquals(0.4f, decoded.musicVolume, 1e-4f)
        assertEquals(0.8f, decoded.originalVolume, 1e-4f)
    }

    @Test
    fun `la duracion se mantiene tras el round trip`() {
        val decoded = requireNotNull(EditSpecCodec.decode(EditSpecCodec.encode(sampleSpec)))
        // Clip 1: 8s a 2x = 4s · Clip 2: 5s a 1x = 5s → 9s
        assertEquals(9_000L, decoded.durationMs)
        assertEquals(sampleSpec.durationMs, decoded.durationMs)
    }

    @Test
    fun `un string corrupto devuelve null en lugar de lanzar`() {
        assertNull(EditSpecCodec.decode("esto-no-es-base64-valido!!!"))
        assertNull(EditSpecCodec.decode(""))
    }

    @Test
    fun `un spec vacio hace round trip correctamente`() {
        val decoded = EditSpecCodec.decode(EditSpecCodec.encode(EditSpec()))
        assertNotNull(decoded)
        requireNotNull(decoded)
        assertTrue(decoded.clips.isEmpty())
        assertEquals(0L, decoded.durationMs)
    }
}
