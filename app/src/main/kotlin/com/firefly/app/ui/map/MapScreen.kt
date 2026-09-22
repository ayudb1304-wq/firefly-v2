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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider as Divider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.ui.platform.LocalResources
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
import com.firefly.app.ui.lighthouse.LighthouseScreen
import com.firefly.app.ui.stats.StatsScreen
import com.firefly.app.ui.settings.SettingsScreen
import com.firefly.app.ui.common.OemBattery
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.provider.Settings
import com.firefly.app.core.protocol.Codebook
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.Button
import com.firefly.app.data.db.PingEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.FloatingActionButtonDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(session: GroupSession) {
    val context = LocalContext.current
    val res = LocalResources.current
    val app = context.applicationContext as FireflyApp
    val vm: MapViewModel = viewModel { MapViewModel(app.container, app.resources) }
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
    val uiScope = rememberCoroutineScope()
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
    var confirmLeave by remember { mutableStateOf(false) }
    val unread by vm.unread.collectAsStateWithLifecycle()
    val heading by vm.heading.collectAsStateWithLifecycle()
    val meetPins by vm.meetPins.collectAsStateWithLifecycle()
    val lighthouses by vm.lighthouses.collectAsStateWithLifecycle()
    var meetPlan by remember { mutableStateOf<MapViewModel.MeetPlan?>(null) }
    var showLighthouse by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val batteryCardDismissed by app.container.settings.batteryCardDismissed.collectAsStateWithLifecycle(initialValue = true)
    val oem = remember { OemBattery.advice(context) }
    DisposableEffect(Unit) { vm.startCompass(); onDispose { vm.stopCompass() } }
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
            onMeetHalfway = { r -> sheetFor = null; meetPlan = vm.planMeeting(r.senderId) },
            onSend = { code, arg, target ->
                if (code == Codebook.LIGHTHOUSE_ON) { sheetFor = null; showLighthouse = true; vm.startLighthouse(); return@CodebookSheet }
                vm.send(code, arg, target)
                sheetFor = null
                if (code != com.firefly.app.core.protocol.Codebook.HELP) {
                    val to = recipients.firstOrNull { it.senderId == target }?.label ?: SenderId.hex(target)
                    val poiName = venue?.pois?.firstOrNull { it.index == arg }?.name
                    val text = res.getString(R.string.codebook_sent, to, PingText.describe(res, code, arg, poiName))
                    uiScope.launch { snackbar.showSnackbar(text) }
                }
            },
            onDismiss = { sheetFor = null },
        )
    }
    BackHandler(enabled = showTimeline) { showTimeline = false }
    if (showLighthouse) LighthouseScreen(senderId = session.senderId, onClose = { showLighthouse = false })
    meetPlan?.let { plan ->
        AlertDialog(
            onDismissRequest = { meetPlan = null },
            title = { Text(stringResource(R.string.meet_title, when (plan) { is MapViewModel.MeetPlan.Ready -> names[plan.member.senderId] ?: SenderId.hex(plan.member.senderId); is MapViewModel.MeetPlan.NoTheirPosition -> names[plan.member.senderId] ?: SenderId.hex(plan.member.senderId); else -> "" })) },
            text = {
                when (plan) {
                    is MapViewModel.MeetPlan.Ready -> Column {
                        val s = plan.suggestion
                        val poi = s.poi
                        Text(if (poi != null) stringResource(R.string.meet_at_poi, poi.name) else stringResource(R.string.meet_at_point), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.meet_distances, Format.distance(s.distanceFromMeMetres), Format.distance(s.distanceFromThemMetres)), style = MaterialTheme.typography.bodyMedium)
                        if (s.theirPositionStale) {
                            Text(stringResource(R.string.meet_stale_warning, names[plan.member.senderId] ?: SenderId.hex(plan.member.senderId)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    is MapViewModel.MeetPlan.NoTheirPosition -> Text(stringResource(R.string.meet_no_position, names[plan.member.senderId] ?: SenderId.hex(plan.member.senderId)))
                    MapViewModel.MeetPlan.NoMyPosition -> Text(stringResource(R.string.meet_no_my_position))
                }
            },
            confirmButton = {
                if (plan is MapViewModel.MeetPlan.Ready) {
                    Button(onClick = {
                        vm.propose(plan); meetPlan = null
                        val who = names[plan.member.senderId] ?: SenderId.hex(plan.member.senderId)
                        val where = plan.suggestion.poi?.name ?: res.getString(R.string.codebook_here)
                        uiScope.launch { snackbar.showSnackbar(res.getString(R.string.codebook_sent, who, res.getString(R.string.code_meet_at) + " " + where)) }
                    }) { Text(stringResource(R.string.meet_send)) }
                } else {
                    TextButton(onClick = { meetPlan = null }) { Text(stringResource(R.string.status_close)) }
                }
            },
            dismissButton = { if (plan is MapViewModel.MeetPlan.Ready) TextButton(onClick = { meetPlan = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.leave_confirm_title, session.code)) },
            text = { Text(stringResource(R.string.leave_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; FireflyService.stop(context); vm.leave {} }) {
                    Text(stringResource(R.string.leave_confirm_yes), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    var showQr by remember { mutableStateOf(false) }
    if (showQr) com.firefly.app.ui.qr.GroupQrDialog(code = session.code, onDismiss = { showQr = false })
    if (showStats) { StatsScreen(onBack = { showStats = false }); return }
    if (showSettings) { SettingsScreen(onBack = { showSettings = false }); return }
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
                icon = { Icon(painterResource(R.drawable.ic_messages), contentDescription = null) },
                text = { Text(stringResource(R.string.map_message_fab), fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
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
                    IconButton(onClick = { vm.markTimelineSeen(); showTimeline = true }) {
                        BadgedBox(badge = { if (unread > 0) Badge { Text("$unread") } }) {
                            Icon(painterResource(R.drawable.ic_messages), contentDescription = stringResource(R.string.timeline_title))
                        }
                    }
                    IconButton(onClick = { showQr = true }) { Icon(painterResource(R.drawable.ic_qr), contentDescription = stringResource(R.string.map_show_qr)) }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.map_more)) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
                            DropdownMenuItem(text = { Text(stringResource(R.string.map_stats)) }, onClick = { menuOpen = false; showStats = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.map_settings)) }, onClick = { menuOpen = false; showSettings = true })
                            Divider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.map_leave), color = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; confirmLeave = true },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Fix-it card (ARCHITECTURE.md §6 Degraded state)
            when (radio.degradedReason) {
                "Bluetooth is off" -> FixCard(stringResource(R.string.fix_bluetooth_title), stringResource(R.string.fix_bluetooth_body), stringResource(R.string.fix_bluetooth_action)) {
                    val canAsk = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
                        androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (canAsk) runCatching { context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                }
                "Location is off" -> FixCard(stringResource(R.string.fix_location_title), stringResource(R.string.fix_location_body), stringResource(R.string.fix_location_action)) {
                    runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                }
            }
            if (!batteryCardDismissed) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.battery_card_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.battery_card_body, oem.maker), style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { showSettings = true }) { Text(stringResource(R.string.battery_card_action)) }
                            TextButton(onClick = { uiScope.launch { app.container.settings.setBatteryCardDismissed(true) } }) { Text(stringResource(R.string.battery_card_dismiss)) }
                        }
                    }
                }
            }
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
                                Text(PingText.describe(res, b.code, b.arg, poiName), style = MaterialTheme.typography.titleMedium)
                                if (b.code == Codebook.MEET_AT) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                                        Button(onClick = { vm.accept(b); banner = null; uiScope.launch { snackbar.showSnackbar(res.getString(R.string.codebook_sent, from, res.getString(R.string.code_on_my_way))) } }) { Text(stringResource(R.string.meet_accept)) }
                                        TextButton(onClick = { banner = null; sheetFor = Recipient(b.senderId, from) }) { Text(stringResource(R.string.meet_counter)) }
                                    }
                                } else {
                                    Text(stringResource(R.string.banner_tap_to_reply), style = MaterialTheme.typography.labelSmall)
                                }
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
            Box(Modifier.fillMaxWidth().heightIn(min = 240.dp).weight(1f).background(Color(0xFF161B23)), contentAlignment = Alignment.Center) {
                if (!frame.waitingForFix) {
                    VenueMap(frame = frame, me = me, members = members, nowMillis = now, modifier = Modifier.fillMaxSize(), pins = meetPins, headingDegrees = heading?.degrees)
                } else {
                    Text(stringResource(R.string.map_waiting_fix_body), Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFECE6DA))
                }
                Column(Modifier.align(Alignment.TopStart).padding(8.dp)) {
                    Text(
                        caption,
                        Modifier.background(Color(0x99161B23), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall, color = Color(0xFFECE6DA),
                    )
                    heading?.let { h ->
                        if (h.unreliable) {
                            Text(
                                stringResource(R.string.compass_hint),
                                Modifier.padding(top = 4.dp).background(Color(0xCC5C1B1B), CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFDAD6),
                            )
                        }
                    }
                }
            }
            StatusRow(
                radio = radio, meFixAgeMillis = me?.let { now - it.timeMillis }, accuracy = me?.accuracyMetres,
                nearby = members.count { now - it.lastSeen <= 60_000L }, myId = SenderId.hex(session.senderId),
            )
            HorizontalDivider()
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp)) {
                if (members.isEmpty()) {
                    item {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.map_no_members_title), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.map_no_members), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                    }
                }
                items(members, key = { it.senderId }) { m ->
                    val myFix = me
                    val label = m.name ?: SenderId.hex(m.senderId)
                    val dist = if (myFix != null && m.lat != null && m.lon != null) GeoMath.distanceMetres(myFix.lat, myFix.lon, m.lat, m.lon) else null
                    val bearing = if (dist != null) GeoMath.bearingDegrees(myFix!!.lat, myFix.lon, m.lat!!, m.lon!!) else null
                    val fresh = now - m.lastSeen <= 60_000L
                    ListItem(
                        leadingContent = {
                            Box(
                                Modifier.size(40.dp).background(if (fresh) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { Text(label.take(1).uppercase(), color = if (fresh) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) }
                        },
                        headlineContent = { Text(label, fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Text(
                                listOfNotNull(
                                    Format.age(now, m.lastSeen),
                                    if (m.hops > 0) stringResource(R.string.member_hops, m.hops) else stringResource(R.string.member_direct),
                                    lighthouses[m.senderId]?.let { "🔦 " + stringResource(R.string.lighthouse_member, it) },
                                ).joinToString(" · "),
                            )
                        },
                        trailingContent = {
                            Text(
                                if (dist != null) "${Format.distance(dist)} ${Format.directionArrow(bearing, heading?.degrees)}" else stringResource(R.string.member_no_position),
                                style = if (dist != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelSmall,
                                color = if (fresh) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                        },
                        modifier = Modifier.clickable { sheetFor = Recipient(m.senderId, label) },
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

@Composable
private fun FixCard(title: String, body: String, action: String, onAction: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Button(onClick = onAction) { Text(action) }
        }
    }
}
