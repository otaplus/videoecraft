package com.example.videoecraft.ui.export

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.example.videoecraft.engine.model.ExportBitrate
import com.example.videoecraft.engine.model.ExportFps
import com.example.videoecraft.engine.model.ExportResolution
import com.example.videoecraft.export.ExportState
import com.example.videoecraft.ui.components.CraftCard
import com.example.videoecraft.ui.components.CraftChip
import com.example.videoecraft.ui.components.CraftPrimaryButton
import com.example.videoecraft.ui.components.CraftSecondaryButton
import com.example.videoecraft.ui.components.CraftSectionHeader
import com.example.videoecraft.ui.theme.CraftAccentEnd
import com.example.videoecraft.ui.theme.CraftAccentGradient
import com.example.videoecraft.ui.theme.CraftAccentStart
import com.example.videoecraft.ui.theme.CraftError
import com.example.videoecraft.ui.theme.CraftOutline
import com.example.videoecraft.ui.theme.CraftSuccess
import com.example.videoecraft.ui.theme.CraftSurface
import com.example.videoecraft.ui.theme.CraftSurfaceHigh
import com.example.videoecraft.ui.theme.CraftTextPrimary
import com.example.videoecraft.ui.theme.CraftTextSecondary
import com.example.videoecraft.ui.theme.CraftVoid
import com.example.videoecraft.ui.theme.VideoECraftTheme
import com.example.videoecraft.ui.util.TimeFormat

/**
 * Pantalla de exportación.
 *
 * Permite elegir resolución, FPS y calidad, y lanza el export con una barra
 * de progreso circular conectada en tiempo real a [ExportState].
 *
 * Mientras exporta, la configuración se bloquea y solo se ofrece Cancelar:
 * cambiar la resolución a mitad de codificación no tiene sentido y
 * confundiría al usuario.
 */
