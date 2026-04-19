package com.cookie.sh.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import com.cookie.sh.ui.theme.CookieGreen
import com.cookie.sh.ui.theme.CookieYellow
import com.cookie.sh.ui.components.PillButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cookie.sh.core.model.DeviceInfo
import com.cookie.sh.core.model.IntegrityResult
import com.cookie.sh.core.model.RootStatus
import com.cookie.sh.data.repository.DeviceRepository
import com.cookie.sh.data.repository.PowerRepository
import com.cookie.sh.data.repository.SystemRepository
import com.cookie.sh.navigation.Destination
import com.cookie.sh.navigation.toolDestinations
import com.cookie.sh.ui.components.DashboardHeader
import com.cookie.sh.ui.components.FeatureTopBar
import com.cookie.sh.ui.components.GlassCard
import com.cookie.sh.ui.components.SectionLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val isLoading: Boolean = true,
    val deviceInfo: DeviceInfo = DeviceInfo(),
    val integrityResult: IntegrityResult = IntegrityResult(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val systemRepository: SystemRepository,
    private val powerRepository: PowerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val deviceInfo = deviceRepository.getDeviceInfo()
            val integrity = systemRepository.checkIntegrity()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    deviceInfo = deviceInfo,
                    integrityResult = integrity,
                )
            }
        }
    }

    fun reboot(mode: String?) {
        viewModelScope.launch {
            if (mode == "soft") {
                powerRepository.softReboot()
            } else {
                powerRepository.reboot(mode.orEmpty())
            }
        }
    }
}

@Composable
fun DashboardRoute(
    onNavigate: (Destination) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreen(
        uiState = uiState,
        onNavigate = onNavigate,
        onReboot = viewModel::reboot
    )
}

