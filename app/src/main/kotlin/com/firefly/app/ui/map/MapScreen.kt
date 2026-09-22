package com.firefly.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.firefly.app.FireflyApp
import com.firefly.app.R
import com.firefly.app.core.geo.GeoMath
import com.firefly.app.core.group.SenderId
import com.firefly.app.data.repo.GroupSession
import com.firefly.app.radio.FireflyService
import com.firefly.app.ui.common.Format
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(session: GroupSession) {
    val context = LocalContext.current
    val app = context.applicationContext as FireflyApp
    val vm: MapViewModel = viewModel { MapViewModel(app.container) }
    val members by vm.members.collectAsStateWithLifecycle()
    val me by vm.myFix.collectAsStateWithLifecycle()
    val radio by vm.radio.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(1_000); value = System.currentTimeMillis() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.map_group, session.code))
                        Text(
                            stringResource(R.string.map_subtitle, SenderId.hex(session.senderId), members.size),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { FireflyService.stop(context); vm.leave {} }) { Text(stringResource(R.string.map_leave)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            VenueMap(
                venue = vm.venue,
                me = me,
                members = members,
                nowMillis = now,
                modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp).weight(1f),
            )
            StatusRow(radio = radio, meFixAgeMillis = me?.let { now - it.timeMillis }, accuracy = me?.accuracyMetres)
            HorizontalDivider()
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                if (members.isEmpty()) {
                    item { Text(stringResource(R.string.map_no_members), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
                }
                items(members, key = { it.senderId }) { m ->
                    val dist = if (me != null && m.lat != null && m.lon != null) GeoMath.distanceMetres(me!!.lat, me!!.lon, m.lat, m.lon) else null
                    val bearing = if (dist != null) GeoMath.bearingDegrees(me!!.lat, me!!.lon, m.lat!!, m.lon!!) else null
                    ListItem(
                        headlineContent = { Text(m.name ?: SenderId.hex(m.senderId)) },
                        supportingContent = {
                            Text("${Format.distance(dist)} ${Format.bearingArrow(bearing)} · ${Format.age(now, m.lastSeen)} · ${m.hops} hop · ${m.rssi} dBm")
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusRow(radio: com.firefly.app.radio.RadioStatus, meFixAgeMillis: Long?, accuracy: Float?) {
    val text = buildString {
        append(if (radio.degradedReason != null) "⚠ ${radio.degradedReason}" else if (radio.running) "● radio" else "○ radio off")
        append(if (radio.advertising) " adv" else "")
        append(if (radio.scanning) " scan" else "")
        append(" · tx ${radio.packetsSent} rx ${radio.packetsReceived} dup ${radio.packetsDeduped}")
        append(" · gps ")
        append(if (meFixAgeMillis == null) "—" else "${accuracy?.toInt() ?: "?"} m, ${meFixAgeMillis / 1000}s")
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.Start) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = if (radio.degradedReason != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}
