package com.cookie.sh.feature.system

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cookie.sh.core.model.IntegrityResult
import com.cookie.sh.core.model.SystemStats
import com.cookie.sh.data.repository.SystemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SystemOverviewUiState(
    val stats: SystemStats = SystemStats(),
    val integrity: IntegrityResult = IntegrityResult(),
    val displayDpi: Int = 0,
)

@HiltViewModel
class SystemOverviewViewModel @Inject constructor(
    private val systemRepository: SystemRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SystemOverviewUiState())
    val uiState: StateFlow<SystemOverviewUiState> = _uiState.asStateFlow()

    init {
        startPolling()
        checkIntegrity()
        loadDisplayDpi()
    }

    private fun loadDisplayDpi() {
        viewModelScope.launch {
            val dpi = systemRepository.getDisplayDpi()
            _uiState.update { it.copy(displayDpi = dpi) }
        }
    }

    fun setDisplayDpi(dpi: Int) {
        viewModelScope.launch {
            systemRepository.setDisplayDpi(dpi)
            loadDisplayDpi()
        }
    }

    fun resetDisplayDpi() {
        viewModelScope.launch {
            systemRepository.resetDisplayDpi()
            loadDisplayDpi()
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                val stats = systemRepository.getSystemStats()
                _uiState.update { it.copy(stats = stats) }
                delay(2000)
            }
        }
    }

    fun checkIntegrity() {
        viewModelScope.launch {
            val result = systemRepository.checkIntegrity()
            _uiState.update { it.copy(integrity = result) }
        }
    }

    fun fixIntegrityProps() {
        viewModelScope.launch {
            systemRepository.fixIntegrityProps()
            checkIntegrity()
        }
    }

    fun toggleSelinux(enforcing: Boolean) {
        viewModelScope.launch {
            systemRepository.setSelinuxEnabled(enforcing)
            val stats = systemRepository.getSystemStats()
            _uiState.update { it.copy(stats = stats) }
        }
    }
}