@Composable
private fun DashboardScreen(
    uiState: DashboardUiState,
    onNavigate: (Destination) -> Unit,
    onReboot: (String?) -> Unit,
) {
    val toolRows = toolDestinations.chunked(2)
    var showRebootDialog by remember { mutableStateOf(false) }

    if (showRebootDialog) {
        RebootDialog(
            onDismiss = { showRebootDialog = false },
            onReboot = { mode ->
                onReboot(mode)
                showRebootDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            FeatureTopBar(
                title = "CookieSH",
                actions = {
                    IconButton(onClick = { showRebootDialog = true }) {
                        Icon(Icons.Rounded.PowerSettingsNew, contentDescription = "Reboot options")
                    }
                    IconButton(onClick = { onNavigate(Destination.Settings) }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                DashboardHeader(deviceInfo = uiState.deviceInfo)
            }
            val isPhh = uiState.deviceInfo.rootProvider.contains("phh", ignoreCase = true)
            if (uiState.deviceInfo.rootStatus != RootStatus.Rooted || 
                uiState.deviceInfo.rootProvider.contains("Unknown", ignoreCase = true) || 
                uiState.deviceInfo.hasInitBoot || isPhh || !uiState.integrityResult.deviceIntegrity) {
                item {
                    RootDoctorCard(
                        deviceInfo = uiState.deviceInfo,
                        integrityResult = uiState.integrityResult,
                        onNavigate = onNavigate
                    )
                }
            }
            item {
                SectionLabel(
                    title = "Toolkit Dashboard",
                    subtitle = "Jump straight into props, logs, partitions, shell sessions, and boot diagnostics.",
                )
            }
            items(toolRows) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { destination ->
                        DashboardToolCard(
                            destination = destination,
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate(destination) },
                        )
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun RootDoctorCard(
    deviceInfo: DeviceInfo,
    integrityResult: IntegrityResult,
    onNavigate: (Destination) -> Unit,
) {
    var showDiagDialog by remember { mutableStateOf(false) }

    if (showDiagDialog) {
        AlertDialog(
            onDismissRequest = { showDiagDialog = false },
            title = { Text("Root Diagnostics") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val statusText = if (deviceInfo.rootStatus == RootStatus.Rooted) "Detected" else "Missing/Incomplete"
                    DiagnosticItem("Root Status", statusText, deviceInfo.rootStatus == RootStatus.Rooted)
                    DiagnosticItem("Provider", deviceInfo.rootProvider, deviceInfo.rootProvider.contains("Magisk"))
                    DiagnosticItem("Init Boot Partition", if (deviceInfo.hasInitBoot) "YES" else "NO", !deviceInfo.hasInitBoot)
                    DiagnosticItem("Device Integrity", if (integrityResult.deviceIntegrity) "PASS" else "FAIL", integrityResult.deviceIntegrity)
                    DiagnosticItem("Strong Integrity", if (integrityResult.strongIntegrity) "PASS" else "FAIL", integrityResult.strongIntegrity)
                    
                    if (integrityResult.spoofingActive) {
                        DiagnosticItem("Prop Spoofing", "ACTIVE", true)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "SPOOFING DETECTED: A Magisk module (like Play Integrity Fix) is likely spoofing your bootloader state to 'Locked'. This helps pass DEVICE_INTEGRITY but won't satisfy STRONG_INTEGRITY.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CookieGreen.copy(alpha = 0.9f)
                        )
                    }
                    
                    if (!integrityResult.strongIntegrity) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "STRONG_INTEGRITY FAILURE: This is expected on GSIs and devices with unlocked bootloaders. Hardware-backed attestation cannot be fully spoofed without specialized TEE exploits.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!deviceInfo.hasInitBoot) {
                        TextButton(
                            onClick = { 
                                showDiagDialog = false
                                onNavigate(Destination.Partitions) 
                            },
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Text("Manually Verify Partitions")
                        }
                    }
                    
                    if (deviceInfo.rootProvider.contains("phh", ignoreCase = true)) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "GSI CONFLICT: Built-in Root (phh-superuser) detected.",
                            style = MaterialTheme.typography.titleSmall,
                            color = CookieYellow
                        )
                        Text(
                            "Magisk often fails to start if phh-superuser is active. You may need to 'Securize' the GSI or disable root in Phh-Treble Settings.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (deviceInfo.hasInitBoot) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "WARNING: This device uses an init_boot partition. Magisk 'Installed: N/A' happens because you patched boot.img instead of init_boot.img.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CookieYellow
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDiagDialog = false }) { Text("Close") }
            }
        )
    }

    GlassCard(
        accent = if ((deviceInfo.hasInitBoot && deviceInfo.rootStatus != RootStatus.Rooted) || !integrityResult.deviceIntegrity) CookieYellow else CookieGreen,
        onClick = { showDiagDialog = true }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.HealthAndSafety,
                contentDescription = null,
                tint = if ((deviceInfo.hasInitBoot && deviceInfo.rootStatus != RootStatus.Rooted) || !integrityResult.deviceIntegrity) CookieYellow else CookieGreen,
                modifier = Modifier.size(32.dp)
            )
            Column {
                Text(text = "Root Doctor", style = MaterialTheme.typography.titleMedium)
                val msg = when {
                    deviceInfo.rootStatus == RootStatus.Rooted && integrityResult.deviceIntegrity -> "System healthy. Magisk active."
                    !integrityResult.deviceIntegrity -> "Integrity issues detected."
                    deviceInfo.hasInitBoot -> "init_boot partition detected."
                    else -> "Diagnostics and health check."
                }
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DiagnosticItem(label: String, value: String, isOk: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value, 
            style = MaterialTheme.typography.bodyMedium, 
            color = if (isOk) CookieGreen else CookieYellow
        )
    }
}

@Composable
private fun RebootDialog(
    onDismiss: () -> Unit,
    onReboot: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Advanced Reboot") },
        text = { Text("Choose a reboot mode. Ensure your work is saved.") },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        dismissButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(text = "System", onClick = { onReboot(null) }, modifier = Modifier.fillMaxWidth())
                PillButton(text = "Soft Reboot", onClick = { onReboot("soft") }, modifier = Modifier.fillMaxWidth())
                PillButton(text = "Recovery", onClick = { onReboot("recovery") }, modifier = Modifier.fillMaxWidth())
                PillButton(text = "Bootloader", onClick = { onReboot("bootloader") }, modifier = Modifier.fillMaxWidth())
                PillButton(text = "Fastbootd", onClick = { onReboot("fastboot") }, modifier = Modifier.fillMaxWidth())
            }
        }
    )
}

@Composable
private fun DashboardToolCard(
    destination: Destination,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    GlassCard(
        modifier = modifier,
        accent = destination.accent,
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(destination.accent.copy(alpha = 0.16f), shape = MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = destination.icon,
                    contentDescription = destination.title,
                    tint = Color.White,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(text = destination.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = destination.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
