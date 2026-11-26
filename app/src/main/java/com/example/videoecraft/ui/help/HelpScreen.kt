package com.example.videoecraft.ui.help

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.videoecraft.ui.components.CraftCard
import com.example.videoecraft.ui.components.CraftChip
import com.example.videoecraft.ui.theme.CraftAccentEnd
import com.example.videoecraft.ui.theme.CraftAccentGradient
import com.example.videoecraft.ui.theme.CraftAccentStart
import com.example.videoecraft.ui.theme.CraftOutline
import com.example.videoecraft.ui.theme.CraftSurface
import com.example.videoecraft.ui.theme.CraftSurfaceHigh
import com.example.videoecraft.ui.theme.CraftTextPrimary
import com.example.videoecraft.ui.theme.CraftTextSecondary
import com.example.videoecraft.ui.theme.CraftVoid
import com.example.videoecraft.ui.theme.VideoECraftTheme

/**
 * Pantalla de Ayuda.
 *
 * Tres pestañas en lugar de un scroll único e interminable:
 *  1. **Empezar** — guía de 4 pasos para la primera edición.
 *  2. **Herramientas** — qué hace cada una y sus trucos.
 *  3. **Dudas** — preguntas frecuentes con respuesta desplegable.
 *
 * Se usa una fila de chips como selector de pestaña porque el editor y Home
 * ya usan `CraftChip` para elegir opciones: mantener el mismo control
 * ahorra tener que enseñar un componente nuevo al usuario.
 */
@Composable
fun HelpScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // rememberSaveable para no perder la pestaña al rotar el dispositivo.
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CraftVoid,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            HelpTopBar(onBack = onBack)

            // ---- Selector de pestañas ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val tabs = listOf("Empezar", "Herramientas", "Dudas")
                tabs.forEachIndexed { index, label ->
                    CraftChip(
                        label = label,
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            when (selectedTab) {
                0 -> QuickStartTab()
                1 -> ToolsTab()
                else -> FaqTab()
            }
        }
    }
}

/** Barra superior con botón atrás y título. */
@Composable
private fun HelpTopBar(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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

        Column {
            Text(
                text = "Ayuda",
                color = CraftTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Todo lo que necesitas para empezar",
                color = CraftTextSecondary,
                fontSize = 11.sp,
            )
        }
    }
}

// ---------------------------------------------------------------------
//  Pestaña 1 · Empezar
// ---------------------------------------------------------------------

@Composable
private fun QuickStartTab(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(HelpContent.QUICK_START) { step ->
            CraftCard(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp)) {
                    // Número del paso en círculo con degradado.
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(CraftAccentGradient),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = step.order.toString(),
                            color = CraftVoid,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(Modifier.width(14.dp))

                    Column {
                        Text(
                            text = step.title,
                            color = CraftTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = step.body,
                            color = CraftTextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
//  Pestaña 2 · Herramientas
// ---------------------------------------------------------------------

@Composable
private fun ToolsTab(modifier: Modifier = Modifier) {
    // Índice de la herramienta expandida; -1 = todas cerradas.
    var expandedIndex by rememberSaveable { mutableIntStateOf(-1) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(HelpContent.TOOL_HELP.size) { index ->
            val tool = HelpContent.TOOL_HELP[index]
            val expanded = expandedIndex == index

            ToolHelpCard(
                tool = tool,
                expanded = expanded,
                onToggle = {
                    // Tocar la abierta la cierra; tocar otra la cambia.
                    expandedIndex = if (expanded) -1 else index
                },
            )
        }
    }
}

/** Tarjeta desplegable con la descripción y los trucos de una herramienta. */
@Composable
private fun ToolHelpCard(
    tool: HelpContent.ToolHelp,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "chevronRotation",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CraftSurface)
            .border(1.dp, CraftOutline.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
    ) {
        // ---- Cabecera tocable ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggle,
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = tool.toolName,
                color = CraftTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Contraer" else "Expandir",
                tint = CraftTextSecondary,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(rotation),
            )
        }

        // ---- Contenido desplegable ----
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            ) {
                Text(
                    text = tool.description,
                    color = CraftTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )

                if (tool.tips.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    tool.tips.forEach { tip ->
                        Row(
                            modifier = Modifier.padding(bottom = 8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = CraftAccentEnd,
                                modifier = Modifier
                                    .padding(top = 1.dp)
                                    .size(13.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = tip,
                                color = CraftTextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
//  Pestaña 3 · Preguntas frecuentes
// ---------------------------------------------------------------------

@Composable
private fun FaqTab(modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(HelpContent.FAQ) { item ->
            FaqCard(item)
        }
    }
}

/** Pregunta con la respuesta desplegable. */
@Composable
private fun FaqCard(
    item: HelpContent.FaqItem,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "faqChevron",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CraftSurface)
            .border(1.dp, CraftOutline.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { expanded = !expanded },
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.question,
                color = CraftTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Contraer" else "Expandir",
                tint = CraftAccentStart,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(rotation),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Text(
                text = item.answer,
                color = CraftTextSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------
//  Preview
// ---------------------------------------------------------------------

@Preview(showBackground = true, backgroundColor = 0xFF101014, heightDp = 700)
@Composable
private fun HelpQuickStartPreview() {
    VideoECraftTheme {
        Column(modifier = Modifier.background(CraftVoid)) {
            HelpTopBar(onBack = {})
            Spacer(Modifier.height(10.dp))
            Box(modifier = Modifier.height(560.dp)) {
                QuickStartTab()
            }
        }
    }
}
