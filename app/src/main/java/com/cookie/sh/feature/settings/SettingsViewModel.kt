package com.cookie.sh.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cookie.sh.data.prefs.SettingsRepository
import com.cookie.sh.data.prefs.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val themeMode = settingsRepository.themeMode.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ThemeMode.System
    )

    val accentColor = settingsRepository.accentColor.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        "#7C4DFF"
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setAccentColor(hex: String) {
        viewModelScope.launch {
            settingsRepository.setAccentColor(hex)
        }
    }
}
