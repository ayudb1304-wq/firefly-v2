package com.firefly.app.ui.map

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.firefly.app.radio.RadioStatus
import com.firefly.app.ui.codebook.CodebookSheet
import com.firefly.app.ui.codebook.PingText
import com.firefly.app.ui.codebook.Recipient
import com.firefly.app.ui.common.Format
import com.firefly.app.ui.timeline.TimelineScreen
import com.firefly.app.data.db.PingEntity
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
    val frame by vm.frame.collectAsStateWithLifecycle()
    val venue by vm.venue.collectAsStateWithLifecycle()
    val myName by vm.myName.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis()) {
        while (true) { delay(1_000); value = System.currentTimeMillis() }
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }

    val pickVenue = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::importVenue) }
    var menuOpen by remember { mutableStateOf(false) }

    val pings by vm.pings.collectAsStateWithLifecycle()
    val names by vm.names.collectAsStateWithLifecycle()
    val everyone = stringResource(R.string.everyone)
    val recipients = remember(members, names, everyone) {
        listOf(Recipient.everyone(everyone)) + members.map { Recipient(it.senderId, names[it.senderId] ?: SenderId.hex(it.senderId)) }
    }
    var sheetFor by remember { mutableStateOf<Recipient?>(null) }
    var showTimeline by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<PingEntity?>(null) }
    val sosActive by vm.sosActive.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.incoming.collect { banner = it } }
    // Ordinary pings auto-hide; an SOS stays until dismissed (PRD F1: persistent alert).
    LaunchedEffect(banner) { if (banner != null && banner!!.code != com.firefly.app.core.protocol.Codebook.HELP) { delay(8_000); banner = null } }

    sheetFor?.let { r ->
        CodebookSheet(
            recipients = recipients,
            initialRecipient = r,
            pois = venue?.pois.orEmpty(),
            onSend = { code, arg, target -> vm.send(code, arg, target); sheetFor = null },
            onDismiss = { sheetFor = null },
        )
    }
    BackHandler(enabled = showTimeline) { showTimeline = false }
    var showQr by remember { mutableStateOf(false) }
    if (showQr) com.firefly.app.ui.qr.GroupQrDialog(code = session.code, onDismiss = { showQr = false })
    if (showTimeline) {
        TimelineScreen(
            pings = pings, names = names, pois = venue?.pois.orEmpty(), nowMillis = now,
            onReply = { id -> showTimeline = false; sheetFor = recipients.firstOrNull { it.senderId == id } ?: Recipient(id, names[id] ?: SenderId.hex(id)) },
            onBack = { showTimeline = false },
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { sheetFor = recipients.first() },
                icon = { Icon(Icons.Default.Send, contentDescription = null) },
                text = { Text(stringResource(R.string.map_send)) },
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.map_group, session.code))
                        Text(
                            stringResource(R.string.map_subtitle, myName?.let { "$it (${SenderId.hex(session.senderId)})" } ?: SenderId.hex(session.senderId), members.size),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showTimeline = true }) { Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.timeline_title)) }
                    TextButton(onClick = { FireflyService.stop(context); vm.leave {} }) { Text(stringResource(R.string.map_leave)) }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.map_more)) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.map_show_qr)) }, onClick = { menuOpen = false; showQr = true })
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.map_load_venue)) },
                                onClick = { menuOpen = false; pickVenue.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                            )
                            if (venue != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.map_remove_venue, venue!!.name)) },
                                    onClick = { menuOpen = false; vm.removeVenue() },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (sosActive) {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.sos_active), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = vm::cancelSos) { Text(stringResource(R.string.sos_cancel), color = MaterialTheme.colorScheme.onError) }
                    }
                }
            }
            banner?.let { b ->
                val from = names[b.senderId] ?: SenderId.hex(b.senderId)
                val poiName = venue?.pois?.firstOrNull { it.index == b.arg }?.name
                val isSos = b.code == com.firefly.app.core.protocol.Codebook.HELP
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).clickable { banner = null; sheetFor = recipients.firstOrNull { it.senderId == b.senderId } ?: Recipient(b.senderId, from) },
                    colors = CardDefaults.cardColors(containerColor = if (isSos) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            if (isSos) {
                                val myFix = me
                                val dist = if (myFix != null && b.lat != null && b.lon != null) GeoMath.distanceMetres(myFix.lat, myFix.lon, b.lat, b.lon) else null
                                val bearing = if (dist != null) GeoMath.bearingDegrees(myFix!!.lat, myFix.lon, b.lat!!, b.lon!!) else null
                                Text(stringResource(R.string.sos_incoming, from), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                                Text(
                                    if (dist != null) stringResource(R.string.sos_incoming_at, Format.distance(dist), Format.bearingArrow(bearing)) else stringResource(R.string.sos_incoming_nopos),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            } else {
                                Text(from, style = MaterialTheme.typography.labelMedium)
                                Text(PingText.describe(context, b.code, b.arg, poiName), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(R.string.banner_tap_to_reply), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (isSos) TextButton(onClick = { banner = null }) { Text(stringResource(R.string.banner_dismiss)) }
                    }
                }
            }
            val caption = when {
                frame.waitingForFix -> stringResource(R.string.map_waiting_fix)
                frame.venue != null -> frame.venue!!.name
                frame.venueDistanceMetres != null -> stringResource(R.string.map_off_venue, Format.distance(frame.widthMetres), Format.distance(frame.venueDistanceMetres))
                else -> stringResource(R.string.map_free, Format.distance(frame.widthMetres))
            }
            Text(caption, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            Box(Modifier.fillMaxWidth().heightIn(min = 240.dp).weight(1f), contentAlignment = Alignment.Center) {
                if (!frame.waitingForFix) {
                    VenueMap(frame = frame, me = me, members = members, nowMillis = now, modifier = Modifier.fillMaxSize())
                } else {
                    Text(stringResource(R.string.map_waiting_fix_body), Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            StatusRow(
                radio = radio, meFixAgeMillis = me?.let { now - it.timeMillis }, accuracy = me?.accuracyMetres,
                nearby = members.count { now - it.lastSeen <= 60_000L }, myId = SenderId.hex(session.senderId),
            )
            HorizontalDivider()
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                if (members.isEmpty()) {
                    item { Text(stringResource(R.string.map_no_members), Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
                }
                items(members, key = { it.senderId }) { m ->
                    val myFix = me
                    val dist = if (myFix != null && m.lat != null && m.lon != null) GeoMath.distanceMetres(myFix.lat, myFix.lon, m.lat, m.lon) else null
                    val bearing = if (dist != null) GeoMath.bearingDegrees(myFix!!.lat, myFix.lon, m.lat!!, m.lon!!) else null
                    ListItem(
                        headlineContent = { Text(m.name ?: SenderId.hex(m.senderId)) },
                        supportingContent = {
                            Text("${Format.distance(dist)} ${Format.bearingArrow(bearing)} · ${Format.age(now, m.lastSeen)} · ${m.hops} hop · ${m.rssi} dBm")
                        },
                        modifier = Modifier.clickable { sheetFor = Recipient(m.senderId, m.name ?: SenderId.hex(m.senderId)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusRow(radio: RadioStatus, meFixAgeMillis: Long?, accuracy: Float?, nearby: Int, myId: String) {
    var details by remember { mutableStateOf(false) }
    val problem = radio.degradedReason
    val headline = when {
        problem != null -> stringResource(R.string.status_problem, problem)
        !radio.running -> stringResource(R.string.status_starting)
        else -> stringResource(R.string.status_ok)
    }
    val gps = if (meFixAgeMillis == null) stringResource(R.string.status_gps_none) else stringResource(R.string.status_gps, accuracy?.toInt() ?: 0)
    val dot = if (problem != null) "⚠" else if (radio.running) "●" else "○"
    val colour = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
    Row(
        Modifier.fillMaxWidth().clickable { details = true }.padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$dot $headline · $gps · ${stringResource(R.string.status_friends, nearby)}", style = MaterialTheme.typography.labelMedium, color = colour, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.status_tap), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
    }
    if (details) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { details = false },
            confirmButton = { TextButton(onClick = { details = false }) { Text(stringResource(R.string.status_close)) } },
            title = { Text(stringResource(R.string.status_details_title)) },
            text = {
                Column {
                    val on = stringResource(R.string.status_on)
                    val off = stringResource(R.string.status_off)
                    DetailRow(stringResource(R.string.status_bt_advertising), if (radio.advertising) on else off, null)
                    DetailRow(stringResource(R.string.status_bt_scanning), if (radio.scanning) on else stringResource(R.string.status_off_pause), null)
                    DetailRow(stringResource(R.string.status_sent), "${radio.packetsSent}", stringResource(R.string.status_sent_desc))
                    DetailRow(stringResource(R.string.status_received), "${radio.packetsReceived}", stringResource(R.string.status_received_desc))
                    DetailRow(stringResource(R.string.status_dups), "${radio.packetsDeduped}", stringResource(R.string.status_dups_desc))
                    DetailRow(stringResource(R.string.status_relayed), "${radio.packetsRelayed}", stringResource(R.string.status_relayed_desc))
                    DetailRow(
                        stringResource(R.string.status_gps_row),
                        if (meFixAgeMillis == null) stringResource(R.string.status_gps_none) else stringResource(R.string.status_gps_value, accuracy?.toInt() ?: 0, Format.age(System.currentTimeMillis(), System.currentTimeMillis() - meFixAgeMillis)),
                        stringResource(R.string.status_gps_desc),
                    )
                    DetailRow(stringResource(R.string.status_me), myId, stringResource(R.string.status_me_desc))
                }
            },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String, description: String?) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}
