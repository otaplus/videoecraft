package com.example.videoecraft.ui.editor.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.example.videoecraft.ui.editor.EditorTool
import com.example.videoecraft.ui.theme.CraftAccentStart
import com.example.videoecraft.ui.theme.CraftOutline
import com.example.videoecraft.ui.theme.CraftSurfaceHigh
import com.example.videoecraft.ui.theme.CraftTextSecondary
import com.example.videoecraft.ui.theme.CraftVoid

/**
 * Barra de herramientas inferior del editor (scroll horizontal).
 *
 * Las 8 herramientas del editor, cada una con icono y etiqueta. La activa
 * se resalta con el degradado de acento. El scroll horizontal permite que
 * quepan todas sin comprimir los toques por debajo del mínimo táctil de
 * 48 dp.
 */
@UnstableApi
@Composable
fun EditorToolBar(
    activeTool: EditorTool,
    onSelectTool: (EditorTool) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(TOOLS) { tool ->
            ToolButton(
                tool = tool,
                selected = activeTool == tool,
                onClick = { onSelectTool(tool) },
            )
        }
    }
}

@Composable
private fun ToolButton(
    tool: EditorTool,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) CraftSurfaceHigh else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (selected) CraftAccentStart else CraftSurfaceHigh)
                .then(
                    if (selected) {
                        Modifier
                    } else {
                        Modifier.border(1.dp, CraftOutline, RoundedCornerShape(11.dp))
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tool.icon(),
                contentDescription = tool.label,
                tint = if (selected) CraftVoid else CraftTextSecondary,
                modifier = Modifier.size(19.dp),
            )
        }

        Text(
            text = tool.label,
            color = if (selected) CraftAccentStart else CraftTextSecondary,
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/** Icono asociado a cada herramienta. */
private fun EditorTool.icon(): ImageVector = when (this) {
    EditorTool.TRIM -> Icons.Default.ContentCut
    EditorTool.TRANSITIONS -> Icons.Default.Transform
    EditorTool.CHROMA -> Icons.Default.CropFree
    EditorTool.SPEED -> Icons.Default.Speed
    EditorTool.AUDIO -> Icons.Default.MusicNote
    EditorTool.TEXT -> Icons.Default.TextFields
    EditorTool.FILTERS -> Icons.Default.Tune
    EditorTool.CANVAS -> Icons.Default.AutoFixHigh
    EditorTool.NONE -> Icons.Default.Tune
}

/** Herramientas visibles en la barra, en orden de uso. */
private val TOOLS = listOf(
    EditorTool.TRIM,
    EditorTool.TRANSITIONS,
    EditorTool.SPEED,
    EditorTool.CHROMA,
    EditorTool.AUDIO,
    EditorTool.TEXT,
    EditorTool.FILTERS,
    EditorTool.CANVAS,
)

/** Alto reservado por la barra (toolbar + panel cuando está abierto). */
internal val ToolBarHeight = 92.dp
