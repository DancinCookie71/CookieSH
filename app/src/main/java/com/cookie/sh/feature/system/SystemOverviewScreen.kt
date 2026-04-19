package com.cookie.sh.feature.system

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cookie.sh.core.model.IntegrityResult
import com.cookie.sh.core.model.SystemStats
import com.cookie.sh.ui.components.FeatureTopBar
import com.cookie.sh.ui.components.GlassCard
import com.cookie.sh.ui.components.PillButton
import com.cookie.sh.ui.theme.CookieGreen
import com.cookie.sh.ui.theme.CookieYellow
import java.util.Locale

@Composable
fun SystemOverviewRoute(
    onBack: () -> Unit,
    viewModel: SystemOverviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    SystemOverviewScreen(
        uiState = uiState,
        onBack = onBack,
        onFixIntegrity = viewModel::fixIntegrityProps,
        onToggleSelinux = viewModel::toggleSelinux,
        onSetDpi = viewModel::setDisplayDpi,
        onResetDpi = viewModel::resetDisplayDpi
    )
}

@Composable
fun SystemOverviewScreen(
    uiState: SystemOverviewUiState,
    onBack: () -> Unit,
    onFixIntegrity: () -> Unit,
    onToggleSelinux: (Boolean) -> Unit,
    onSetDpi: (Int) -> Unit,
    onResetDpi: () -> Unit,
) {
    val stats = uiState.stats
    val integrity = uiState.integrity
    Scaffold(
        topBar = {
            FeatureTopBar(title = "System Overview", onBack = onBack)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = "CPU Usage",
                        value = "${stats.cpuUsage}%",
                        progress = stats.cpuUsage / 100f,
                        icon = Icons.Rounded.Memory,
                        accent = MaterialTheme.colorScheme.primary,
                        subtext = "${stats.cpuTemp}°C"
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = "RAM Usage",
                        value = formatSize(stats.ramUsedBytes),
                        progress = stats.ramPercent,
                        icon = Icons.Rounded.Speed,
                        accent = MaterialTheme.colorScheme.secondary,
                        subtext = "Total: ${formatSize(stats.ramTotalBytes)}"
                    )
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = "Disk Usage",
                        value = formatSize(stats.diskUsedBytes),
                        progress = stats.diskPercent,
                        icon = Icons.Rounded.Storage,
                        accent = MaterialTheme.colorScheme.tertiary,
                        subtext = "Total: ${formatSize(stats.diskTotalBytes)}"
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        title = "Swap",
                        value = formatSize(stats.swapUsedBytes),
                        progress = stats.swapPercent,
                        icon = Icons.Rounded.SyncAlt,
                        accent = Color(0xFFFFA000),
                        subtext = "Total: ${formatSize(stats.swapTotalBytes)}"
                    )
                }
            }

            item {
                GlassCard(accent = if (integrity.deviceIntegrity) CookieGreen else CookieYellow) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.VerifiedUser, 
                                contentDescription = null, 
                                tint = if (integrity.deviceIntegrity) CookieGreen else CookieYellow
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Play Integrity & Spoofing", style = MaterialTheme.typography.titleMedium)
                        }

                        IntegrityRow("Device Integrity", integrity.deviceIntegrity)
                        IntegrityRow("Basic Integrity", integrity.basicIntegrity)
                        IntegrityRow("Strong Integrity", integrity.strongIntegrity)
                        IntegrityRow("Spoofing Active (resetprop)", integrity.spoofingActive)
                        
                        if (!integrity.deviceIntegrity || !integrity.spoofingActive) {
                            Spacer(modifier = Modifier.height(8.dp))
                            PillButton(
                                text = "Fix Integrity Props",
                                onClick = onFixIntegrity,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                        
                        Text(
                            text = "Fingerprint: ${integrity.fingerprint}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                GlassCard(accent = if (stats.selinuxEnforcing) CookieGreen else CookieYellow) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Security,
                                contentDescription = null,
                                tint = if (stats.selinuxEnforcing) CookieGreen else CookieYellow
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SELinux Status", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.weight(1f))
                            Switch(
                                checked = stats.selinuxEnforcing,
                                onCheckedChange = onToggleSelinux,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = CookieGreen,
                                    uncheckedThumbColor = CookieYellow
                                )
                            )
                        }
                        Text(
                            text = if (stats.selinuxEnforcing) "Enforcing: System is secure. Apps are restricted by policy." 
                                   else "Permissive: Policy violations are logged but not blocked. Useful for debugging GSI hardware issues.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                GlassCard(accent = MaterialTheme.colorScheme.primary) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Monitor, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Display Tweaks", style = MaterialTheme.typography.titleMedium)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Density (DPI)", style = MaterialTheme.typography.titleSmall)
                                Text("Current: ${uiState.displayDpi}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PillButton(text = "-40", onClick = { onSetDpi(uiState.displayDpi - 40) })
                                PillButton(text = "+40", onClick = { onSetDpi(uiState.displayDpi + 40) })
                                PillButton(text = "Reset", onClick = onResetDpi, outlined = true)
                            }
                        }
                    }
                }
            }

            item {
                GlassCard(accent = Color(0xFF00E676)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null, tint = Color(0xFF00E676))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Battery Health", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(stats.batteryStatus, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            BatteryDetail(label = "Level", value = "${stats.batteryLevel}%")
                            BatteryDetail(label = "Temp", value = "${stats.batteryTemp}°C")
                            BatteryDetail(label = "Current", value = "${stats.batteryCurrentMa}mA")
                        }
                        
                        LinearProgressIndicator(
                            progress = stats.batteryLevel / 100f,
                            modifier = Modifier.fillMaxWidth().height(8.dp),
                            color = Color(0xFF00E676),
                            trackColor = Color(0xFF00E676).copy(alpha = 0.2f)
                        )
                    }
                }
            }

            item {
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("System Uptime", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stats.uptime, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    progress: Float,
    icon: ImageVector,
    accent: Color,
    subtext: String
) {
    GlassCard(modifier = modifier, accent = accent) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            
            LinearProgressIndicator(
                progress = progress.coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = accent,
                trackColor = accent.copy(alpha = 0.2f)
            )
            
            Text(subtext, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun IntegrityRow(label: String, passed: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            if (passed) "PASSED" else "FAILED",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (passed) CookieGreen else CookieYellow
        )
    }
}

@Composable
fun BatteryDetail(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

fun formatSize(bytes: Long): String {
    val kb = bytes / 1024
    val mb = kb / 1024
    val gb = mb.toFloat() / 1024
    return when {
        gb >= 1 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1 -> "$mb MB"
        else -> "$kb KB"
    }
}
