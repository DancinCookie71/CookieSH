package com.cookie.sh.feature.logcat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.remember
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cookie.sh.core.model.LogLevel
import com.cookie.sh.core.model.LogLine
import com.cookie.sh.data.repository.LogcatRepository
import com.cookie.sh.ui.components.ActionMessage
import com.cookie.sh.ui.components.EmptyState
import com.cookie.sh.ui.components.FeatureTopBar
import com.cookie.sh.ui.components.GlassCard
import com.cookie.sh.ui.components.SearchField
import com.cookie.sh.ui.components.SectionLabel
import com.cookie.sh.ui.theme.CookieGreen
import com.cookie.sh.ui.theme.CookieTerminal
import com.cookie.sh.ui.theme.CookieTextSecondary
import com.cookie.sh.ui.theme.CookieYellow
import com.cookie.sh.ui.theme.TerminalTextStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LogcatUiState(
    val lines: List<LogLine> = emptyList(),
    val search: String = "",
    val tagFilter: String = "",
    val packageFilter: String = "",
    val levelFilter: LogLevel? = null,
    val paused: Boolean = false,
    val statusMessage: String? = null,
    val statusSuccess: Boolean = true,
)

@HiltViewModel
class LogcatViewModel @Inject constructor(
    private val logcatRepository: LogcatRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogcatUiState())
    val uiState: StateFlow<LogcatUiState> = _uiState.asStateFlow()

    private var streamJob: Job? = null

    init {
        resume()
    }

    fun resume() {
        if (streamJob?.isActive == true) {
            _uiState.update { it.copy(paused = false) }
            return
        }
        _uiState.update { it.copy(paused = false) }
        streamJob = viewModelScope.launch(Dispatchers.IO) {
            var logBuffer = mutableListOf<LogLine>()
            var lastUpdateTime = System.currentTimeMillis()

            logcatRepository.streamLogs()
                .buffer(capacity = 500)
                .collect { line ->
                    if (!uiState.value.paused) {
                        logBuffer.add(line)
                        val now = System.currentTimeMillis()
                        if (now - lastUpdateTime > 200 || logBuffer.size >= 50) {
                            val newLines = logBuffer.toList()
                            logBuffer.clear()
                            lastUpdateTime = now
                            _uiState.update { state ->
                                val updated = (state.lines + newLines).takeLast(600)
                                state.copy(lines = updated)
                            }
                        }
                    }
                }
        }
    }

    fun pause() {
        _uiState.update { it.copy(paused = true) }
    }

    fun updateSearch(value: String) {
        _uiState.update { it.copy(search = value) }
    }

    fun updateTagFilter(value: String) {
        _uiState.update { it.copy(tagFilter = value) }
    }

    fun updatePackageFilter(value: String) {
        _uiState.update { it.copy(packageFilter = value) }
    }

    fun setLevelFilter(level: LogLevel?) {
        _uiState.update { it.copy(levelFilter = level) }
    }

    fun clearLocalView() {
        _uiState.update { it.copy(lines = emptyList()) }
    }

    fun clearDeviceBuffers() {
        viewModelScope.launch {
            val result = logcatRepository.clearLogs()
            _uiState.update {
                it.copy(
                    lines = emptyList(),
                    statusMessage = result.message,
                    statusSuccess = result.success,
                )
            }
        }
    }

    fun export(lines: List<LogLine>) {
        viewModelScope.launch {
            val result = logcatRepository.exportLogs(lines)
            _uiState.update { it.copy(statusMessage = result.message, statusSuccess = result.success) }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun filteredLines(): List<LogLine> {
        val state = uiState.value
        return state.lines.filter { line ->
            val searchMatch = state.search.isBlank() ||
                line.raw.contains(state.search, ignoreCase = true) ||
                line.message.contains(state.search, ignoreCase = true)
            val tagMatch = state.tagFilter.isBlank() || line.tag.contains(state.tagFilter, ignoreCase = true)
            val packageMatch = state.packageFilter.isBlank() || line.packageName.contains(state.packageFilter, ignoreCase = true)
            val levelMatch = state.levelFilter == null || line.level == state.levelFilter
            searchMatch && tagMatch && packageMatch && levelMatch
        }
    }
}

@Composable
fun LogcatRoute(
    onBack: () -> Unit,
    viewModel: LogcatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LogcatScreen(
        uiState = uiState,
        filteredLines = viewModel.filteredLines(),
        onBack = onBack,
        onSearch = viewModel::updateSearch,
        onTagFilter = viewModel::updateTagFilter,
        onPackageFilter = viewModel::updatePackageFilter,
        onLevelFilter = viewModel::setLevelFilter,
        onPauseResume = {
            if (uiState.paused) viewModel.resume() else viewModel.pause()
        },
        onClearLocal = viewModel::clearLocalView,
        onClearDevice = viewModel::clearDeviceBuffers,
        onExport = viewModel::export,
        onConsumeMessage = viewModel::consumeMessage,
    )
}

@Composable
private fun LogcatScreen(
    uiState: LogcatUiState,
    filteredLines: List<LogLine>,
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    onTagFilter: (String) -> Unit,
    onPackageFilter: (String) -> Unit,
    onLevelFilter: (LogLevel?) -> Unit,
    onPauseResume: () -> Unit,
    onClearLocal: () -> Unit,
    onClearDevice: () -> Unit,
    onExport: (List<LogLine>) -> Unit,
    onConsumeMessage: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(filteredLines.size, uiState.paused) {
        if (!uiState.paused && filteredLines.isNotEmpty()) {
            listState.animateScrollToItem(filteredLines.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            FeatureTopBar(
                title = "Logcat Viewer",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onPauseResume) {
                        Icon(
                            imageVector = if (uiState.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = "Pause or resume",
                        )
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
                    title = "Live Log Feed",
                    subtitle = "Color-coded logcat output with search, tag, package, and log-level filters.",
                )
            }
            item {
                SearchField(
                    value = uiState.search,
                    onValueChange = onSearch,
                    label = "Search logs",
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SearchField(
                        value = uiState.tagFilter,
                        onValueChange = onTagFilter,
                        label = "Tag",
                        modifier = Modifier.weight(1f),
                    )
                    SearchField(
                        value = uiState.packageFilter,
                        onValueChange = onPackageFilter,
                        label = "Package",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(null, LogLevel.Verbose, LogLevel.Debug, LogLevel.Info, LogLevel.Warn, LogLevel.Error).forEach { level ->
                        FilterChip(
                            selected = uiState.levelFilter == level,
                            onClick = { onLevelFilter(level) },
                            label = { Text(level?.shortName ?: "All") },
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(
                        selected = uiState.paused,
                        onClick = onPauseResume,
                        label = { Text(if (uiState.paused) "Resume" else "Pause") },
                    )
                    FilterChip(
                        selected = false,
                        onClick = { onExport(filteredLines) },
                        label = { Text("Export") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Rounded.SaveAlt, contentDescription = null)
                        },
                    )
                    FilterChip(
                        selected = false,
                        onClick = onClearLocal,
                        label = { Text("Clear view") },
                    )
                    FilterChip(
                        selected = false,
                        onClick = onClearDevice,
                        label = { Text("Clear buffer") },
                    )
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
            item {
                val terminalBackground = CookieTerminal
                GlassCard(accent = CookieYellow) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(terminalBackground, shape = MaterialTheme.shapes.large)
                            .padding(14.dp),
                    ) {
                        if (filteredLines.isEmpty()) {
                            Text(
                                text = if (uiState.paused) "Logcat is paused." else "Waiting for log output...",
                                style = TerminalTextStyle,
                                color = CookieTextSecondary,
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 280.dp, max = 520.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(items = filteredLines, key = { it.id }) { line: LogLine ->
                                    val color = logColor(line.level)
                                    Text(
                                        text = line.raw,
                                        style = TerminalTextStyle,
                                        color = color,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun logColor(level: LogLevel): Color {
    return when (level) {
        LogLevel.Info -> CookieGreen
        LogLevel.Warn -> CookieYellow
        LogLevel.Error, LogLevel.Fatal -> MaterialTheme.colorScheme.error
        LogLevel.Debug -> Color.White
        LogLevel.Verbose, LogLevel.Unknown -> CookieTextSecondary
    }
}