@UnstableApi
@Composable
fun ExportScreen(
    viewModel: ExportViewModel,
    onBack: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()

    val isExporting = exportState is ExportState.Running

    // Al completar, se avisa a la navegación tras un instante para que el
    // usuario vea el estado de éxito.
    LaunchedEffect(exportState) {
        if (exportState is ExportState.Completed) {
            kotlinx.coroutines.delay(1500)
            viewModel.resetExportState()
            onFinished()
        }
    }

    BackHandler {
        if (isExporting) viewModel.cancelExport() else onBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CraftVoid,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // ---- Cabecera ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { if (isExporting) viewModel.cancelExport() else onBack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = CraftTextPrimary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Exportar",
                        color = CraftTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = uiState.projectName,
                        color = CraftTextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (isExporting || exportState is ExportState.Completed || exportState is ExportState.Failed) {
                // ---- Estado del export ----
                ExportProgressState(
                    state = exportState,
                    onCancel = viewModel::cancelExport,
                    onRetry = {
                        viewModel.resetExportState()
                        viewModel.startExport()
                    },
                    modifier = Modifier.weight(1f),
                )
            } else {
                // ---- Configuración ----
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SummaryCard(uiState)

                    OptionSection(
                        title = "Resolución",
                        subtitle = "Cuanto más alta, mayor es el archivo",
                    ) {
                        ExportResolution.entries.forEach { resolution ->
                            CraftChip(
                                label = resolution.label,
                                selected = uiState.options.resolution == resolution,
                                onClick = { viewModel.setResolution(resolution) },
                            )
                        }
                    }

                    OptionSection(
                        title = "Fotogramas por segundo",
                        subtitle = "60 fps para movimiento rápido",
                    ) {
                        ExportFps.entries.forEach { fps ->
                            CraftChip(
                                label = fps.label,
                                selected = uiState.options.fps == fps,
                                onClick = { viewModel.setFps(fps) },
                            )
                        }
                    }

                    OptionSection(
                        title = "Calidad",
                        subtitle = "Bitrate del video exportado",
                    ) {
                        ExportBitrate.entries.forEach { bitrate ->
                            CraftChip(
                                label = bitrate.label,
                                selected = uiState.options.bitrate == bitrate,
                                onClick = { viewModel.setBitrate(bitrate) },
                            )
                        }
                    }
                }

                // ---- CTA ----
                Box(modifier = Modifier.padding(16.dp)) {
                    CraftPrimaryButton(
                        text = "Exportar video",
                        icon = Icons.Default.FileDownload,
                        enabled = !uiState.isLoading && uiState.clipCount > 0,
                        onClick = viewModel::startExport,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** Resumen del proyecto: duración, clips y tamaño estimado. */
@UnstableApi
@Composable
private fun SummaryCard(state: ExportUiState, modifier: Modifier = Modifier) {
    CraftCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(CraftAccentGradient),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = CraftVoid,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.projectName,
                        color = CraftTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${state.clipCount} clips · ${TimeFormat.durationLong(state.durationMs)}",
                        color = CraftTextSecondary,
                        fontSize = 11.sp,
                    )
                }

                Text(
                    text = String.format(
                        java.util.Locale.US,
                        "~%.0f MB",
                        state.estimatedSizeMb,
                    ),
                    color = CraftAccentEnd,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** Sección de opciones con título, subtítulo y fila de chips. */
@Composable
private fun OptionSection(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CraftSectionHeader(title = title, subtitle = subtitle)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            content()
        }
    }
}

/** Pantalla de progreso / resultado del export. */
@UnstableApi
@Composable
private fun ExportProgressState(
    state: ExportState,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (state) {
            is ExportState.Running -> {
                ExportProgressRing(progress = state.progress)

                Spacer(Modifier.height(24.dp))

                Text(
                    text = "${(state.progress * 100).toInt()}%",
                    color = CraftTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Codificando en tu dispositivo…",
                    color = CraftTextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(28.dp))

                CraftSecondaryButton(
                    text = "Cancelar",
                    onClick = onCancel,
                )
            }

            is ExportState.Completed -> {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = CraftSuccess,
                    modifier = Modifier.size(64.dp),
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "¡Listo!",
                    color = CraftTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Tu video está en Movies/VideoEcraft",
                    color = CraftTextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }

            is ExportState.Failed -> {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = CraftError,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "No se pudo exportar",
                    color = CraftTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = state.message,
                    color = CraftTextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                CraftPrimaryButton(
                    text = "Reintentar",
                    onClick = onRetry,
                )
            }

            ExportState.Cancelled -> {
                Text(
                    text = "Exportación cancelada",
                    color = CraftTextSecondary,
                    fontSize = 13.sp,
                )
            }

            ExportState.Idle -> Unit
        }
    }
}

/**
 * Anillo de progreso circular con degradado de acento.
 *
 * El degradado se aplica con `Brush.sweepGradient`: es lo que hace que el
 * anillo se vea "Craft" en lugar de un spinner genérico.
 */
@Composable
private fun ExportProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 320),
        label = "exportProgress",
    )

    Box(
        modifier = modifier.size(150.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Pista de fondo.
        CircularProgressIndicator(
            progress = { 1f },
            modifier = Modifier.fillMaxSize(),
            color = CraftSurfaceHigh,
            strokeWidth = 9.dp,
        )

        // Progreso real con degradado.
        CircularProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier.fillMaxSize(),
            color = CraftAccentStart,
            strokeWidth = 9.dp,
            strokeCap = StrokeCap.Round,
        )

        // Interior con el porcentaje ya renderizado arriba.
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(56.dp))
                .background(
                    Brush.radialGradient(
                        listOf(CraftSurface, CraftVoid),
                    ),
                ),
        )
    }
}

// ---------------------------------------------------------------------
//  Preview
// ---------------------------------------------------------------------

@Preview(showBackground = true, backgroundColor = 0xFF101014, heightDp = 600)
@UnstableApi
@Composable
private fun ExportSummaryPreview() {
    VideoECraftTheme {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CraftVoid)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SummaryCard(
                state = ExportUiState(
                    projectName = "Proyecto 1",
                    durationMs = 42_000L,
                    clipCount = 3,
                    isLoading = false,
                ),
            )
            OptionSection(title = "Resolución", subtitle = "Cuanto más alta, mayor es el archivo") {
                ExportResolution.entries.forEach { resolution ->
                    CraftChip(
                        label = resolution.label,
                        selected = resolution == ExportResolution.P1080,
                        onClick = {},
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF101014, heightDp = 500)
@UnstableApi
@Composable
private fun ExportProgressPreview() {
    VideoECraftTheme {
        ExportProgressState(
            state = ExportState.Running(progress = 0.42f),
            onCancel = {},
            onRetry = {},
        )
    }
}
