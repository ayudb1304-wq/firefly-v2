package com.firefly.app.ui.lighthouse

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.firefly.app.R
import com.firefly.app.core.messaging.LighthousePattern
import kotlinx.coroutines.delay

/**
 * PRD E1: turn the screen into a full-brightness flashing beacon with my
 * pattern, so a friend within sight can pick me out. Auto-off after 2 minutes.
 */
@Composable
fun LighthouseScreen(senderId: Int, onClose: () -> Unit) {
    val context = LocalContext.current
    val pattern = remember(senderId) { LighthousePattern.forSender(senderId) }
    var colour by remember { mutableStateOf(Color.Black) }
    var remainingMs by remember { mutableStateOf(LighthousePattern.AUTO_OFF_MS) }

    // Max brightness + keep screen on while the lighthouse is up; restore on exit.
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        val prev = window?.attributes?.screenBrightness
        window?.let {
            it.attributes = it.attributes.apply { screenBrightness = 1f }
            it.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.let {
                it.attributes = it.attributes.apply { screenBrightness = prev ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
                it.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    LaunchedEffect(pattern) {
        val start = System.currentTimeMillis()
        while (true) {
            for (c in pattern) {
                colour = Color(c.argb); delay(LighthousePattern.FLASH_MS)
                colour = Color.Black; delay(LighthousePattern.GAP_MS)
            }
            delay(LighthousePattern.CYCLE_PAUSE_MS)
            remainingMs = LighthousePattern.AUTO_OFF_MS - (System.currentTimeMillis() - start)
            if (remainingMs <= 0) { onClose(); return@LaunchedEffect }
        }
    }
    BackHandler(onBack = onClose)

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(colour).clickable(onClick = onClose), contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row {
                    pattern.forEach { c -> Box(Modifier.padding(4.dp).size(18.dp).background(Color(c.argb), CircleShape)) }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.lighthouse_hint, LighthousePattern.label(pattern), (remainingMs / 1000).toInt()),
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
