package com.example.videoecraft.ui.editor.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.example.videoecraft.engine.PlaybackSpeed
import com.example.videoecraft.engine.effects.ColorAdjustments
import com.example.videoecraft.engine.effects.TransitionType
import com.example.videoecraft.engine.effects.VideoFilter
import com.example.videoecraft.engine.model.AspectRatio
import com.example.videoecraft.engine.model.BackgroundFill
import com.example.videoecraft.ui.components.CraftChip
import com.example.videoecraft.ui.editor.EditorTool
import com.example.videoecraft.ui.editor.EditorUiState
import com.example.videoecraft.ui.theme.CraftAccentEnd
import com.example.videoecraft.ui.theme.CraftAccentStart
import com.example.videoecraft.ui.theme.CraftError
import com.example.videoecraft.ui.theme.CraftOutline
import com.example.videoecraft.ui.theme.CraftSurface
import com.example.videoecraft.ui.theme.CraftSurfaceHigh
import com.example.videoecraft.ui.theme.CraftTextPrimary
import com.example.videoecraft.ui.theme.CraftTextSecondary
import com.example.videoecraft.ui.theme.CraftVoid

/**
 * Contenedor del panel de herramienta activo.
 *
 * Todos los paneles comparten el mismo chasis (tarjeta `#1C1C24` con
 * esquinas y padding consistentes) para que cambiar de herramienta no
 * produzca saltos visuales.
 */
@UnstableApi
@Composable
fun ToolPanel(
    state: EditorUiState,
    actions: ToolActions,
    modifier: Modifier = Modifier,
) {
    val tool = state.activeTool
    if (tool == EditorTool.NONE) return

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(CraftSurface)
            .padding(vertical = 14.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Título del panel.
            Text(
                text = tool.label.uppercase(),
                color = CraftTextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 16.dp, bottom = 10.dp),
            )

            when (tool) {
                EditorTool.TRIM -> TrimPanel(actions)
                EditorTool.TRANSITIONS -> TransitionsPanel(state, actions)
                EditorTool.CHROMA -> ChromaPanel(state, actions)
                EditorTool.SPEED -> SpeedPanel(state, actions)
                EditorTool.AUDIO -> AudioPanel(state, actions)
                EditorTool.TEXT -> TextPanel(actions)
                EditorTool.FILTERS -> FiltersPanel(state, actions)
                EditorTool.CANVAS -> CanvasPanel(state, actions)
                EditorTool.NONE -> Unit
            }
        }
    }
}

/**
 * Acciones que los paneles pueden invocar.
 *
 * Se agrupan en un objeto en lugar de pasar 20 lambdas sueltas: mantiene
 * las firmas de los composables legibles y facilita añadir herramientas.
 */
@UnstableApi
data class ToolActions(
    // Recorte
    val onSplit: () -> Unit = {},
    val onDeleteClip: () -> Unit = {},
    val onTrim: (startMs: Long, endMs: Long) -> Unit = { _, _ -> },
    val onAddClip: () -> Unit = {},
    // Transiciones
    val onTransition: (TransitionType) -> Unit = {},
    // Croma
    val onChromaSimilarity: (Float) -> Unit = {},
    val onChromaSmoothness: (Float) -> Unit = {},
    val onChromaSpill: (Float) -> Unit = {},
    val onChromaColor: (Int) -> Unit = {},
    val onChromaClear: () -> Unit = {},
    // Velocidad
    val onSpeed: (PlaybackSpeed) -> Unit = {},
    // Audio
    val onPickMusic: () -> Unit = {},
    val onClearMusic: () -> Unit = {},
    val onMusicVolume: (Float) -> Unit = {},
    val onOriginalVolume: (Float) -> Unit = {},
    val onMuteOriginal: (Boolean) -> Unit = {},
    // Texto
    val onAddText: (String) -> Unit = {},
    // Filtros
    val onFilter: (ColorAdjustments) -> Unit = {},
    val onBrightness: (Float) -> Unit = {},
    val onContrast: (Float) -> Unit = {},
    val onSaturation: (Float) -> Unit = {},
    val onWarmth: (Float) -> Unit = {},
    val onResetColor: () -> Unit = {},
    // Lienzo
    val onAspectRatio: (AspectRatio) -> Unit = {},
    val onBackground: (BackgroundFill) -> Unit = {},
)

