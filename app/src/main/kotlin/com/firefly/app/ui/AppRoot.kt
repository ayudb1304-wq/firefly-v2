package com.firefly.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firefly.app.data.repo.GroupSession
import com.firefly.app.di.AppContainer
import com.firefly.app.radio.FireflyService
import com.firefly.app.ui.common.Permissions
import com.firefly.app.ui.home.HomeScreen
import com.firefly.app.ui.map.MapScreen
import com.firefly.app.ui.onboarding.OnboardingScreen
import kotlinx.coroutines.flow.map

/** Session wrapper so "still loading from DataStore" is distinguishable from "no group". */
private data class SessionState(val session: GroupSession?)

/**
 * Top-level screen switch. No navigation library: the app has three states —
 * permissions missing → onboarding; no group → home; in a group → map.
 */
@Composable
fun AppRoot(container: AppContainer) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(Permissions.allGranted(context)) }

    // Re-check permissions whenever we come back from Settings.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = Permissions.allGranted(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val sessionFlow = remember(container) { container.groupRepository.session.map { SessionState(it) } }
    val state by sessionFlow.collectAsStateWithLifecycle(initialValue = null)

    val session = state?.session
    LaunchedEffect(granted, session) {
        if (granted && session != null) FireflyService.start(context)
    }

    when {
        !granted -> OnboardingScreen(onGranted = { granted = true })
        state == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        session == null -> HomeScreen()
        else -> MapScreen(session = session)
    }
}
