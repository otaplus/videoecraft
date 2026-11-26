package com.example.videoecraft.ui.editor.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import coil3.compose.AsyncImage
import com.example.videoecraft.engine.model.EditSpec
import com.example.videoecraft.ui.theme.CraftAccentEnd
import com.example.videoecraft.ui.theme.CraftAccentStart
import com.example.videoecraft.ui.theme.CraftOutline
import com.example.videoecraft.ui.theme.CraftSurface
import com.example.videoecraft.ui.theme.CraftSurfaceHigh
import com.example.videoecraft.ui.theme.CraftTextPrimary
import com.example.videoecraft.ui.theme.CraftTextSecondary
import com.example.videoecraft.ui.theme.CraftVoid
import com.example.videoecraft.ui.util.TimeFormat
import com.example.videoecraft.ui.util.VideoThumbnail

/**
 * Línea de tiempo multicapa del editor.
 *
 * Pistas, de arriba a abajo:
 *  1. **Texto** — capas de texto superpuestas.
 *  2. **Superposiciones** — PIP / croma.
 *  3. **Video principal** — clips con miniaturas.
 *  4. **Audio** — pista de música.
 *
 * El **cabezal está fijo en el centro** y la timeline se desplaza por debajo
 * (patrón de editor profesional: el dedo mueve el contenido, no el cursor).
 * El zoom con pinch cambia los px/segundo.
 *
 * @param spec edición actual (fuente de las pistas).
 * @param playheadMs posición del cabezal.
 * @param zoom px por segundo.
 * @param selectedClipIndex clip resaltado.
 * @param onSeek mover el cabezal.
 * @param onSelectClip seleccionar un clip.
 * @param onZoomGesture factor de zoom del pinch.
 */
@UnstableApi
@Composable
fun Timeline(
    spec: EditSpec,
    playheadMs: Long,
    zoom: Float,
    selectedClipIndex: Int,
    onSeek: (Long) -> Unit,
    onSelectClip: (Int) -> Unit,
    onZoomGesture: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scrollState = rememberScrollState()
    val totalDurationMs = spec.durationMs.coerceAtLeast(1L)

    // Ancho total de la pista en px según el zoom.
    val trackWidthPx = (totalDurationMs / 1000f) * zoom
    val trackWidthDp: Dp = with(density) { trackWidthPx.toDp() }

    // El cabezal va centrado: se añade medio viewport de padding a ambos lados
    // para poder llegar al primer y al último frame.
    var viewportWidthPx by remember { mutableStateOf(0) }
    val edgePaddingDp: Dp = with(density) { (viewportWidthPx / 2f).toDp() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TIMELINE_HEIGHT),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            // ---- Regla de tiempo ----
            TimeRuler(
                totalDurationMs = totalDurationMs,
                zoom = zoom,
                scrollState = scrollState,
                edgePadding = edgePaddingDp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(18.dp),
            )

            // ---- Pistas con scroll ----
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .pointerInput(Unit) {
                        // Pinch-to-zoom sobre toda la timeline.
                        detectTransformGestures { _, _, gestureZoom, _ ->
                            if (gestureZoom != 1f) onZoomGesture(gestureZoom)
                        }
                    },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .horizontalScroll(scrollState),
                ) {
                    // Padding izquierdo para que el cabezal alcance el inicio.
                    Box(Modifier.width(edgePaddingDp))

                    Column(
                        modifier = Modifier
                            .width(trackWidthDp.coerceAtLeast(1.dp))
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        // ---- Pista de TEXTO ----
                        if (spec.texts.isNotEmpty()) {
                            TextTrack(spec)
                        }

                        // ---- Pista de SUPERPOSICIONES ----
                        if (spec.overlays.isNotEmpty()) {
                            OverlayTrack(spec)
                        }

                        // ---- Pista de VIDEO PRINCIPAL ----
                        VideoTrack(
                            spec = spec,
                            zoom = zoom,
                            selectedClipIndex = selectedClipIndex,
                            onSelectClip = onSelectClip,
                            modifier = Modifier.weight(1f),
                        )

                        // ---- Pista de AUDIO ----
                        if (spec.music.isNotEmpty()) {
                            MusicTrack(spec)
                        }
                    }

                    Box(Modifier.width(edgePaddingDp))
                }

                // Reportar el ancho del viewport para el padding del cabezal.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            viewportWidthPx = size.width
                        },
                )

                // ---- CABEZAL FIJO CENTRADO ----
                Playhead(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxHeight(),
                )
            }
        }

        // Mapear scroll → posición del cabezal.
        // Cuando la pista se desplaza, el cabezal (fijo) apunta a otro tiempo.
        androidx.compose.runtime.LaunchedEffect(scrollState.value, zoom, totalDurationMs) {
            val pxPerMs = zoom / 1000f
            if (pxPerMs <= 0f) return@LaunchedEffect
            val positionMs = (scrollState.value / pxPerMs).toLong()
            onSeek(positionMs.coerceIn(0L, totalDurationMs))
        }

        // Mantener la pista sincronizada si el cabezal cambia por reproducción.
        androidx.compose.runtime.LaunchedEffect(playheadMs, zoom) {
            val pxPerMs = zoom / 1000f
            val expectedScroll = (playheadMs * pxPerMs).toInt()
            // Sólo corrige si hay una desviación grande: evita el bucle
            // scroll ⇄ seek cuando el usuario está arrastrando.
            if (kotlin.math.abs(scrollState.value - expectedScroll) > 4) {
                scrollState.scrollTo(expectedScroll.coerceIn(0, scrollState.maxValue))
            }
        }
    }
}