// ---------------------------------------------------------------------
//  Recortar y dividir
// ---------------------------------------------------------------------

@Composable
private fun TrimPanel(actions: ToolActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TrimAction(
            icon = Icons.Default.ContentCut,
            label = "Dividir",
            description = "Partir en el cabezal",
            onClick = actions.onSplit,
            modifier = Modifier.weight(1f),
        )
        TrimAction(
            icon = Icons.Default.Delete,
            label = "Borrar",
            description = "Quitar clip",
            onClick = actions.onDeleteClip,
            tint = CraftError,
            modifier = Modifier.weight(1f),
        )
        TrimAction(
            icon = Icons.Default.ContentCopy,
            label = "Añadir",
            description = "Más clips",
            onClick = actions.onAddClip,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TrimAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = CraftTextPrimary,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CraftSurfaceHigh)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = description,
            color = CraftTextSecondary,
            fontSize = 9.sp,
        )
    }
}

// ---------------------------------------------------------------------
//  Transiciones
// ---------------------------------------------------------------------

@Composable
private fun TransitionsPanel(state: EditorUiState, actions: ToolActions) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(TransitionType.entries) { transition ->
            TransitionCell(
                transition = transition,
                selected = state.selectedTransition == transition,
                onClick = { actions.onTransition(transition) },
            )
        }
    }
}

@Composable
private fun TransitionCell(
    transition: TransitionType,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) CraftAccentStart else CraftSurfaceHigh)
                .border(
                    width = if (selected) 0.dp else 1.dp,
                    color = CraftOutline,
                    shape = RoundedCornerShape(12.dp),
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = transitionEmoji(transition),
                fontSize = 20.sp,
            )
        }
        Text(
            text = transition.displayName,
            color = if (selected) CraftAccentStart else CraftTextSecondary,
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** Icono textual para cada transición (evita 6 vectores extra). */
private fun transitionEmoji(transition: TransitionType): String = when (transition) {
    TransitionType.NONE -> "—"
    TransitionType.FADE_TO_BLACK -> "◐"
    TransitionType.DISSOLVE -> "◑"
    TransitionType.SLIDE_LEFT -> "←"
    TransitionType.SLIDE_RIGHT -> "→"
    TransitionType.ZOOM_IN -> "⊕"
    TransitionType.ZOOM_OUT -> "⊖"
}

// ---------------------------------------------------------------------
//  Croma
// ---------------------------------------------------------------------

@Composable
private fun ChromaPanel(state: EditorUiState, actions: ToolActions) {
    val settings = state.chromaSettings

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Selector de color: verde / azul / personalizado.
        Text(
            text = "Color clave",
            color = CraftTextSecondary,
            fontSize = 10.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CraftChip(
                label = "Verde",
                selected = settings.keyColor == Triple(0f, 1f, 0f),
                onClick = { actions.onChromaColor(0xFF00FF00.toInt()) },
            )
            CraftChip(
                label = "Azul",
                selected = settings.keyColor == Triple(0f, 0f, 1f),
                onClick = { actions.onChromaColor(0xFF0000FF.toInt()) },
            )
            CraftChip(
                label = "Quitar",
                selected = state.selectedOverlay?.chromaKey == null,
                onClick = actions.onChromaClear,
            )
        }

        ToolSlider(
            label = "Similitud",
            value = settings.similarity,
            valueRange = 0f..1f,
            displayValue = "${(settings.similarity * 100).toInt()}%",
            onValueChange = actions.onChromaSimilarity,
        )
        ToolSlider(
            label = "Suavizado",
            value = settings.smoothness,
            valueRange = 0f..0.5f,
            displayValue = "${(settings.smoothness * 100).toInt()}%",
            onValueChange = actions.onChromaSmoothness,
        )
        ToolSlider(
            label = "Spill",
            value = settings.spillReduction,
            valueRange = 0f..1f,
            displayValue = "${(settings.spillReduction * 100).toInt()}%",
            onValueChange = actions.onChromaSpill,
        )
    }
}

