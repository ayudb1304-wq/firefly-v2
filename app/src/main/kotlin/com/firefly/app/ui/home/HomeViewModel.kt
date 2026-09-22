package com.firefly.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firefly.app.core.messaging.NamePolicy
import com.firefly.app.data.prefs.SettingsStore
import com.firefly.app.data.repo.GroupRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val name: String = "",
    val joinInput: String = "",
    val error: Boolean = false,
    val busy: Boolean = false,
)

class HomeViewModel(private val groups: GroupRepository, private val settings: SettingsStore) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { _state.update { it.copy(name = settings.displayName.first().orEmpty()) } }
    }

    fun onName(text: String) = _state.update { it.copy(name = NamePolicy.sanitise(text)) }

    private suspend fun saveName() = settings.setDisplayName(NamePolicy.sanitise(_state.value.name))

    fun onJoinInput(text: String) = _state.update { it.copy(joinInput = text.uppercase().take(8), error = false) }

    fun create() = viewModelScope.launch {
        _state.update { it.copy(busy = true) }
        saveName()
        groups.create() // AppRoot switches to the map when the session appears
    }

    fun joinScanned(code: String) {
        _state.update { it.copy(joinInput = code) }
        join()
    }

    fun join() = viewModelScope.launch {
        _state.update { it.copy(busy = true) }
        saveName()
        val ok = groups.join(_state.value.joinInput) != null
        if (!ok) _state.update { it.copy(busy = false, error = true) }
    }
}
