package com.example.videoecraft.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import coil3.compose.AsyncImage
import com.example.videoecraft.data.repository.Project
import com.example.videoecraft.ui.components.CraftBadge
import com.example.videoecraft.ui.components.CraftPrimaryButton
import com.example.videoecraft.ui.components.clickableCraft
import com.example.videoecraft.ui.picker.rememberMediaPicker
import com.example.videoecraft.ui.theme.CraftAccentEnd
import com.example.videoecraft.ui.theme.CraftAccentStart
import com.example.videoecraft.ui.theme.CraftSurface
import com.example.videoecraft.ui.theme.CraftSurfaceHigh
import com.example.videoecraft.ui.theme.CraftTextPrimary
import com.example.videoecraft.ui.theme.CraftTextSecondary
import com.example.videoecraft.ui.theme.CraftVoid
import com.example.videoecraft.ui.theme.VideoECraftTheme
import com.example.videoecraft.ui.util.TimeFormat

/**
 * Pantalla de inicio de VideoEcraft.
 *
 * Cabecera minimalista con el logo + CTA "+ Nuevo proyecto" + cuadrícula
 * de proyectos recientes con miniatura y duración.
 */
@UnstableApi
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenProject: (Long) -> Unit,
    onOpenHelp: () -> Unit = {},
    onOpenCredits: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Photo Picker: no requiere permisos y devuelve los Uri elegidos.
    val pickMedia = rememberMediaPicker(maxItems = 20) { uris ->
        viewModel.onMediaPicked(uris)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.OpenEditor -> onOpenProject(event.projectId)
                is HomeEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
            }
        }
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
            HomeHeader(
                onOpenHelp = onOpenHelp,
                onOpenCredits = onOpenCredits,
            )

            NewProjectButton(
                isImporting = uiState.isImporting,
                onClick = pickMedia,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )

            AnimatedVisibility(
                visible = uiState.projects.isNotEmpty(),
                enter = fadeIn() + slideInVertically { it / 6 },
                exit = fadeOut(),
            ) {
                Text(
                    text = "Borradores recientes",
                    color = CraftTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(start = 20.dp, bottom = 10.dp),
                )
            }

            when {
                uiState.isLoading -> LoadingState()
                uiState.isEmpty -> EmptyState(onCreateProject = pickMedia)
                else -> ProjectGrid(
                    projects = uiState.projects,
                    onOpenProject = onOpenProject,
                    onDeleteProject = viewModel::deleteProject,
                )
            }
        }
    }
}

/** Cabecera: logo "VideoEcraft", tagline y accesos a Ayuda / Créditos. */
@Composable
private fun HomeHeader(
    onOpenHelp: () -> Unit = {},
    onOpenCredits: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(listOf(CraftAccentStart, CraftAccentEnd)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = CraftVoid,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "VideoEcraft",
                    color = CraftTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                )
                Spacer(Modifier.width(8.dp))
                CraftBadge(text = "SIN MARCA")
            }
            Text(
                text = "Editor de video",
                color = CraftTextSecondary,
                fontSize = 11.sp,
            )
        }

        Spacer(Modifier.width(4.dp))

        // Acceso a Ayuda.
        HeaderIconButton(
            icon = Icons.AutoMirrored.Filled.HelpOutline,
            contentDescription = "Ayuda",
            onClick = onOpenHelp,
        )

        // Acceso a Créditos.
        HeaderIconButton(
            icon = Icons.Default.Info,
            contentDescription = "Créditos",
            onClick = onOpenCredits,
        )
    }
}

/**
 * Botón de icono de la cabecera.
 *
 * Se factoriza porque Ayuda y Créditos comparten tamaño, forma y
 * tratamiento visual: mantener una sola definición evita que se
desenrosquen con el tiempo.
 */
@Composable
private fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(CraftSurfaceHigh)
            .clickable(
                interactionSource = remember {
                    androidx.compose.foundation.interaction.MutableInteractionSource()
                },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = CraftTextSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** CTA principal "+ Nuevo proyecto". */
@Composable
private fun NewProjectButton(
    isImporting: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        CraftPrimaryButton(
            text = if (isImporting) "Importando…" else "Nuevo proyecto",
            onClick = onClick,
            enabled = !isImporting,
            icon = Icons.Default.Add,
            modifier = Modifier.fillMaxWidth(),
        )

        if (isImporting) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 20.dp)
                    .size(20.dp),
                color = CraftVoid,
                strokeWidth = 2.dp,
            )
        }
    }
}

/** Cuadrícula de proyectos en 2 columnas. */
@Composable
private fun ProjectGrid(
    projects: List<Project>,
    onOpenProject: (Long) -> Unit,
    onDeleteProject: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = projects, key = { it.id }) { project ->
            ProjectCard(
                project = project,
                onOpen = { onOpenProject(project.id) },
                onDelete = { onDeleteProject(project.id) },
            )
        }
    }
}

/** Tarjeta de proyecto con miniatura, duración y menú contextual. */
@Composable
private fun ProjectCard(
    project: Project,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CraftSurface)
            .clickableCraft(
                enabled = true,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                onClick = onOpen,
            ),
    ) {
        // --- Miniatura 16:9 con duración superpuesta ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(CraftSurfaceHigh),
        ) {
            if (project.thumbnailUri != null) {
                AsyncImage(
                    model = project.thumbnailUri,
                    contentDescription = project.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MovieCreation,
                    contentDescription = null,
                    tint = CraftTextSecondary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(28.dp),
                )
            }

            // Duración en la esquina inferior derecha.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(CraftVoid.copy(alpha = 0.82f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = TimeFormat.duration(project.durationMs),
                    color = CraftTextPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // --- Pie: nombre, metadatos y menú ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    color = CraftTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${project.clipCount} clips · ${TimeFormat.relativeDate(project.updatedAt)}",
                    color = CraftTextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Opciones",
                        tint = CraftTextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Abrir") },
                        onClick = {
                            menuExpanded = false
                            onOpen()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar") },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

/** Spinner centrado mientras Room entrega la primera lista. */
@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 80.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = CraftAccentStart,
            strokeWidth = 2.5.dp,
            modifier = Modifier.size(32.dp),
        )
    }
}

/** Estado vacío con ilustración y CTA. */
@Composable
private fun EmptyState(
    onCreateProject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp)
            .padding(bottom = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(CraftSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.MovieCreation,
                contentDescription = null,
                tint = CraftAccentEnd,
                modifier = Modifier.size(34.dp),
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Aún no hay proyectos",
            color = CraftTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Toca «Nuevo proyecto» para importar tus videos y empezar a editar.",
            color = CraftTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
        )

        Spacer(Modifier.height(24.dp))

        CraftPrimaryButton(
            text = "Empezar ahora",
            onClick = onCreateProject,
            icon = Icons.Default.Add,
        )
    }
}

// ---------------------------------------------------------------------
//  Previews
// ---------------------------------------------------------------------

@Preview(showBackground = true, backgroundColor = 0xFF101014, heightDp = 780)
@Composable
private fun HomeEmptyPreview() {
    VideoECraftTheme {
        EmptyState(onCreateProject = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF101014, heightDp = 500)
@Composable
private fun HomeHeaderPreview() {
    VideoECraftTheme {
        Column(modifier = Modifier.background(CraftVoid)) {
            HomeHeader()
            NewProjectButton(isImporting = false, onClick = {})
            NewProjectButton(isImporting = true, onClick = {})
        }
    }
}
