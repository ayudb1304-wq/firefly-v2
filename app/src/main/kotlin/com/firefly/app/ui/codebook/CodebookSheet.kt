package com.firefly.app.ui.codebook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.firefly.app.R
import com.firefly.app.core.protocol.Codebook
import com.firefly.app.core.protocol.Protocol
import com.firefly.app.venue.Poi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape

/** A pickable recipient: the group or one member. */
data class Recipient(val senderId: Int, val label: String) {
    companion object {
        fun everyone(label: String) = Recipient(Protocol.TARGET_BROADCAST, label)
    }
}

/**
 * The 12-button grid (PRD §5 screen 4). Two taps from the map for any message
 * without an argument: FAB → button. Messages with an argument add one picker step.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CodebookSheet(
    recipients: List<Recipient>,
    initialRecipient: Recipient,
    pois: List<Poi>,
    onSend: (code: Int, arg: Int, target: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var recipient by remember { mutableStateOf(initialRecipient) }
    var pendingCode by remember { mutableStateOf<Int?>(null) }
    var sosHint by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.codebook_to), style = MaterialTheme.typography.labelLarge)
            if (sosHint) Text(stringResource(R.string.codebook_sos_hint), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                items(recipients, key = { it.senderId }) { r ->
                    FilterChip(selected = r.senderId == recipient.senderId, onClick = { recipient = r }, label = { Text(r.label) })
                }
            }
            val code = pendingCode
            if (code == null) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 420.dp),
                ) {
                    items(Codebook.userSendable, key = { it.code }) { entry ->
                        if (entry.priority) {
                            // PRD F1: SOS needs a long-press so it cannot be sent by accident.
                            Surface(
                                color = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxWidth().height(64.dp).combinedClickable(
                                    onClick = { sosHint = true },
                                    onLongClick = { onSend(entry.code, 0, Protocol.TARGET_BROADCAST) },
                                ),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(stringResource(PingText.labelRes(entry.code)), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
                                        Text(stringResource(R.string.codebook_hold), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (entry.arg == Codebook.ArgKind.NONE) onSend(entry.code, 0, recipient.senderId) else pendingCode = entry.code
                                },
                                modifier = Modifier.fillMaxWidth().height(64.dp),
                            ) {
                                Text(stringResource(PingText.labelRes(entry.code)), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            } else {
                ArgPicker(
                    code = code,
                    pois = pois,
                    onPick = { arg -> onSend(code, arg, recipient.senderId) },
                    onBack = { pendingCode = null },
                )
            }
        }
    }
}

@Composable
private fun ArgPicker(code: Int, pois: List<Poi>, onPick: (Int) -> Unit, onBack: () -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.codebook_back)) }
        Text(stringResource(PingText.labelRes(code)), style = MaterialTheme.typography.titleMedium)
    }
    when (Codebook.entry(code)?.arg) {
        Codebook.ArgKind.MINUTES -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                listOf(5, 10, 15, 30, 60).forEach { m ->
                    Button(onClick = { onPick(m) }, modifier = Modifier.height(56.dp)) { Text("$m") }
                }
            }
            Text(stringResource(R.string.codebook_minutes_hint), style = MaterialTheme.typography.bodySmall)
        }
        Codebook.ArgKind.POI -> {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.codebook_here)) },
                        supportingContent = { Text(stringResource(R.string.codebook_here_hint)) },
                        modifier = Modifier.clickable { onPick(Codebook.POI_HERE) },
                    )
                }
                if (pois.isEmpty()) {
                    item { Text(stringResource(R.string.codebook_no_pois), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall) }
                }
                items(pois, key = { it.index }) { poi ->
                    ListItem(headlineContent = { Text(poi.name) }, modifier = Modifier.clickable { onPick(poi.index) })
                }
            }
        }
        else -> Spacer(Modifier.height(0.dp))
    }
}