// ---------------------------------------------------------------------
//  Velocidad
// ---------------------------------------------------------------------

@Composable
private fun SpeedPanel(state: EditorUiState, actions: ToolActions) {
    val current = state.selectedSpeed

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Valor grande centrado.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = current.label,
                color = CraftAccentStart,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(PlaybackSpeed.PRESETS) { speed ->
                CraftChip(
                    label = speed.label,
                    selected = current.factor == speed.factor,
                    onClick = { actions.onSpeed(speed) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------
//  Audio
// ---------------------------------------------------------------------

@Composable
private fun AudioPanel(state: EditorUiState, actions: ToolActions) {
    val hasMusic = state.spec.music.isNotEmpty()
    val hasOriginalAudio = state.spec.clips.any { it.keepOriginalAudio }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Botón añadir / quitar música.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(CraftSurfaceHigh)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = if (hasMusic) actions.onClearMusic else actions.onPickMusic,
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = CraftAccentEnd,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = if (hasMusic) "Quitar música" else "Añadir música",
                color = CraftTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (hasMusic) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Quitar",
                    tint = CraftError,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        // Mezclador: música vs audio original.
        ToolSlider(
            label = "Música",
            value = state.musicVolume,
            valueRange = 0f..1f,
            displayValue = "${(state.musicVolume * 100).toInt()}%",
            onValueChange = actions.onMusicVolume,
        )
        ToolSlider(
            label = "Audio original",
            value = state.originalVolume,
            valueRange = 0f..1f,
            displayValue = "${(state.originalVolume * 100).toInt()}%",
            onValueChange = actions.onOriginalVolume,
        )

        // Interruptor de mute del audio original.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = if (hasOriginalAudio) {
                        Icons.AutoMirrored.Filled.VolumeUp
                    } else {
                        Icons.AutoMirrored.Filled.VolumeOff
                    },
                    contentDescription = null,
                    tint = CraftTextSecondary,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = "Silenciar original",
                    color = CraftTextSecondary,
                    fontSize = 12.sp,
                )
            }
            Switch(
                checked = !hasOriginalAudio,
                onCheckedChange = actions.onMuteOriginal,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CraftVoid,
                    checkedTrackColor = CraftAccentStart,
                    uncheckedThumbColor = CraftTextSecondary,
                    uncheckedTrackColor = CraftSurfaceHigh,
                ),
            )
        }
    }
}

// ---------------------------------------------------------------------
//  Texto
// ---------------------------------------------------------------------

@Composable
private fun TextPanel(actions: ToolActions) {
    var text by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CraftSurfaceHigh)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = CraftTextPrimary,
                    fontSize = 14.sp,
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(CraftAccentStart),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { innerTextField ->
                    if (text.isEmpty()) {
                        Text(
                            text = "Escribe un título…",
                            color = CraftTextSecondary,
                            fontSize = 14.sp,
                        )
                    }
                    innerTextField()
                },
            )
        }

        Box(
            modifier = Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CraftAccentStart)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        actions.onAddText(text)
                        text = ""
                    },
                )
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Añadir",
                color = CraftVoid,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ---------------------------------------------------------------------
//  Filtros y ajustes
// ---------------------------------------------------------------------

