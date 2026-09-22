package com.firefly.app.ui.settings

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.firefly.app.FireflyApp
import com.firefly.app.R
import com.firefly.app.core.messaging.NamePolicy
import com.firefly.app.data.prefs.SettingsStore
import com.firefly.app.ui.common.OemBattery
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** PRD §5 screen 8: name, radio knobs for field tests, battery-optimisation help. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = (context.applicationContext as FireflyApp).container
    val scope = rememberCoroutineScope()
    val scanOn by c.settings.scanOnMs.collectAsStateWithLifecycle(initialValue = SettingsStore.DEFAULT_SCAN_ON_MS)
    val scanOff by c.settings.scanOffMs.collectAsStateWithLifecycle(initialValue = SettingsStore.DEFAULT_SCAN_OFF_MS)
    val adv by c.settings.advInterval.collectAsStateWithLifecycle(initialValue = SettingsStore.DEFAULT_ADV_INTERVAL)
    var name by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { name = c.settings.displayName.first().orEmpty() }
    val advice = remember { OemBattery.advice(context) }
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = NamePolicy.sanitise(it); scope.launch { c.settings.setDisplayName(name) } },
                label = { Text(stringResource(R.string.home_name_label)) },
                supportingText = { Text(stringResource(R.string.settings_name_desc)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text(stringResource(R.string.settings_battery_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_battery_body, advice.maker), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp))
            advice.steps.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp)) }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { OemBattery.open(context, advice) }) { Text(stringResource(R.string.settings_battery_open)) }

            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            Text(stringResource(R.string.settings_radio_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_radio_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.settings_scan_label), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                listOf(8_000L to 2_000L, 6_000L to 4_000L, 4_000L to 6_000L, 10_000L to 0L).forEach { (on, off) ->
                    FilterChip(
                        selected = scanOn == on && scanOff == off,
                        onClick = { scope.launch { c.settings.setScanWindow(on, off) } },
                        label = { Text(if (off == 0L) stringResource(R.string.settings_scan_always) else "${on / 1000}s / ${off / 1000}s") },
                    )
                }
            }
            Text(stringResource(R.string.settings_scan_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.settings_adv_label), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                listOf(
                    SettingsStore.ADV_LOW_LATENCY to R.string.settings_adv_fast,
                    SettingsStore.ADV_BALANCED to R.string.settings_adv_balanced,
                    SettingsStore.ADV_LOW_POWER to R.string.settings_adv_saver,
                ).forEach { (mode, label) ->
                    FilterChip(selected = adv == mode, onClick = { scope.launch { c.settings.setAdvInterval(mode) } }, label = { Text(stringResource(label)) })
                }
            }
            Text(stringResource(R.string.settings_adv_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Card { Text(stringResource(R.string.settings_radio_note), Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
        }
    }
}
