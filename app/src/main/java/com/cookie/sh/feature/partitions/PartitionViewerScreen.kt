package com.cookie.sh.feature.partitions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.cookie.sh.core.model.PartitionInfo
import com.cookie.sh.data.repository.BootRepository
import com.cookie.sh.data.repository.PartitionRepository
import com.cookie.sh.ui.components.ActionMessage
import com.cookie.sh.ui.components.EmptyState
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

data class PartitionViewerUiState(
    val activeSlot: String = "",
    val partitions: List<PartitionInfo> = emptyList(),
    val statusMessage: String? = null,
    val statusSuccess: Boolean = true,
)

@HiltViewModel
class PartitionViewerViewModel @Inject constructor(
    private val bootRepository: BootRepository,
    private val partitionRepository: PartitionRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PartitionViewerUiState())
    val uiState: StateFlow<PartitionViewerUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val bootInfo = bootRepository.getBootInfo()
            val partitions = partitionRepository.getPartitions(bootInfo.slotSuffix.takeIf { it != "Unknown" } ?: "")
            _uiState.update {
                it.copy(
                    activeSlot = bootInfo.slotSuffix,
                    partitions = partitions,
                )
            }
        }
    }

    fun dumpPartition(partition: PartitionInfo) {
        viewModelScope.launch {
            val result = partitionRepository.dumpPartition(partition)
            _uiState.update { it.copy(statusMessage = result.message, statusSuccess = result.success) }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}

@Composable
fun PartitionViewerRoute(
    onBack: () -> Unit,
    viewModel: PartitionViewerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PartitionViewerScreen(
        uiState = uiState,
        onBack = onBack,
        onDump = viewModel::dumpPartition,
        onRefresh = viewModel::refresh,
        onConsumeMessage = viewModel::consumeMessage,
    )
}

@Composable
private fun PartitionViewerScreen(
    uiState: PartitionViewerUiState,
    onBack: () -> Unit,
    onDump: (PartitionInfo) -> Unit,
    onRefresh: () -> Unit,
    onConsumeMessage: () -> Unit,
) {
    Scaffold(
        topBar = {
            FeatureTopBar(title = "Partition Viewer", onBack = onBack)
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
                    title = "Block Partitions",
                    subtitle = "CookieSH reads `/dev/block/by-name` and highlights the active slot suffix when it is present.",
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(
                        selected = false,
                        onClick = onRefresh,
                        label = { Text("Refresh") },
                    )
                    if (uiState.activeSlot.isNotBlank()) {
                        FilterChip(
                            selected = true,
                            onClick = {},
                            label = { Text("Active ${uiState.activeSlot}") },
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
            if (uiState.partitions.isEmpty()) {
                item {
                    EmptyState(
                        title = "No partitions found",
                        subtitle = "The rooted shell was unable to enumerate `/dev/block/by-name`.",
                    )
                }
            } else {
                items(uiState.partitions, key = { it.name }) { partition ->
                    GlassCard(accent = if (partition.isActive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary) {
                        Text(text = partition.name, style = MaterialTheme.typography.titleMedium)
                        KeyValueRow(label = "Target", value = partition.symlinkTarget)
                        KeyValueRow(label = "Size", value = readableBytes(partition.sizeBytes))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (partition.isActive) {
                                FilterChip(selected = true, onClick = {}, label = { Text("Active slot") })
                            }
                            FilterChip(
                                selected = false,
                                onClick = { onDump(partition) },
                                label = { Text("Dump to file") },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun readableBytes(value: Long): String {
    if (value <= 0) return "Unknown"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var current = value.toDouble()
    var index = 0
    while (current >= 1024 && index < units.lastIndex) {
        current /= 1024.0
        index += 1
    }
    return String.format("%.2f %s", current, units[index])
}
