package com.firefly.app.radio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Process-wide bridge between the service (writer) and the UI (reader). No binding needed. */
class RadioStatusHolder {
    private val _status = MutableStateFlow(RadioStatus())
    val status: StateFlow<RadioStatus> = _status.asStateFlow()

    fun update(transform: (RadioStatus) -> RadioStatus) = _status.update(transform)
    fun reset() = _status.update { RadioStatus() }
}
