package com.example.videoecraft.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifica el rango y el etiquetado de [PlaybackSpeed]. */
class PlaybackSpeedTest {

    @Test
    fun `el rango valido es 0_2x a 4x`() {
        PlaybackSpeed(0.2f)
        PlaybackSpeed(4.0f)
        assertThrows(IllegalArgumentException::class.java) { PlaybackSpeed(0.1f) }
        assertThrows(IllegalArgumentException::class.java) { PlaybackSpeed(4.1f) }
    }

    @Test
    fun `1x se detecta como velocidad normal`() {
        assertTrue(PlaybackSpeed.NORMAL.isNormal)
        assertFalse(PlaybackSpeed(2f).isNormal)
    }

    @Test
    fun `las etiquetas omiten el decimal en factores enteros`() {
        assertEquals("1x", PlaybackSpeed(1f).label)
        assertEquals("2x", PlaybackSpeed(2f).label)
        assertEquals("0.25x", PlaybackSpeed(0.25f).label)
        assertEquals("1.5x", PlaybackSpeed(1.5f).label)
    }

    @Test
    fun `todos los presets estan dentro del rango`() {
        PlaybackSpeed.PRESETS.forEach { speed ->
            assertTrue(speed.factor in PlaybackSpeed.MIN..PlaybackSpeed.MAX)
        }
    }

    @Test
    fun `el preset normal esta en la lista`() {
        assertTrue(PlaybackSpeed.PRESETS.any { it.isNormal })
    }
}
