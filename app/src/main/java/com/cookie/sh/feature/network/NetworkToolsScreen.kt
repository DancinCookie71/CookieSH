package com.cookie.sh.feature.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cookie.sh.core.model.NetworkToolsState
import com.cookie.sh.data.repository.NetworkToolsRepository
import com.cookie.sh.ui.components.ActionMessage
import com.cookie.sh.ui.components.FeatureTopBar
import com.cookie.sh.ui.components.GlassCard
import com.cookie.sh.ui.components.KeyValueRow
import com.cookie.sh.ui.components.SearchField
import com.cookie.sh.ui.components.SectionLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NetworkToolsUiState(
    val status: NetworkToolsState = NetworkToolsState(),
    val portInput: String = "5555",
    val statusMessage: String? = null,
    val statusSuccess: Boolean = true,
)

@HiltViewModel
class NetworkToolsViewModel @Inject constructor(
    private val networkToolsRepository: NetworkToolsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NetworkToolsUiState())
    val uiState: StateFlow<NetworkToolsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val status = networkToolsRepository.getStatus()
            _uiState.update {
                it.copy(
                    status = status,
                    portInput = status.adbPort,
                )
            }
        }
    }

    fun updatePort(value: String) {
        _uiState.update { it.copy(portInput = value) }
    }

    fun toggleAdbWifi(enabled: Boolean) {
        viewModelScope.launch {
            val result = networkToolsRepository.setAdbWifi(enabled, uiState.value.portInput.toIntOrNull() ?: 5555)
            _uiState.update { it.copy(statusMessage = result.message, statusSuccess = result.success) }
            refresh()
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}

@Composable
fun NetworkToolsRoute(
    onBack: () -> Unit,
    viewModel: NetworkToolsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    NetworkToolsScreen(
        uiState = uiState,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onPortChanged = viewModel::updatePort,
        onToggleAdbWifi = viewModel::toggleAdbWifi,
        onConsumeMessage = viewModel::consumeMessage,
    )
}

@Composable
private fun NetworkToolsScreen(
    uiState: NetworkToolsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onPortChanged: (String) -> Unit,
    onToggleAdbWifi: (Boolean) -> Unit,
    onConsumeMessage: () -> Unit,
) {
    Scaffold(
        topBar = {
            FeatureTopBar(title = "ADB & Network Tools", onBack = onBack)
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SectionLabel(
                    title = "Wireless Debugging",
                    subtitle = "Use root to toggle `service.adb.tcp.port`, restart adbd, and keep the current IP handy.",
                )
            }
            item {
                GlassCard(accent = MaterialTheme.colorScheme.secondary) {
                    KeyValueRow(label = "ADB over Wi-Fi", value = if (uiState.status.adbWifiEnabled) "Enabled" else "Disabled")
                    KeyValueRow(label = "Port", value = uiState.status.adbPort)
                    KeyValueRow(label = "IP Address", value = uiState.status.ipAddress)
                    SearchField(
                        value = uiState.portInput,
                        onValueChange = onPortChanged,
                        label = "ADB TCP port",
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = uiState.status.adbWifiEnabled,
                            onClick = { onToggleAdbWifi(!uiState.status.adbWifiEnabled) },
                            label = { Text(if (uiState.status.adbWifiEnabled) "Disable ADB Wi-Fi" else "Enable ADB Wi-Fi") },
                        )
                        FilterChip(
                            selected = false,
                            onClick = onRefresh,
                            label = { Text("Refresh") },
                        )
                    }
                }
            }
            uiState.statusMessage?.let { message ->
                item {
                    ActionMessage(message = message, success = uiState.statusSuccess)
                    LaunchedEffect(message) {
                        onConsumeMessage()
                    }
                }
            }
        }
    }
}
