package com.example.videoecraft.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.videoecraft.ui.theme.CraftAccentEnd
import com.example.videoecraft.ui.theme.CraftAccentGradient
import com.example.videoecraft.ui.theme.CraftAccentStart
import com.example.videoecraft.ui.theme.CraftOutline
import com.example.videoecraft.ui.theme.CraftSurface
import com.example.videoecraft.ui.theme.CraftSurfaceHigh
import com.example.videoecraft.ui.theme.CraftTextPrimary
import com.example.videoecraft.ui.theme.CraftTextSecondary

/**
 * Botón principal con degradado Naranja Neón → Coral.
 *
 * Es el CTA de VideoEcraft ("+ Nuevo proyecto", "Exportar"). Tiene
 * animación de escala al pulsar para dar respuesta táctil inmediata, algo
 * que los botones Material estándar no aportan en un editor donde se toca
 * mucho y muy rápido.
 */
@Composable
fun CraftPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        label = "primaryButtonScale",
    )

    val gradient = if (enabled) {
        CraftAccentGradient
    } else {
        Brush.linearGradient(listOf(CraftOutline, CraftOutline))
    }

    Box(
        modifier = modifier
            .scale(scale)
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(gradient)
            .clickableCraft(enabled = enabled, interactionSource = interactionSource, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF101014),
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = text,
                color = Color(0xFF101014),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Botón secundario: superficie elevada con borde sutil.
 * Se usa en acciones no destructivas del editor (cancelar, ajustes).
 */
@Composable
fun CraftSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        label = "secondaryButtonScale",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CraftSurfaceHigh)
            .border(BorderStroke(1.dp, CraftOutline), RoundedCornerShape(16.dp))
            .clickableCraft(enabled = enabled, interactionSource = interactionSource, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = CraftTextPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = text,
                color = CraftTextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
            )
        }
    }
}

/**
 * Tarjeta Craft: superficie `#1C1C24` con esquinas redondeadas.
 * La base visual de las tarjetas de proyecto y los paneles del editor.
 */
@Composable
fun CraftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val base = modifier
        .clip(shape)
        .background(CraftSurface)

    val clickable = if (onClick != null) {
        base.then(
            Modifier.clickableCraft(
                enabled = true,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        )
    } else {
        base
    }

    Box(modifier = clickable.padding(contentPadding)) { content() }
}

/** Cabecera de sección: título y subtítulo con la jerarquía de la app. */
@Composable
fun CraftSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = CraftTextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = CraftTextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * Chip de selección (relación de aspecto, filtros, calidad de export).
 *
 * @param selected resalta con el degradado de acento.
 */
@Composable
fun CraftChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = if (selected) CraftAccentStart else CraftSurfaceHigh
    val textColor = if (selected) Color(0xFF101014) else CraftTextSecondary

    Surface(
        modifier = modifier.height(34.dp),
        shape = RoundedCornerShape(10.dp),
        color = backgroundColor,
        contentColor = textColor,
    ) {
        Box(
            modifier = Modifier
                .clickableCraft(
                    enabled = true,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick,
                )
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

/** Píldora con degradado de acento, para badges ("PRO", "4K"). */
@Composable
fun CraftBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                Brush.linearGradient(listOf(CraftAccentStart, CraftAccentEnd)),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            color = Color(0xFF101014),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}

/** Separador vertical sutil reutilizable. */
@Composable
fun CraftDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(1.dp)
            .fillMaxWidth()
            .background(CraftOutline.copy(alpha = 0.5f)),
    )
}

/**
 * `clickable` sin ripple: en el editor oscuro el ripple de Material
 * destaca demasiado y rompe la estética. La respuesta táctil la da la
 * animación de escala de los botones.
 */
fun Modifier.clickableCraft(
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
): Modifier = this.then(
    Modifier.clickable(
        enabled = enabled,
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick,
    ),
)
