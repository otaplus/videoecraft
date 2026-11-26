package com.example.videoecraft.ui.editor.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.videoecraft.ui.theme.CraftVoid
import com.example.videoecraft.ui.util.TimeFormat
import kotlinx.coroutines.delay

/**
 * Visor superior del editor.
 *
 * Muestra la previsualización con ExoPlayer, el contador en milisegundos y
 * los controles de reproducción. Se usa `PlayerView` de Media3 dentro de un
 * [AndroidView] en lugar de reimplementar los controles en Compose: el
 * PlayerView gestiona subtítulos, buffer y superficie de forma correcta y
 * es el camino recomendado por Media3 para superficies de vídeo.
 *
 * @param uri media a reproducir (el clip seleccionado).
 * @param positionMs posición inicial del cabezal.
 * @param isPlaying estado de reproducción controlado por el ViewModel.
 * @param aspectRatio relación de aspecto del lienzo elegida.
 * @param onPositionUpdate callback con la posición actual.
 * @param onPlaybackStateChange callback al cambiar play/pause.
 * @param onTogglePlay acción del botón central.
 */
@UnstableApi
@Composable
fun VideoViewer(
    uri: String?,
    positionMs: Long,
    isPlaying: Boolean,
    aspectRatio: Float,
    onPositionUpdate: (Long) -> Unit,
    onPlaybackStateChange: (Boolean) -> Unit,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Un único ExoPlayer por composición; se libera al salir del editor.
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = ExoPlayer.REPEAT_MODE_OFF
            playWhenReady = false
        }
    }

    var controlsVisible by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    // Cargar el clip cuando cambia el URI.
    LaunchedEffect(uri) {
        if (uri != null) {
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.seekTo(positionMs)
            player.playWhenReady = isPlaying
        } else {
            player.clearMediaItems()
        }
    }

    // Sincronizar el estado play/pause del ViewModel con el reproductor.
    LaunchedEffect(isPlaying) {
        player.playWhenReady = isPlaying
    }

    // Sondear la posición para el contador y la timeline.
    LaunchedEffect(isPlaying, uri) {
        while (true) {
            if (player.isPlaying) {
                onPositionUpdate(player.currentPosition)
            }
            delay(POSITION_POLL_MS)
        }
    }

    // Escuchar eventos de reproducción.
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                onPlaybackStateChange(playing)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio, matchHeightConstraintsFirst = false)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black),
    ) {
        if (uri != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false // controles propios en Compose
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Sin video",
                    color = com.example.videoecraft.ui.theme.CraftTextSecondary,
                    fontSize = 13.sp,
                )
            }
        }

        // Zona táctil sobre el vídeo: alterna los controles.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { controlsVisible = !controlsVisible },
        )

        // Botón central play/pause.
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(CraftVoid.copy(alpha = 0.65f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTogglePlay,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        // Contador de tiempo en milisegundos (esquina inferior derecha).
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(CraftVoid.copy(alpha = 0.82f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = TimeFormat.timecodeWithMillis(positionMs),
                color = Color.White,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private const val POSITION_POLL_MS = 50L
