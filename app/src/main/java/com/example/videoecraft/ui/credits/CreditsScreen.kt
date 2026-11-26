package com.example.videoecraft.ui.credits

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.videoecraft.BuildConfig
import com.example.videoecraft.ui.components.CraftBadge
import com.example.videoecraft.ui.components.CraftCard
import com.example.videoecraft.ui.components.CraftDivider
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
 * Pantalla de Créditos.
 *
 * Destaca al estudio **Otaplus** como autor del proyecto y añade el resto
 * de información que se espera en una app open source: versión, licencia,
 * stack tecnológico y agradecimientos.
 *
 * Los enlaces se abren con un `Intent` explícito al navegador / cliente de
 * correo del usuario. Si no hay ninguna app que pueda atenderlo, se ignora
 * silenciosamente: no tiene sentido mostrar un error por un enlace de web.
 */
@Composable
fun CreditsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Abre una URL; ignora el fallo si no hay navegador instalado.
    val openUrl: (String) -> Unit = { url ->
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    // Abre el cliente de correo.
    val sendEmail: (String) -> Unit = { address ->
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = CraftVoid,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { CreditsTopBar(onBack = onBack) }

            // ---------------- Hero de Otaplus ----------------

            item { OtaplusHero() }

            // ---------------- Enlaces del estudio ----------------

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinkPill(
                        icon = Icons.Default.Language,
                        label = "Web",
                        onClick = { openUrl(CreditsData.OTAPLUS_URL) },
                        modifier = Modifier.weight(1f),
                    )
                    LinkPill(
                        icon = Icons.Default.Email,
                        label = "Contacto",
                        onClick = { sendEmail(CreditsData.OTAPLUS_CONTACT_EMAIL) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item {
                LinkPill(
                    icon = Icons.Default.Code,
                    label = "Ver en GitHub",
                    onClick = { openUrl(CreditsData.REPOSITORY_URL) },
                    modifier = Modifier.fillMaxWidth(),
                    highlighted = true,
                )
            }

            // ---------------- Hecho con ----------------

            item {
                SectionTitle("Hecho con")
            }

            items(CreditsData.TECHNOLOGIES) { tech ->
                TechnologyRow(tech)
            }

            // ---------------- Agradecimientos ----------------

            item {
                SectionTitle("Agradecimientos")
            }

            items(CreditsData.ACKNOWLEDGEMENTS) { acknowledgement ->
                CraftCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = CraftAccentEnd,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(13.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = acknowledgement.title,
                                color = CraftTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = acknowledgement.reason,
                                color = CraftTextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                }
            }

            // ---------------- Licencia ----------------

            item {
                SectionTitle("Licencia")
            }

            item {
                CraftCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { openUrl(CreditsData.LICENSE_URL) },
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = CreditsData.LICENSE_NAME,
                                color = CraftTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Uso libre, modificación y distribución",
                                color = CraftTextSecondary,
                                fontSize = 10.sp,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = CraftTextSecondary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }

            // ---------------- Pie ----------------

            item {
                Spacer(Modifier.height(6.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CraftDivider(modifier = Modifier.padding(horizontal = 40.dp))
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Hecho con cuidado por ${CreditsData.AUTHOR_NAME}",
                        color = CraftTextSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "${CreditsData.APP_NAME} v${CreditsData.APP_VERSION}" +
                            " (${CreditsData.APP_VERSION_CODE})",
                        color = CraftTextSecondary.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                    )

                    // Aviso honesto: en builds de debug con los enlaces de
                    // ejemplo todavía sin sustituir, se dice claramente.
                    if (CreditsData.usesPlaceholderLinks && BuildConfig.DEBUG) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Enlaces de contacto pendientes de configurar",
                            color = CraftAccentEnd.copy(alpha = 0.7f),
                            fontSize = 9.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------
//  Componentes
// ---------------------------------------------------------------------

/** Barra superior con botón atrás. */
@Composable
private fun CreditsTopBar(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
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
        Text(
            text = "Créditos",
            color = CraftTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Bloque principal: logo, nombre de la app y autoría de Otaplus.
 * Es lo primero que se ve al entrar, y el único elemento con degradado
 * a pantalla completa: la jerarquía visual marca dónde mirar.
 */
@Composable
private fun OtaplusHero(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(CraftSurfaceHigh, CraftSurface),
                ),
            )
            .border(1.dp, CraftOutline.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            .padding(vertical = 28.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Logo de la app con degradado.
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(CraftAccentGradient),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Movie,
                contentDescription = null,
                tint = CraftVoid,
                modifier = Modifier.size(32.dp),
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = CreditsData.APP_NAME,
                color = CraftTextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
            )
            Spacer(Modifier.width(8.dp))
            CraftBadge(text = "SIN MARCA")
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Editor de video open source",
            color = CraftTextSecondary,
            fontSize = 11.sp,
        )

        Spacer(Modifier.height(22.dp))

        // Separador con el nombre del estudio.
        CraftDivider(modifier = Modifier.padding(horizontal = 30.dp))

        Spacer(Modifier.height(18.dp))

        Text(
            text = "UN PROYECTO DE",
            color = CraftTextSecondary.copy(alpha = 0.7f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
        )

        Spacer(Modifier.height(8.dp))

        // Nombre del estudio con el degradado de acento.
        Text(
            text = CreditsData.AUTHOR_NAME,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)),
            color = CraftAccentStart,
        )

        Spacer(Modifier.height(6.dp))

        Text(
            text = CreditsData.AUTHOR_ROLE,
            color = CraftTextSecondary,
            fontSize = 11.sp,
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = "\"${CreditsData.AUTHOR_TAGLINE}\"",
            color = CraftTextSecondary.copy(alpha = 0.75f),
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** Píldora-enlace con icono. */
@Composable
private fun LinkPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val background = if (highlighted) {
        Brush.linearGradient(listOf(CraftAccentStart, CraftAccentEnd))
    } else {
        Brush.linearGradient(listOf(CraftSurfaceHigh, CraftSurfaceHigh))
    }
    val contentColor = if (highlighted) CraftVoid else CraftTextPrimary

    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(
                width = if (highlighted) 0.dp else 1.dp,
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = label,
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** Título de sección con la jerarquía tipográfica de la app. */
@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = CraftTextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.9.sp,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/** Fila de una tecnología del stack. */
@Composable
private fun TechnologyRow(
    tech: CreditsData.Technology,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CraftSurface)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tech.name,
                color = CraftTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = tech.description,
                color = CraftTextSecondary,
                fontSize = 10.sp,
                lineHeight = 14.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = tech.license,
            color = CraftAccentEnd.copy(alpha = 0.8f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ---------------------------------------------------------------------
//  Preview
// ---------------------------------------------------------------------

@Preview(showBackground = true, backgroundColor = 0xFF101014, heightDp = 620)
@Composable
private fun OtaplusHeroPreview() {
    VideoECraftTheme {
        Column(
            modifier = Modifier
                .background(CraftVoid)
                .padding(20.dp),
        ) {
            OtaplusHero()
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LinkPill(Icons.Default.Language, "Web", {}, Modifier.weight(1f))
                LinkPill(Icons.Default.Email, "Contacto", {}, Modifier.weight(1f))
            }
        }
    }
}