// ---------------------------------------------------------------------
//  Regla de tiempo
// ---------------------------------------------------------------------

@Composable
private fun TimeRuler(
    totalDurationMs: Long,
    zoom: Float,
    scrollState: androidx.compose.foundation.ScrollState,
    edgePadding: Dp,
    modifier: Modifier = Modifier,
) {
    // Un tick cada N segundos según el zoom, para que no se solapen.
    val tickIntervalMs = when {
        zoom >= 200f -> 500L
        zoom >= 100f -> 1_000L
        zoom >= 50f -> 2_000L
        else -> 5_000L
    }

    val pxPerMs = zoom / 1000f
    val density = LocalDensity.current

    // La regla se desplaza junto con las pistas, así los tiempos
    // permanecen alineados con los clips en cualquier posición de scroll.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .horizontalScroll(scrollState, enabled = false),
        ) {
            // Padding izquierdo (medio viewport) que replica el de las pistas.
            Box(Modifier.width(edgePadding))

            Box(
                modifier = Modifier
                    .width(with(density) { (totalDurationMs * pxPerMs).toDp() })
                    .fillMaxHeight(),
            ) {
                var tick = 0L
                while (tick <= totalDurationMs) {
                    val xPx = (tick * pxPerMs).toInt()
                    Text(
                        text = TimeFormat.duration(tick),
                        color = CraftTextSecondary,
                        fontSize = 9.sp,
                        modifier = Modifier
                            .offset(x = with(density) { xPx.toDp() })
                            .padding(start = 2.dp),
                    )
                    tick += tickIntervalMs
                }
            }

            Box(Modifier.width(edgePadding))
        }
    }
}

// ---------------------------------------------------------------------
//  Pista de vídeo
// ---------------------------------------------------------------------

