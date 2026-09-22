package com.firefly.app.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.firefly.app.data.db.MemberEntity
import com.firefly.app.di.AppContainer
import com.firefly.app.location.Fix
import com.firefly.app.radio.RadioStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MapViewModel(private val container: AppContainer) : ViewModel() {
    val venue get() = container.venue

    val members: StateFlow<List<MemberEntity>> =
        container.memberRepository.members.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val myFix: StateFlow<Fix?> = container.locationSource.fixes

    val radio: StateFlow<RadioStatus> = container.radioStatus.status

    fun leave(onDone: () -> Unit) = viewModelScope.launch {
        container.groupRepository.leave()
        onDone()
    }
}
