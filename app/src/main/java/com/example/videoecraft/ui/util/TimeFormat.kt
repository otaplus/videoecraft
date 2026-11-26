package com.example.videoecraft.ui.util

import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Formateo de tiempos de la UI.
 *
 * El visor muestra milisegundos (requisito del editor), mientras que las
 * tarjetas de proyecto y la timeline usan `m:ss` para no saturar.
 */
object TimeFormat {

    /**
     * Contador del visor con milisegundos: `01:23.456`.
     * Cifras de ancho fijo para que no baile durante la reproducción.
     */
    fun timecodeWithMillis(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(safe)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(safe) % 60
        val millis = safe % 1000
        return String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, millis)
    }

    /** Duración compacta para tarjetas y timeline: `1:23`. */
    fun duration(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(safe)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(safe) % 60
        return String.format(Locale.US, "%d:%02d", minutes, seconds)
    }

    /** Duración con horas cuando hace falta: `1:02:03`. */
    fun durationLong(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val hours = TimeUnit.MILLISECONDS.toHours(safe)
        if (hours == 0L) return duration(safe)

        val minutes = TimeUnit.MILLISECONDS.toMinutes(safe) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(safe) % 60
        return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    }

    /** Fecha relativa para las tarjetas de proyecto: "Hoy", "Ayer", "3 d". */
    fun relativeDate(epochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val diff = nowMillis - epochMillis
        val dayMs = 86_400_000L
        return when {
            diff < dayMs -> "Hoy"
            diff < 2 * dayMs -> "Ayer"
            diff < 7 * dayMs -> "${diff / dayMs} d"
            else -> "${diff / (7 * dayMs)} sem"
        }
    }
}
