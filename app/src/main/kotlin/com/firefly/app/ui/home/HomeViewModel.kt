package com.firefly.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firefly.app.data.repo.GroupRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val joinInput: String = "",
    val error: Boolean = false,
    val busy: Boolean = false,
)

class HomeViewModel(private val groups: GroupRepository) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    fun onJoinInput(text: String) = _state.update { it.copy(joinInput = text.uppercase().take(8), error = false) }

    fun create() = viewModelScope.launch {
        _state.update { it.copy(busy = true) }
        groups.create() // AppRoot switches to the map when the session appears
    }

    fun join() = viewModelScope.launch {
        _state.update { it.copy(busy = true) }
        val ok = groups.join(_state.value.joinInput) != null
        if (!ok) _state.update { it.copy(busy = false, error = true) }
    }
}
