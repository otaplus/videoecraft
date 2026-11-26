package com.example.videoecraft.engine.effects

import android.graphics.Matrix
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect
import androidx.media3.effect.MatrixTransformation

/**
 * Librería de transiciones entre clips de VideoEcraft.
 *
 * Cada entrada produce la lista de [GlEffect] que hay que aplicar al
 * clip **entrante** (el que aparece). Los efectos se "cuecen" durante la
 * exportación sobre el `EditedMediaItem` del clip entrante, de modo que la
 * `Composition` los procesa sin necesidad de un crossfade nativo entre
 * secuencias (que Media3 todavía no soporta).
 *
 * > [!NOTE]
 * > Media3 no soporta crossfading entre secuencias de una `Composition`.
 * > La estrategia de VideoEcraft es aplicar la transición **dentro** del
 * > `EditedMediaItem` entrante con [MatrixTransformation] dependiente del
 * > tiempo (sí soportado y determinista) más un efecto de alfa para los
 * > fundidos.
 */
@UnstableApi
enum class TransitionType(val displayName: String, val durationMs: Long = 500L) {

    /** Sin transición: corte directo. */
    NONE("Ninguna", 0L),

    /** Funde desde negro: el clip entrante arranca oscuro y se ilumina. */
    FADE_TO_BLACK("Fundido a negro", 600L),

    /** Disolución: el clip entrante arranca transparente y sube el alfa. */
    DISSOLVE("Disolución", 700L),

    /** Deslizamiento lateral: el clip entra deslizándose desde la izquierda. */
    SLIDE_LEFT("Deslizar ←", 500L),

    /** Deslizamiento desde la derecha. */
    SLIDE_RIGHT("Deslizar →", 500L),

    /** Zoom in: el clip entrante empieza alejado y se acerca. */
    ZOOM_IN("Zoom", 550L),

    /** Zoom out: el clip entrante empieza acercado y se aleja. */
    ZOOM_OUT("Zoom out", 550L),
    ;

    /**
     * Devuelve los efectos de video que implementan esta transición
     * sobre el clip **entrante**.
     *
     * @param transitionDurationMs duración real; por defecto la del tipo.
     */
    fun toEffects(transitionDurationMs: Long = durationMs): List<GlEffect> {
        if (this == NONE || transitionDurationMs <= 0L) return emptyList()
        val durationUs = transitionDurationMs * 1_000L

        return when (this) {
            NONE -> emptyList()

            FADE_TO_BLACK -> listOf(FadeInEffect(durationUs = durationUs, fromBlack = true))

            DISSOLVE -> listOf(FadeInEffect(durationUs = durationUs, fromBlack = false))

            SLIDE_LEFT -> listOf(
                MatrixTransformation { presentationTimeUs ->
                    // Empieza desplazado -1 (fuera por la izquierda) y llega a 0.
                    slideMatrix(presentationTimeUs, durationUs, fromX = -1f, toX = 0f)
                },
            )

            SLIDE_RIGHT -> listOf(
                MatrixTransformation { presentationTimeUs ->
                    slideMatrix(presentationTimeUs, durationUs, fromX = 1f, toX = 0f)
                },
            )

            ZOOM_IN -> listOf(
                MatrixTransformation { presentationTimeUs ->
                    val eased = easeOutCubic(presentationTimeUs, durationUs)
                    scaleMatrix(from = 1.6f, to = 1f, eased = eased)
                },
            )

            ZOOM_OUT -> listOf(
                MatrixTransformation { presentationTimeUs ->
                    val eased = easeOutCubic(presentationTimeUs, durationUs)
                    scaleMatrix(from = 0.6f, to = 1f, eased = eased)
                },
            )
        }
    }
}

// ---------------------------------------------------------------------
//  Curvas de interpolación
// ---------------------------------------------------------------------

/** Progreso normalizado 0..1 dentro de la ventana de la transición. */
private fun progressAt(presentationTimeUs: Long, durationUs: Long): Float {
    if (durationUs <= 0L) return 1f
    return (presentationTimeUs.toFloat() / durationUs.toFloat()).coerceIn(0f, 1f)
}

/** Ease-out cúbico: entra rápido y frena al final. */
private fun easeOutCubic(presentationTimeUs: Long, durationUs: Long): Float {
    val p = progressAt(presentationTimeUs, durationUs)
    val inv = 1f - p
    return 1f - inv * inv * inv
}

// ---------------------------------------------------------------------
//  Constructores de matrices (coordenadas NDC: -1..1)
// ---------------------------------------------------------------------

/** Desplaza horizontalmente el clip desde [fromX] hasta [toX]. */
private fun slideMatrix(
    presentationTimeUs: Long,
    durationUs: Long,
    fromX: Float,
    toX: Float,
): Matrix {
    val p = progressAt(presentationTimeUs, durationUs)
    val eased = 1f - (1f - p) * (1f - p) // ease-out cuadrático
    val x = fromX + (toX - fromX) * eased
    return Matrix().apply { postTranslate(x, 0f) }
}

/** Escala uniforme interpolada entre [from] y [to] según [eased]. */
private fun scaleMatrix(from: Float, to: Float, eased: Float): Matrix {
    val scale = from + (to - from) * eased
    return Matrix().apply { postScale(scale, scale) }
}
