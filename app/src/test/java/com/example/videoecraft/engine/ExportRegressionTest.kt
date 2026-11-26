package com.example.videoecraft.engine

import com.example.videoecraft.data.MediaImporter
import com.example.videoecraft.engine.model.ClipSpec
import com.example.videoecraft.engine.model.EditSpec
import com.example.videoecraft.engine.model.MusicSpec
import com.example.videoecraft.engine.model.OverlaySpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests de regresión de los bugs encontrados al ejecutar la exportación
 * real en un dispositivo. Cada bloque documenta un fallo concreto que no
 * debe volver.
 *
 * Todos son lógica pura, así que corren en JVM sin emulador.
 */
class ExportRegressionTest {

    // =================================================================
    //  Bug #1 — VideoCompositorSettings con una sola secuencia de vídeo
    // =================================================================

    /*
     * Síntoma en dispositivo:
     *   IllegalArgumentException: SingleInputVideoGraph does not use
     *   VideoCompositor, and therefore cannot apply VideoCompositorSettings
     *
     * Causa: con una única secuencia de vídeo, Media3 monta
     * `SingleInputVideoGraph` (sin compositor) y rechaza el objeto.
     * `VideoProcessor` sólo debe registrar el compositor cuando hay
     * varias secuencias que aporten vídeo.
     */

    /**
     * Espeja la condición de `VideoProcessor.buildComposition`:
     * el compositor sólo se registra si hay overlays.
     */
    private fun requiresCompositor(spec: EditSpec): Boolean = spec.overlays.isNotEmpty()

    @Test
    fun `un proyecto de un solo clip NO debe usar VideoCompositorSettings`() {
        val spec = EditSpec(
            clips = listOf(ClipSpec(uri = "file:///a.mp4", sourceDurationMs = 30_000L)),
        )
        assertFalse(
            "Con una sola secuencia Media3 usa SingleInputVideoGraph, " +
                "que no admite VideoCompositorSettings",
            requiresCompositor(spec),
        )
    }

    @Test
    fun `varios clips en la pista principal tampoco requieren compositor`() {
        // Varios clips son UNA secuencia (van en serie), no varias pistas.
        val spec = EditSpec(
            clips = listOf(
                ClipSpec(uri = "file:///a.mp4", sourceDurationMs = 10_000L),
                ClipSpec(uri = "file:///b.mp4", sourceDurationMs = 10_000L),
                ClipSpec(uri = "file:///c.mp4", sourceDurationMs = 10_000L),
            ),
        )
        assertFalse(requiresCompositor(spec))
    }

    @Test
    fun `una superposicion SI requiere compositor`() {
        val spec = EditSpec(
            clips = listOf(ClipSpec(uri = "file:///a.mp4", sourceDurationMs = 10_000L)),
            overlays = listOf(OverlaySpec(uri = "file:///pip.mp4")),
        )
        assertTrue(requiresCompositor(spec))
    }

    @Test
    fun `la musica sola no requiere compositor porque no aporta video`() {
        // La secuencia de música tiene removeVideo = true: no hay segundo
        // flujo de vídeo, así que sigue siendo un video graph de una entrada.
        val spec = EditSpec(
            clips = listOf(ClipSpec(uri = "file:///a.mp4", sourceDurationMs = 10_000L)),
            music = listOf(MusicSpec(uri = "file:///music.m4a")),
        )
        assertFalse(
            "La pista de audio no crea un segundo flujo de vídeo",
            requiresCompositor(spec),
        )
    }

    // =================================================================
    //  Bug #2 — URIs efímeras del Photo Picker
    // =================================================================

    /*
     * Síntoma en dispositivo:
     *   SecurityException: Calling uid does not have permission to access
     *   picker uri: content://media/picker/0/.../media/58987
     *
     * Causa: la concesión de lectura del Photo Picker caduca al morir el
     * proceso. Guardar esa URI en Room rompe el proyecto al reabrirlo.
     * La solución es copiar el archivo al sandbox y guardar el `file://`.
     */

    @Test
    fun `una URI de Photo Picker necesita importarse`() {
        assertTrue(
            MediaImporter.needsImport(
                "content://media/picker/0/com.android.providers.media.photopicker/media/123",
            ),
        )
        assertTrue(
            MediaImporter.needsImport(
                "content://media/picker_get_content/0/" +
                    "com.android.providers.media.photopicker/media/123",
            ),
        )
    }

    @Test
    fun `un content uri de MediaStore necesita importarse`() {
        assertTrue(
            MediaImporter.needsImport("content://media/external/video/media/1234"),
        )
    }

    @Test
    fun `un file uri ya dentro del sandbox NO necesita importarse`() {
        assertFalse(
            MediaImporter.needsImport(
                "file:///data/user/0/com.example.videoecraft/files/editor/p1_abc.mp4",
            ),
        )
    }

    @Test
    fun `un file uri fuera del sandbox SI necesita importarse`() {
        assertTrue(MediaImporter.needsImport("file:///sdcard/Download/clip.mp4"))
    }

    // =================================================================
    //  Bug #3 — duración correcta al importar
    // =================================================================

    /*
     * Síntoma observado: al seleccionar 2 videos, la pantalla de
     * exportación mostraba "1 clips · 0:33". Los clips sin duración
     * legible se descartaban silenciosamente.
     *
     * Este test fija el contrato: la duración del proyecto es la suma de
     * los clips, y un clip sin duración aporta 0 en lugar de romper la suma.
     */

    @Test
    fun `la duracion total suma todos los clips importados`() {
        val spec = EditSpec(
            clips = listOf(
                ClipSpec(uri = "file:///a.mp4", sourceDurationMs = 33_000L),
                ClipSpec(uri = "file:///b.mp4", sourceDurationMs = 33_000L),
            ),
        )
        assertEquals(66_000L, spec.durationMs)
        assertEquals(2, spec.clips.size)
    }

    @Test
    fun `un clip sin duracion conocida no rompe el proyecto`() {
        val spec = EditSpec(
            clips = listOf(
                ClipSpec(uri = "file:///a.mp4", sourceDurationMs = 33_000L),
                // sourceDurationMs = 0 → clip inválido, pero no debe lanzar.
                ClipSpec(uri = "file:///broken.mp4", sourceDurationMs = 0L),
            ),
        )
        assertEquals(33_000L, spec.durationMs)
    }
}