@Composable
private fun VideoTrack(
    spec: EditSpec,
    zoom: Float,
    selectedClipIndex: Int,
    onSelectClip: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pxPerMs = zoom / 1000f

    Row(
        modifier = modifier
            .fillMaxHeight()
            .height(CLIP_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        spec.clips.forEachIndexed { index, clip ->
            val widthDp = with(LocalDensity.current) {
                (clip.trimmedDurationMs * pxPerMs).toDp()
            }
            val isSelected = index == selectedClipIndex

            ClipCell(
                uri = clip.uri,
                width = widthDp.coerceAtLeast(24.dp),
                isSelected = isSelected,
                label = TimeFormat.duration(clip.trimmedDurationMs),
                badge = if (clip.speed.isNormal) null else clip.speed.label,
                onClick = { onSelectClip(index) },
            )
        }
    }
}

/** Celda de clip con miniatura del video. */
@UnstableApi
@Composable
private fun ClipCell(
    uri: String,
    width: Dp,
    isSelected: Boolean,
    label: String,
    badge: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (isSelected) CraftAccentStart else CraftOutline
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Box(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .clip(RoundedCornerShape(6.dp))
            .background(CraftSurfaceHigh)
            .border(borderWidth, borderColor, RoundedCornerShape(6.dp))
            .then(
                Modifier.pointerInput(Unit) {
                    detectHorizontalDragGestures { _, _ -> }
                },
            ),
    ) {
        // Miniatura del clip extraída ~1 s dentro del video.
        // Pedir la URI cruda devuelve el primer frame, que a menudo es negro.
        AsyncImage(
            model = VideoThumbnail.request(
                context = LocalContext.current,
                uriUri = uri,
                offsetMs = 1_000L,
            ),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        )

        // Velo para legibilidad del texto.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(CraftVoid.copy(alpha = 0.18f)),
        )

        // Duración del clip.
        Text(
            text = label,
            color = CraftTextPrimary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp),
        )

        // Badge de velocidad (2x, 0.5x…)
        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(CraftAccentEnd)
                    .padding(horizontal = 3.dp, vertical = 1.dp),
            ) {
                Text(
                    text = badge,
                    color = CraftVoid,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Área táctil de selección.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectTapGestures { onClick() }
                },
        )
    }
}

// ---------------------------------------------------------------------
//  Pista de superposiciones
// ---------------------------------------------------------------------

@Composable
private fun OverlayTrack(spec: EditSpec) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(OVERLAY_TRACK_HEIGHT),
    ) {
        spec.overlays.forEach { overlay ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(CraftSurface)
                    .border(1.dp, CraftAccentEnd.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.VideoCall,
                        contentDescription = null,
                        tint = CraftAccentEnd,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = if (overlay.chromaKey != null) "Croma" else "PIP",
                        color = CraftAccentEnd,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
//  Pista de texto
// ---------------------------------------------------------------------

@Composable
private fun TextTrack(spec: EditSpec) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(OVERLAY_TRACK_HEIGHT),
    ) {
        spec.texts.take(1).forEach { text ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(CraftSurface)
                    .border(1.dp, CraftAccentStart.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.TextFields,
                        contentDescription = null,
                        tint = CraftAccentStart,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = "\"${text.text}\"",
                        color = CraftAccentStart,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
//  Pista de audio
// ---------------------------------------------------------------------

@Composable
private fun MusicTrack(spec: EditSpec) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AUDIO_TRACK_HEIGHT),
    ) {
        spec.music.forEach {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                CraftAccentStart.copy(alpha = 0.30f),
                                CraftAccentEnd.copy(alpha = 0.20f),
                            ),
                        ),
                    )
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = CraftAccentEnd,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = "Música · ${(spec.musicVolume * 100).toInt()}%",
                        color = CraftTextSecondary,
                        fontSize = 9.sp,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
//  Cabezal
// ---------------------------------------------------------------------

/**
 * Cabezal fijo. Se dibuja como una línea vertical con un tirador superior,
 * en el degradado de acento para que destaque sobre cualquier contenido.
 */
@Composable
private fun Playhead(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        // Línea vertical.
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(
                        listOf(CraftAccentStart, CraftAccentEnd.copy(alpha = 0.25f)),
                    ),
                ),
        )

        // Tirador superior.
        Box(
            modifier = Modifier
                .offset(y = (-1).dp)
                .size(width = 12.dp, height = 10.dp)
                .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                .background(CraftAccentStart),
        )
    }
}

// ---------------------------------------------------------------------
//  Dimensiones
// ---------------------------------------------------------------------

private val TIMELINE_HEIGHT = 168.dp
private val CLIP_HEIGHT = 68.dp
private val OVERLAY_TRACK_HEIGHT = 20.dp
private val AUDIO_TRACK_HEIGHT = 18.dp

/** Reexportado para el editor: color de fondo de la timeline. */
internal val TimelineBackground = Color(0xFF141419)
