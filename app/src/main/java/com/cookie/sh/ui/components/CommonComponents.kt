package com.cookie.sh.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cookie.sh.R
import com.cookie.sh.core.model.DeviceInfo
import com.cookie.sh.core.model.RootStatus
import com.cookie.sh.ui.theme.CookieGreen
import com.cookie.sh.ui.theme.CookiePrimary
import com.cookie.sh.ui.theme.CookieSecondary
import com.cookie.sh.ui.theme.CookieSurfaceElevated
import com.cookie.sh.ui.theme.CookieTextSecondary

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .shadow(elevation = 16.dp, shape = RoundedCornerShape(28.dp), ambientColor = accent.copy(alpha = 0.28f))
            .clip(RoundedCornerShape(28.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(28.dp),
        color = CookieSurfaceElevated.copy(alpha = 0.72f),
        border = BorderStroke(width = 1.dp, color = Color.White.copy(alpha = 0.10f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

@Composable
fun DashboardHeader(deviceInfo: DeviceInfo, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "header")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "gradient",
    )
    val brush = Brush.linearGradient(
        colors = listOf(
            CookiePrimary.copy(alpha = 0.95f),
            CookieSecondary.copy(alpha = 0.9f),
            CookiePrimary.copy(alpha = 0.7f),
        ),
        start = Offset.Zero,
        end = Offset(900f * offset.coerceAtLeast(0.25f), 360f),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(34.dp))
            .background(brush)
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.12f), shape = RoundedCornerShape(34.dp))
            .padding(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = R.mipmap.ic_launcher,
                        contentDescription = "CookieSH launcher",
                        modifier = Modifier.size(40.dp),
                    )
                }
                Column {
                    Text(text = "CookieSH", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = "Root-first toolkit for GSI tinkerers",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.88f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusChip(
                    label = when (deviceInfo.rootStatus) {
                        RootStatus.Rooted -> "Rooted"
                        RootStatus.Unavailable -> "Unavailable"
                        RootStatus.Unknown -> "Unknown"
                    },
                    accent = when (deviceInfo.rootStatus) {
                        RootStatus.Rooted -> CookieGreen
                        RootStatus.Unavailable -> MaterialTheme.colorScheme.error
                        RootStatus.Unknown -> Color.White.copy(alpha = 0.7f)
                    },
                )
                val bootloaderLabel = if (deviceInfo.bootloaderState.contains("locked", ignoreCase = true) && !deviceInfo.bootloaderState.contains("unlocked", ignoreCase = true)) {
                    "LOCKED (Spoofed?)"
                } else {
                    deviceInfo.bootloaderState
                }
                StatusChip(label = bootloaderLabel, accent = Color.White.copy(alpha = 0.82f))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${deviceInfo.name} • Android ${deviceInfo.androidVersion}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Provider: ${deviceInfo.rootProvider}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
fun StatusChip(label: String, accent: Color, modifier: Modifier = Modifier) {
    AssistChip(
        onClick = {},
        label = { Text(text = label) },
        modifier = modifier,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = accent.copy(alpha = 0.14f),
            labelColor = Color.White,
            leadingIconContentColor = accent,
        ),
        border = BorderStroke(width = 1.dp, color = accent.copy(alpha = 0.4f)),
    )
}

@Composable
fun KeyValueRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = CookieTextSecondary,
            modifier = Modifier.weight(0.38f),
        )
        Text(
            text = value.ifBlank { "Unavailable" },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.62f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    CenterAlignedTopAppBar(
        title = { Text(text = title) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Rounded.KeyboardArrowLeft, contentDescription = "Back")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = Color.Transparent,
        ),
    )
}

@Composable
fun SectionLabel(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) {
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = CookieTextSecondary)
        }
    }
}

@Composable
fun ActionMessage(message: String, success: Boolean) {
    GlassCard(accent = if (success) CookieGreen else MaterialTheme.colorScheme.error) {
        Text(text = message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(22.dp),
    )
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false
) {
    if (outlined) {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(999.dp),
        ) {
            Text(text = text)
        }
    } else {
        Button(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(999.dp),
        ) {
            Text(text = text)
        }
    }
}

@Composable
fun EmptyState(title: String, subtitle: String) {
    GlassCard {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = CookieTextSecondary)
    }
}
