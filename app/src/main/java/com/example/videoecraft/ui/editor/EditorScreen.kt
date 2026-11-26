package com.example.videoecraft.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.example.videoecraft.engine.model.AspectRatio
import com.example.videoecraft.ui.components.CraftChip
import com.example.videoecraft.ui.editor.timeline.Timeline
import com.example.videoecraft.ui.editor.tools.EditorToolBar
import com.example.videoecraft.ui.editor.tools.ToolActions
import com.example.videoecraft.ui.editor.tools.ToolPanel
import com.example.videoecraft.ui.editor.viewer.VideoViewer
import com.example.videoecraft.ui.picker.rememberAudioPicker
import com.example.videoecraft.ui.theme.CraftAccentEnd
import com.example.videoecraft.ui.theme.CraftAccentStart
import com.example.videoecraft.ui.theme.CraftSurface
import com.example.videoecraft.ui.theme.CraftSurfaceHigh
import com.example.videoecraft.ui.theme.CraftTextPrimary
import com.example.videoecraft.ui.theme.CraftTextSecondary
import com.example.videoecraft.ui.theme.CraftVoid
import com.example.videoecraft.ui.theme.VideoECraftTheme

/**
 * Pantalla del editor.
 *
 * Composición vertical:
 * ```
 *  ┌─────────────────────────────────┐
 *  │ ← Editor     Guardar·Exportar   │
 *  ├─────────────────────────────────┤
 *  │                                 │
 *  │  VISOR (ExoPlayer + timecode)   │
 *  │                                 │
 *  │  [Original][16:9][9:16][1:1]    │
 *  ├─────────────────────────────────┤
 *  │  TIMELINE MULTICAPA             │
 *  ├─────────────────────────────────┤
 *  │  PANEL DE HERRAMIENTA           │
 *  │  BARRA DE HERRAMIENTAS          │
 *  └─────────────────────────────────┘
 * ```
 */
@UnstableApi
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Al salir del editor se fuerza el guardado del borrador.
    val handleBack = {
        viewModel.saveNow()
        onBack()
    }
    BackHandler(onBack = handleBack)

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    val pickMusic = rememberAudioPicker { uri -> viewModel.setMusic(uri) }

    // Acciones que invocan los paneles de herramientas.
    val actions = remember(viewModel) {
        ToolActions(
            onSplit = viewModel::splitClipAtPlayhead,
            onDeleteClip = { viewModel.deleteClip(viewModel.uiState.value.selectedClipIndex) },
            onTransition = viewModel::setTransition,
            onChromaSimilarity = viewModel::updateChromaSimilarity,
            onChromaSmoothness = viewModel::updateChromaSmoothness,
            onChromaSpill = viewModel::updateChromaSpill,
            onChromaColor = viewModel::setChromaKeyColor,
            onChromaClear = viewModel::clearChromaKey,
            onSpeed = viewModel::setSpeed,
            onPickMusic = pickMusic,
            onClearMusic = viewModel::clearMusic,
            onMusicVolume = viewModel::setMusicVolume,
            onOriginalVolume = viewModel::setOriginalVolume,
            onMuteOriginal = viewModel::toggleOriginalAudioMuted,
            onAddText = viewModel::addTextLayer,
            onFilter = viewModel::updateColorAdjustments,
            onBrightness = viewModel::updateBrightness,
            onContrast = viewModel::updateContrast,
            onSaturation = viewModel::updateSaturation,
            onWarmth = viewModel::updateWarmth,
            onResetColor = viewModel::resetColorAdjustments,
            onAspectRatio = viewModel::setAspectRatio,
            onBackground = viewModel::setBackground,
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CraftVoid,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            EditorTopBar(
                projectName = state.projectName,
                isSaving = state.isSaving,
                isPlaying = state.isPlaying,
                canExport = state.canExport,
                onBack = handleBack,
                onExport = onExport,
                onTogglePlay = {
                    viewModel.onPlaybackStateChange(!state.isPlaying)
                },
            )

            // ------------------- VISOR -------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                VideoViewer(
                    uri = state.selectedClip?.uri,
                    positionMs = state.playheadMs,
                    isPlaying = state.isPlaying,
                    aspectRatio = state.aspectRatio,
                    onPositionUpdate = viewModel::onPlayheadUpdate,
                    onPlaybackStateChange = viewModel::onPlaybackStateChange,
                    onTogglePlay = {
                        viewModel.onPlaybackStateChange(!state.isPlaying)
                    },
                )
            }

            // ------------ Selector de relación ------------
            AspectRatioSelector(
                current = state.spec.canvas.aspectRatio,
                onSelect = viewModel::setAspectRatio,
            )

            Spacer(Modifier.size(6.dp))

            // ------------------ TIMELINE ------------------
            Timeline(
                spec = state.spec,
                playheadMs = state.playheadMs,
                zoom = state.timelineZoom,
                selectedClipIndex = state.selectedClipIndex,
                onSeek = { positionMs ->
                    viewModel.onPlaybackStateChange(false)
                    viewModel.seekTo(positionMs)
                },
                onSelectClip = viewModel::selectClip,
                onZoomGesture = viewModel::onTimelineZoom,
                modifier = Modifier.background(CraftVoid),
            )

            // --------- PANEL DE HERRAMIENTA (animado) ---------
            AnimatedVisibility(
                visible = state.activeTool != EditorTool.NONE,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
            ) {
                ToolPanel(
                    state = state,
                    actions = actions,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }

            // ------------- BARRA DE HERRAMIENTAS -------------
            EditorToolBar(
                activeTool = state.activeTool,
                onSelectTool = viewModel::selectTool,
                modifier = Modifier.background(CraftSurface),
            )
        }
    }
}

