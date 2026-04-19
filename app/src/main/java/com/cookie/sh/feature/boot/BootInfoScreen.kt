package com.cookie.sh.feature.boot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cookie.sh.core.model.BootInfo
import com.cookie.sh.data.repository.BootRepository
import com.cookie.sh.ui.components.FeatureTopBar
import com.cookie.sh.ui.components.GlassCard
import com.cookie.sh.ui.components.KeyValueRow
import com.cookie.sh.ui.components.SectionLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BootInfoUiState(
    val bootInfo: BootInfo = BootInfo(),
)

@HiltViewModel
class BootInfoViewModel @Inject constructor(
    private val bootRepository: BootRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BootInfoUiState())
    val uiState: StateFlow<BootInfoUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(bootInfo = bootRepository.getBootInfo()) }
        }
    }
}

@Composable
fun BootInfoRoute(
    onBack: () -> Unit,
    viewModel: BootInfoViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BootInfoScreen(uiState = uiState, onBack = onBack)
}

@Composable
private fun BootInfoScreen(
    uiState: BootInfoUiState,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            FeatureTopBar(title = "Boot Info", onBack = onBack)
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
                    title = "Parsed Boot State",
                    subtitle = "Current slot, bootloader state, AVB, verified boot, kernel, and raw boot sources.",
                )
            }
            item {
                GlassCard {
                    KeyValueRow(label = "Current slot", value = uiState.bootInfo.slotSuffix)
                    KeyValueRow(label = "Bootloader", value = uiState.bootInfo.bootloaderState)
                    KeyValueRow(label = "Verified boot", value = uiState.bootInfo.verifiedBootState)
                    KeyValueRow(label = "AVB version", value = uiState.bootInfo.avbVersion)
                    KeyValueRow(label = "Kernel", value = uiState.bootInfo.kernelVersion)
                }
            }
            item {
                GlassCard(accent = androidx.compose.material3.MaterialTheme.colorScheme.secondary) {
                    Text(text = "cmdline", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Text(text = uiState.bootInfo.cmdline.ifBlank { "Unavailable" }, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            }
            item {
                GlassCard(accent = androidx.compose.material3.MaterialTheme.colorScheme.primary) {
                    Text(text = "bootconfig", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                    Text(text = uiState.bootInfo.bootConfig.ifBlank { "Unavailable" }, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
