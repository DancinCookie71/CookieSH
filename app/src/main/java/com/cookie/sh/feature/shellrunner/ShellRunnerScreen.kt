package com.cookie.sh.feature.shellrunner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cookie.sh.core.model.ShellHistoryItem
import com.cookie.sh.core.model.TerminalLine
import com.cookie.sh.core.shell.ShellEvent
import com.cookie.sh.data.repository.ShellRepository
import com.cookie.sh.ui.components.ActionMessage
import com.cookie.sh.ui.components.EmptyState
import com.cookie.sh.ui.components.FeatureTopBar
import com.cookie.sh.ui.components.GlassCard
import com.cookie.sh.ui.components.SectionLabel
import com.cookie.sh.ui.theme.CookieGreen
import com.cookie.sh.ui.theme.CookieTerminal
import com.cookie.sh.ui.theme.CookieTextSecondary
import com.cookie.sh.ui.theme.TerminalTextStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShellRunnerUiState(
    val command: String = "",
    val useRoot: Boolean = true,
    val isRunning: Boolean = false,
    val output: List<TerminalLine> = emptyList(),
    val history: List<ShellHistoryItem> = emptyList(),
    val statusMessage: String? = null,
    val statusSuccess: Boolean = true,
)

@HiltViewModel
class ShellRunnerViewModel @Inject constructor(
    private val shellRepository: ShellRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShellRunnerUiState())
    val uiState: StateFlow<ShellRunnerUiState> = _uiState.asStateFlow()

    private var activeJob: Job? = null

    init {
        viewModelScope.launch {
            shellRepository.observeHistory().collect { history ->
                _uiState.update { it.copy(history = history) }
            }
        }
    }

    fun updateCommand(command: String) {
        _uiState.update { it.copy(command = command) }
    }

    fun setUseRoot(useRoot: Boolean) {
        _uiState.update { it.copy(useRoot = useRoot) }
    }

    fun runCommand() {
        val command = uiState.value.command.trim()
        if (command.isBlank()) return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            val buffer = StringBuilder()
            _uiState.update {
                it.copy(
                    isRunning = true,
                    output = listOf(TerminalLine(text = "$ ${command}", isError = false)),
                    statusMessage = null,
                )
            }
            shellRepository.runCommand(command = command, useRoot = uiState.value.useRoot).collect { event ->
                when (event) {
                    is ShellEvent.Output -> {
                        buffer.appendLine(event.line)
                        _uiState.update { state ->
                            state.copy(output = state.output + TerminalLine(text = event.line, isError = event.isError))
                        }
                    }

                    is ShellEvent.Completed -> {
                        val success = event.exitCode == 0
                        shellRepository.saveCommandResult(
                            command = command,
                            useRoot = uiState.value.useRoot,
                            fullOutput = buffer.toString(),
                        )
                        _uiState.update {
                            it.copy(
                                isRunning = false,
                                statusMessage = if (success) "Command finished successfully" else "Command exited with ${event.exitCode}",
                                statusSuccess = success,
                            )
                        }
                    }
                }
            }
        }
    }

    fun clearOutput() {
        _uiState.update { it.copy(output = emptyList(), statusMessage = null) }
    }

    fun applyHistory(command: String) {
        _uiState.update { it.copy(command = command) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            shellRepository.clearHistory()
            _uiState.update { it.copy(history = emptyList()) }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}

@Composable
fun ShellRunnerRoute(
    onBack: () -> Unit,
    viewModel: ShellRunnerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ShellRunnerScreen(
        uiState = uiState,
        onBack = onBack,
        onCommandChanged = viewModel::updateCommand,
        onUseRootChanged = viewModel::setUseRoot,
        onRun = viewModel::runCommand,
        onClearOutput = viewModel::clearOutput,
        onApplyHistory = viewModel::applyHistory,
        onClearHistory = viewModel::clearHistory,
        onConsumeMessage = viewModel::consumeMessage,
    )
}

@Composable
private fun ShellRunnerScreen(
    uiState: ShellRunnerUiState,
    onBack: () -> Unit,
    onCommandChanged: (String) -> Unit,
    onUseRootChanged: (Boolean) -> Unit,
    onRun: () -> Unit,
    onClearOutput: () -> Unit,
    onApplyHistory: (String) -> Unit,
    onClearHistory: () -> Unit,
    onConsumeMessage: () -> Unit,
) {
    val clipboardManager = LocalClipboardManager.current
    val terminalState = rememberLazyListState()

    LaunchedEffect(uiState.output.size) {
        if (uiState.output.isNotEmpty()) {
            terminalState.animateScrollToItem(uiState.output.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            FeatureTopBar(
                title = "Shell Runner",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onClearOutput) {
                        Icon(imageVector = Icons.Rounded.DeleteSweep, contentDescription = "Clear output")
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
                    title = "Terminal Session",
                    subtitle = "Run any shell command with or without root. Output streams live and history is persisted in Room.",
                )
            }
            item {
                GlassCard(accent = CookieGreen) {
                    TextField(
                        value = uiState.command,
                        onValueChange = onCommandChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Command") },
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterChip(
                            selected = uiState.useRoot,
                            onClick = { onUseRootChanged(!uiState.useRoot) },
                            label = { Text(if (uiState.useRoot) "Root" else "User shell") },
                        )
                        FilterChip(
                            selected = uiState.isRunning,
                            onClick = onRun,
                            label = { Text(if (uiState.isRunning) "Running" else "Run") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                )
                            },
                        )
                        FilterChip(
                            selected = false,
                            onClick = {
                                clipboardManager.setText(
                                    AnnotatedString(uiState.output.joinToString(separator = "\n") { it.text }),
                                )
                            },
                            label = { Text("Copy output") },
                            leadingIcon = {
                                Icon(imageVector = Icons.Rounded.ContentCopy, contentDescription = null)
                            },
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
            item {
                TerminalPane(lines = uiState.output, listState = terminalState)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionLabel(
                        title = "Recent Commands",
                        subtitle = "Tap any history item to load it back into the prompt.",
                    )
                    FilterChip(
                        selected = false,
                        onClick = onClearHistory,
                        label = { Text("Clear history") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null)
                        },
                    )
                }
            }
            if (uiState.history.isEmpty()) {
                item {
                    EmptyState(
                        title = "No command history yet",
                        subtitle = "Run a shell command and CookieSH will keep the recent results locally.",
                    )
                }
            } else {
                items(uiState.history, key = { it.id }) { history ->
                    GlassCard(accent = MaterialTheme.colorScheme.secondary, onClick = { onApplyHistory(history.command) }) {
                        Text(text = history.command, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = history.outputPreview.ifBlank { "No output captured." },
                            style = MaterialTheme.typography.bodySmall,
                            color = CookieTextSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalPane(
    lines: List<TerminalLine>,
    listState: LazyListState,
) {
    GlassCard(accent = CookieGreen) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(CookieTerminal, shape = MaterialTheme.shapes.large)
                .padding(14.dp),
        ) {
            if (lines.isEmpty()) {
                Text(
                    text = "No output yet. Run a command to start streaming.",
                    style = TerminalTextStyle,
                    color = CookieTextSecondary,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(items = lines, key = { it.text + it.isError }) { line: TerminalLine ->
                        Text(
                            text = line.text,
                            style = TerminalTextStyle,
                            color = if (line.isError) MaterialTheme.colorScheme.error else CookieGreen,
                        )
                    }
                }
            }
        }
    }
}