/** Barra superior: volver, nombre, estado de guardado y exportar. */
@Composable
private fun EditorTopBar(
    projectName: String,
    isSaving: Boolean,
    isPlaying: Boolean,
    canExport: Boolean,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onTogglePlay: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Volver
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CraftSurfaceHigh)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Volver",
                tint = CraftTextPrimary,
                modifier = Modifier.size(19.dp),
            )
        }

        // Nombre + estado de guardado
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = projectName.ifBlank { "Editor" },
                color = CraftTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(8.dp),
                        color = CraftAccentStart,
                        strokeWidth = 1.5.dp,
                    )
                    Text(
                        text = "Guardando…",
                        color = CraftTextSecondary,
                        fontSize = 9.sp,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = CraftAccentEnd,
                        modifier = Modifier.size(10.dp),
                    )
                    Text(
                        text = "Borrador guardado",
                        color = CraftTextSecondary,
                        fontSize = 9.sp,
                    )
                }
            }
        }

        // Play/pause compacto
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(CraftSurfaceHigh)
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
                tint = CraftTextPrimary,
                modifier = Modifier.size(19.dp),
            )
        }

        // Exportar
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (canExport) {
                        Brush.linearGradient(listOf(CraftAccentStart, CraftAccentEnd))
                    } else {
                        Brush.linearGradient(listOf(CraftSurfaceHigh, CraftSurfaceHigh))
                    },
                )
                .clickable(
                    enabled = canExport,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onExport,
                )
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = Icons.Default.FileUpload,
                contentDescription = null,
                tint = if (canExport) CraftVoid else CraftTextSecondary,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = "Exportar",
                color = if (canExport) CraftVoid else CraftTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Fila de chips para elegir la relación de aspecto del lienzo. */
@Composable
private fun AspectRatioSelector(
    current: AspectRatio,
    onSelect: (AspectRatio) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AspectRatio.entries.forEach { aspect ->
            CraftChip(
                label = aspect.label,
                selected = current == aspect,
                onClick = { onSelect(aspect) },
            )
        }
    }
}

// ---------------------------------------------------------------------
//  Preview
// ---------------------------------------------------------------------

@Preview(showBackground = true, backgroundColor = 0xFF101014, widthDp = 400)
@Composable
private fun EditorTopBarPreview() {
    VideoECraftTheme {
        EditorTopBar(
            projectName = "Proyecto 1",
            isSaving = false,
            isPlaying = false,
            canExport = true,
            onBack = {},
            onExport = {},
            onTogglePlay = {},
        )
    }
}