@Composable
private fun FiltersPanel(state: EditorUiState, actions: ToolActions) {
    val color = state.selectedColor

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Fila de presets.
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(VideoFilter.entries.toList()) { filter ->
                val selected = color == filter.adjustments
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) CraftAccentStart else CraftSurfaceHigh,
                            )
                            .border(
                                width = if (selected) 0.dp else 1.dp,
                                color = CraftOutline,
                                shape = RoundedCornerShape(10.dp),
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { actions.onFilter(filter.adjustments) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = filter.displayName.take(2),
                            color = if (selected) CraftVoid else CraftTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = filter.displayName,
                        color = if (selected) CraftAccentStart else CraftTextSecondary,
                        fontSize = 8.sp,
                        maxLines = 1,
                    )
                }
            }
        }

        // Ajustes manuales.
        ToolSlider(
            label = "Brillo",
            value = color.brightness,
            valueRange = -0.5f..0.5f,
            displayValue = String.format("%.2f", color.brightness),
            onValueChange = actions.onBrightness,
        )
        ToolSlider(
            label = "Contraste",
            value = color.contrast,
            valueRange = 0.5f..1.8f,
            displayValue = String.format("%.2f", color.contrast),
            onValueChange = actions.onContrast,
        )
        ToolSlider(
            label = "Saturación",
            value = color.saturation,
            valueRange = 0f..2f,
            displayValue = String.format("%.2f", color.saturation),
            onValueChange = actions.onSaturation,
        )
        ToolSlider(
            label = "Calidez",
            value = color.warmth,
            valueRange = -1f..1f,
            displayValue = String.format("%.2f", color.warmth),
            onValueChange = actions.onWarmth,
        )

        // Reset.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(CraftSurfaceHigh)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = actions.onResetColor,
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = CraftTextSecondary,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "Restablecer",
                    color = CraftTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------
//  Lienzo / fondo
// ---------------------------------------------------------------------

@Composable
private fun CanvasPanel(state: EditorUiState, actions: ToolActions) {
    val canvas = state.spec.canvas

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Relación de aspecto.
        Text(text = "Relación de aspecto", color = CraftTextSecondary, fontSize = 10.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AspectRatio.entries.forEach { aspect ->
                CraftChip(
                    label = aspect.label,
                    selected = canvas.aspectRatio == aspect,
                    onClick = { actions.onAspectRatio(aspect) },
                )
            }
        }

        Spacer(Modifier.height(2.dp))

        // Relleno de fondo.
        Text(text = "Fondo", color = CraftTextSecondary, fontSize = 10.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CraftChip(
                label = "Desenfoque",
                selected = canvas.background is BackgroundFill.Blur,
                onClick = { actions.onBackground(BackgroundFill.Blur()) },
            )
            CraftChip(
                label = "Negro",
                selected = canvas.background is BackgroundFill.Black,
                onClick = { actions.onBackground(BackgroundFill.Black) },
            )
            // Paleta de colores sólidos.
            BackgroundColorSwatch(
                color = CraftVoid,
                selected = canvas.background.let {
                    it is BackgroundFill.Solid && it.colorArgb == CraftVoid.value.toInt()
                },
                onClick = { actions.onBackground(BackgroundFill.Solid(0xFF101014.toInt())) },
            )
            BackgroundColorSwatch(
                color = CraftAccentStart,
                selected = canvas.background.let {
                    it is BackgroundFill.Solid && it.colorArgb == 0xFFFF5722.toInt()
                },
                onClick = { actions.onBackground(BackgroundFill.Solid(0xFFFF5722.toInt())) },
            )
            BackgroundColorSwatch(
                color = Color.White,
                selected = canvas.background.let {
                    it is BackgroundFill.Solid && it.colorArgb == 0xFFFFFFFF.toInt()
                },
                onClick = { actions.onBackground(BackgroundFill.Solid(0xFFFFFFFF.toInt())) },
            )
        }
    }
}

@Composable
private fun BackgroundColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) CraftAccentStart else CraftOutline,
                shape = CircleShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    )
}

// ---------------------------------------------------------------------
//  Slider reutilizable
// ---------------------------------------------------------------------

/**
 * Slider con etiqueta a la izquierda y valor numérico a la derecha.
 *
 * Se usa para todos los ajustes continuos (brillo, similitud de croma,
 * volúmenes…), de modo que el editor tenga un único lenguaje visual.
 */
@Composable
fun ToolSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                color = CraftTextSecondary,
                fontSize = 11.sp,
            )
            Text(
                text = displayValue,
                color = CraftTextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = CraftAccentStart,
                activeTrackColor = CraftAccentStart,
                inactiveTrackColor = CraftOutline,
            ),
            modifier = Modifier.height(28.dp),
        )
    }
}
