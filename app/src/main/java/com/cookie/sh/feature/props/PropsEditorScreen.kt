package com.cookie.sh.feature.props

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cookie.sh.core.model.BuildProp
import com.cookie.sh.core.model.BuildPropPreset
import com.cookie.sh.data.repository.PropsRepository
import com.cookie.sh.ui.components.ActionMessage
import com.cookie.sh.ui.components.EmptyState
import com.cookie.sh.ui.components.FeatureTopBar
import com.cookie.sh.ui.components.GlassCard
import com.cookie.sh.ui.components.PillButton
import com.cookie.sh.ui.components.SearchField
import com.cookie.sh.ui.components.SectionLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PropsUiState(
    val isLoading: Boolean = true,
    val props: List<BuildProp> = emptyList(),
    val search: String = "",
    val favoritesOnly: Boolean = false,
    val statusMessage: String? = null,
    val statusSuccess: Boolean = true,
)

@HiltViewModel
class PropsViewModel @Inject constructor(
    private val propsRepository: PropsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PropsUiState())
    val uiState: StateFlow<PropsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    props = propsRepository.loadProps(),
                )
            }
        }
    }

    fun updateSearch(query: String) {
        _uiState.update { it.copy(search = query) }
    }

    fun setFavoritesOnly(enabled: Boolean) {
        _uiState.update { it.copy(favoritesOnly = enabled) }
    }

    fun saveProp(name: String, value: String) {
        viewModelScope.launch {
            val result = propsRepository.setProp(name = name, value = value)
            _uiState.update {
                it.copy(statusMessage = result.message, statusSuccess = result.success)
            }
            refresh()
        }
    }

    fun deleteProp(name: String) {
        viewModelScope.launch {
            val result = propsRepository.deleteProp(name)
            _uiState.update {
                it.copy(statusMessage = result.message, statusSuccess = result.success)
            }
            refresh()
        }
    }

    fun toggleFavorite(prop: BuildProp) {
        viewModelScope.launch {
            propsRepository.toggleFavorite(prop.name, !prop.isFavorite)
            refresh()
        }
    }

    fun exportProps() {
        viewModelScope.launch {
            val result = propsRepository.exportProps(filteredProps())
            _uiState.update { it.copy(statusMessage = result.message, statusSuccess = result.success) }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun filteredProps(): List<BuildProp> {
        val state = uiState.value
        return state.props.filter { prop ->
            val searchMatch = state.search.isBlank() || prop.name.contains(state.search, ignoreCase = true) ||
                prop.value.contains(state.search, ignoreCase = true)
            val favoriteMatch = !state.favoritesOnly || prop.isFavorite
            searchMatch && favoriteMatch
        }
    }
}

@Composable
fun PropsEditorRoute(
    onBack: () -> Unit,
    viewModel: PropsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PropsEditorScreen(
        uiState = uiState,
        filteredProps = viewModel.filteredProps(),
        onBack = onBack,
        onSearch = viewModel::updateSearch,
        onFavoritesOnly = viewModel::setFavoritesOnly,
        onRefresh = viewModel::refresh,
        onExport = viewModel::exportProps,
        onToggleFavorite = viewModel::toggleFavorite,
        onSaveProp = viewModel::saveProp,
        onDeleteProp = viewModel::deleteProp,
        onConsumeMessage = viewModel::consumeMessage,
    )
}

@Composable
private fun PropsEditorScreen(
    uiState: PropsUiState,
    filteredProps: List<BuildProp>,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onFavoritesOnly: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onExport: () -> Unit,
    onToggleFavorite: (BuildProp) -> Unit,
    onSaveProp: (String, String) -> Unit,
    onDeleteProp: (String) -> Unit,
    onConsumeMessage: () -> Unit,
) {
    var editingProp by remember { mutableStateOf<BuildProp?>(null) }
    var deletingProp by remember { mutableStateOf<BuildProp?>(null) }
    var addingNew by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            FeatureTopBar(
                title = "Props Editor",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(imageVector = Icons.Rounded.Refresh, contentDescription = "Refresh props")
                    }
                },
            )
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
                    title = "Build Properties",
                    subtitle = "Search and apply temporary runtime tweaks through root-safe background commands.",
                )
            }
            item {
                SearchField(
                    value = uiState.search,
                    onValueChange = onSearch,
                    label = "Search properties or values",
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(
                        selected = uiState.favoritesOnly,
                        onClick = { onFavoritesOnly(!uiState.favoritesOnly) },
                        label = { Text("Favorites only") },
                    )
                    PillButton(text = "Add prop", onClick = { addingNew = true })
                    PillButton(text = "Export", onClick = onExport)
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
            if (filteredProps.isEmpty()) {
                item {
                    EmptyState(
                        title = "No properties matched",
                        subtitle = "Try a broader query or refresh the live property list.",
                    )
                }
            } else {
                items(filteredProps, key = { it.name }) { prop ->
                    GlassCard(accent = if (prop.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary) {
                        Text(text = prop.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = prop.value.ifBlank { "(empty)" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = { onToggleFavorite(prop) }) {
                                Icon(
                                    imageVector = Icons.Rounded.PushPin,
                                    contentDescription = "Favorite prop",
                                    tint = if (prop.isFavorite) MaterialTheme.colorScheme.tertiary else Color.White,
                                )
                            }
                            IconButton(onClick = { editingProp = prop }) {
                                Icon(imageVector = Icons.Rounded.Edit, contentDescription = "Edit prop")
                            }
                            IconButton(onClick = { deletingProp = prop }) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "Delete prop",
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingProp != null) {
        PropEditorDialog(
            title = "Edit property",
            initialName = editingProp!!.name,
            initialValue = editingProp!!.value,
            nameEnabled = false,
            onDismiss = { editingProp = null },
            onConfirm = { name, value ->
                onSaveProp(name, value)
                editingProp = null
            },
        )
    }

    if (addingNew) {
        PropEditorDialog(
            title = "Add property",
            initialName = "",
            initialValue = "",
            nameEnabled = true,
            onDismiss = { addingNew = false },
            onConfirm = { name, value ->
                onSaveProp(name, value)
                addingNew = false
            },
        )
    }

    deletingProp?.let { prop ->
        AlertDialog(
            onDismissRequest = { deletingProp = null },
            title = { Text(text = "Delete ${prop.name}?") },
            text = { Text("CookieSH will try to remove this property with resetprop, then fall back to clearing the value.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteProp(prop.name)
                        deletingProp = null
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingProp = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun PropEditorDialog(
    title: String,
    initialName: String,
    initialValue: String,
    nameEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    var value by rememberSaveable(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    enabled = nameEnabled,
                    label = { Text("Property name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Property value") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim(), value)
                    }
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
