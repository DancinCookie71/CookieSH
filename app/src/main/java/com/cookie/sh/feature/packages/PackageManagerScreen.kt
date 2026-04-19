package com.cookie.sh.feature.packages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cookie.sh.core.model.ActionResult
import com.cookie.sh.core.model.AppPackage
import com.cookie.sh.core.model.PackageFilter
import com.cookie.sh.data.repository.PackageRepository
import com.cookie.sh.ui.components.ActionMessage
import com.cookie.sh.ui.components.EmptyState
import com.cookie.sh.ui.components.FeatureTopBar
import com.cookie.sh.ui.components.GlassCard
import com.cookie.sh.ui.components.KeyValueRow
import com.cookie.sh.ui.components.SearchField
import com.cookie.sh.ui.components.SectionLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DateFormat
import javax.inject.Inject

data class PackageManagerUiState(
    val isLoading: Boolean = true,
    val packages: List<AppPackage> = emptyList(),
    val search: String = "",
    val filter: PackageFilter = PackageFilter.All,
    val selectedPackage: AppPackage? = null,
    val statusMessage: String? = null,
    val statusSuccess: Boolean = true,
)

@HiltViewModel
class PackageManagerViewModel @Inject constructor(
    private val packageRepository: PackageRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PackageManagerUiState())
    val uiState: StateFlow<PackageManagerUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val list = packageRepository.getPackages(uiState.value.filter)
            _uiState.update { it.copy(packages = list, isLoading = false) }
        }
    }

    fun updateSearch(value: String) {
        _uiState.update { it.copy(search = value) }
    }

    fun setFilter(filter: PackageFilter) {
        _uiState.update { it.copy(filter = filter) }
        refresh()
    }

    fun selectPackage(item: AppPackage?) {
        _uiState.update { it.copy(selectedPackage = item) }
    }

    fun disable(packageName: String) = runPackageAction { packageRepository.disable(packageName) }

    fun enable(packageName: String) = runPackageAction { packageRepository.enable(packageName) }

    fun uninstallUser0(packageName: String) = runPackageAction { packageRepository.uninstallUser0(packageName) }

    fun debloatGsi() = runPackageAction { packageRepository.debloatGsi() }

    fun forceStop(packageName: String) = runPackageAction {
        // Implementation of force stop if needed, or leave for later.
        ActionResult(true, "Force stop not implemented")
    }

    fun consumeMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun filteredPackages(): List<AppPackage> {
        val search = uiState.value.search
        return uiState.value.packages.filter { item ->
            search.isBlank() ||
                item.label.contains(search, ignoreCase = true) ||
                item.packageName.contains(search, ignoreCase = true)
        }
    }

    private fun runPackageAction(action: suspend () -> ActionResult) {
        viewModelScope.launch {
            val result = action()
            _uiState.update {
                it.copy(
                    statusMessage = result.message,
                    statusSuccess = result.success,
                    selectedPackage = null,
                )
            }
            refresh()
        }
    }
}

@Composable
fun PackageManagerRoute(
    onBack: () -> Unit,
    viewModel: PackageManagerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PackageManagerScreen(
        uiState = uiState,
        filteredPackages = viewModel.filteredPackages(),
        onBack = onBack,
        onSearch = viewModel::updateSearch,
        onFilter = viewModel::setFilter,
        onSelectPackage = viewModel::selectPackage,
        onDisable = viewModel::disable,
        onEnable = viewModel::enable,
        onUninstallUser0 = viewModel::uninstallUser0,
        onForceStop = viewModel::forceStop,
        onDebloatGsi = viewModel::debloatGsi,
        onConsumeMessage = viewModel::consumeMessage,
    )
}

@Composable
private fun PackageManagerScreen(
    uiState: PackageManagerUiState,
    filteredPackages: List<AppPackage>,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onFilter: (PackageFilter) -> Unit,
    onSelectPackage: (AppPackage?) -> Unit,
    onDisable: (String) -> Unit,
    onEnable: (String) -> Unit,
    onUninstallUser0: (String) -> Unit,
    onForceStop: (String) -> Unit,
    onDebloatGsi: () -> Unit,
    onConsumeMessage: () -> Unit,
) {
    Scaffold(
        topBar = {
            FeatureTopBar(title = "Package Manager", onBack = onBack)
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
                    title = "Installed Packages",
                    subtitle = "Inspect package metadata and use root package-management actions safely in the background.",
                )
            }
            item {
                SearchField(
                    value = uiState.search,
                    onValueChange = onSearch,
                    label = "Search packages",
                )
            }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PackageFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = uiState.filter == filter,
                            onClick = { onFilter(filter) },
                            label = { Text(filter.name) },
                        )
                    }
                }
            }
            item {
                TextButton(onClick = onDebloatGsi) {
                    Text("GSI Debloat (Disable AOSP apps)")
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
            if (filteredPackages.isEmpty()) {
                item {
                    EmptyState(
                        title = "No packages matched",
                        subtitle = "Adjust the filter or search query to broaden the app list.",
                    )
                }
            } else {
                items(filteredPackages, key = { it.packageName }) { item ->
                    GlassCard(onClick = { onSelectPackage(item) }) {
                        Text(text = item.label, style = MaterialTheme.typography.titleMedium)
                        Text(text = item.packageName, style = MaterialTheme.typography.bodySmall)
                        Text(
                            text = "${if (item.isSystemApp) "System" else "User"} app • ${if (item.isEnabled) "Enabled" else "Disabled"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    uiState.selectedPackage?.let { item ->
        AlertDialog(
            onDismissRequest = { onSelectPackage(null) },
            title = { Text(item.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyValueRow(label = "Package", value = item.packageName)
                    KeyValueRow(label = "Version", value = item.versionName)
                    KeyValueRow(
                        label = "Installed",
                        value = DateFormat.getDateTimeInstance().format(item.installDate),
                    )
                    KeyValueRow(
                        label = "Permissions",
                        value = if (item.permissions.isEmpty()) "None requested" else item.permissions.joinToString(),
                    )
                }
            },
            confirmButton = {
                Column {
                    TextButton(onClick = { if (item.isEnabled) onDisable(item.packageName) else onEnable(item.packageName) }) {
                        Text(if (item.isEnabled) "Disable" else "Enable")
                    }
                    TextButton(onClick = { onForceStop(item.packageName) }) {
                        Text("Force stop")
                    }
                    TextButton(onClick = { onUninstallUser0(item.packageName) }) {
                        Text("Uninstall user 0")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { onSelectPackage(null) }) {
                    Text("Close")
                }
            },
        )
    }
}
