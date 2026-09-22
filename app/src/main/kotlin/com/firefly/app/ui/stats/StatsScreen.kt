package com.firefly.app.ui.stats

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firefly.app.FireflyApp
import com.firefly.app.R
import com.firefly.app.BuildConfig
import com.firefly.app.core.group.SenderId
import com.firefly.app.ui.common.Format
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** PRD G3: live counters, battery drain, and CSV export (PRD G2). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as FireflyApp
    val c = app.container
    val radio by c.radioStatus.status.collectAsStateWithLifecycle()
    val logCount by c.fieldLog.count.collectAsStateWithLifecycle(initialValue = 0)
    val now by produceState(System.currentTimeMillis()) { while (true) { delay(1_000); value = System.currentTimeMillis() } }
    val unique by remember(now / 10_000) { c.fieldLog.uniqueSendersSince(now - 5 * 60_000L) }.collectAsStateWithLifecycle(initialValue = 0)
    val keepLog by c.settings.keepLog.collectAsStateWithLifecycle(initialValue = false)
    val session by c.groupRepository.current.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            val uptime = radio.startedAtMillis?.let { Format.age(now, it).removeSuffix(" ago") } ?: "—"
            Stat(stringResource(R.string.stats_uptime), uptime)
            Stat(stringResource(R.string.stats_sent), "${radio.packetsSent}")
            Stat(stringResource(R.string.stats_received), "${radio.packetsReceived}")
            Stat(stringResource(R.string.stats_relayed), "${radio.packetsRelayed}" + if (radio.relaysDropped > 0) "  (${radio.relaysDropped} dropped)" else "")
            Stat(stringResource(R.string.stats_dups), "${radio.packetsDeduped}" + if (radio.packetsReceived > 0) "  (${"%.1f".format(radio.packetsDeduped.toDouble() / radio.packetsReceived)}×)" else "")
            Stat(stringResource(R.string.stats_foreign), "${radio.packetsForeign}")
            Stat(stringResource(R.string.stats_unique), "$unique")
            Stat(stringResource(R.string.stats_rate), "%.2f /s".format(radio.rxPerSecond), stringResource(R.string.stats_rate_desc))
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            val b = radio.battery
            Stat(stringResource(R.string.stats_battery), if (b == null) "—" else "${b.level}%" + if (b.charging) " ⚡" else "")
            Stat(
                stringResource(R.string.stats_drain),
                b?.drainPerHour?.let { "%.2f %%/h".format(it) } ?: stringResource(R.string.stats_drain_wait),
                stringResource(R.string.stats_drain_desc),
                warn = (b?.drainPerHour ?: 0.0) > 3.0,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text(stringResource(R.string.stats_log_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.stats_log_rows, logCount), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.stats_log_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = !busy && logCount > 0,
                    onClick = {
                        busy = true
                        scope.launch {
                            runCatching {
                                val st = c.settings
                                val summary = "scan=${st.scanOnMs.first()}/${st.scanOffMs.first()}ms adv=${st.advInterval.first()}"
                                val uri = c.fieldLog.export(session?.code, session?.let { SenderId.hex(it.senderId) } ?: "----", BuildConfig.VERSION_NAME, summary)
                                val send = Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                context.startActivity(Intent.createChooser(send, context.getString(R.string.stats_export)))
                            }.onFailure { message = it.message ?: "export failed" }
                            busy = false
                        }
                    },
                ) { Text(stringResource(R.string.stats_export)) }
                OutlinedButton(enabled = !busy && logCount > 0, onClick = { scope.launch { c.fieldLog.clear(); message = context.getString(R.string.stats_cleared) } }) {
                    Text(stringResource(R.string.stats_clear))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.stats_keep_log), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.stats_keep_log_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = keepLog, onCheckedChange = { v -> scope.launch { c.settings.setKeepLog(v) } })
            }
            message?.let {
                Spacer(Modifier.height(8.dp))
                Card { Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
            }
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.stats_footer, BuildConfig.VERSION_NAME, android.os.Build.MANUFACTURER, android.os.Build.MODEL, android.os.Build.VERSION.RELEASE), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Stat(label: String, value: String, description: String? = null, warn: Boolean = false) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
