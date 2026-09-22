package com.firefly.app.ui.codebook

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.firefly.app.R
import com.firefly.app.core.protocol.Codebook
import com.firefly.app.core.protocol.Protocol
import com.firefly.app.venue.Poi

/** A pickable recipient: the group or one member. */
data class Recipient(val senderId: Int, val label: String) {
    companion object {
        fun everyone(label: String) = Recipient(Protocol.TARGET_BROADCAST, label)
    }
}

/** Grouping for the sheet. Order matters: what people need most in a crowd comes first. */
private data class Section(val titleRes: Int, val codes: List<Int>)

private val sections = listOf(
    Section(R.string.codebook_section_find, listOf(Codebook.WHERE_ARE_YOU, Codebook.MEET_AT, Codebook.ON_MY_WAY, Codebook.STAY_THERE)),
    Section(R.string.codebook_section_plans, listOf(Codebook.GOING_TO, Codebook.BACK_IN_MINUTES, Codebook.CALL_ME, Codebook.LEAVING_VENUE)),
    Section(R.string.codebook_section_other, listOf(Codebook.LIGHTHOUSE_ON, Codebook.LOW_BATTERY)),
)

/**
 * The codebook (PRD §5 screen 4). Two taps from the map for any message
 * without an argument: button → message. Messages with an argument add one picker step.
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

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.codebook_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))

            // Recipient
            Text(stringResource(R.string.codebook_to), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                items(recipients, key = { it.senderId }) { r ->
                    val selected = r.senderId == recipient.senderId
                    FilterChip(
                        selected = selected,
                        onClick = { recipient = r },
                        label = { Text(r.label) },
                        leadingIcon = {
                            Box(
                                Modifier.size(20.dp).background(if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (r.senderId == Protocol.TARGET_BROADCAST) "★" else r.label.take(1).uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            val code = pendingCode
            if (code == null) {
                sections.forEach { section ->
                    SectionTitle(stringResource(section.titleRes))
                    section.codes.chunked(2).forEach { pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            pair.forEach { c ->
                                val entry = Codebook.entry(c)!!
                                MessageTile(
                                    code = c,
                                    hasStep = entry.arg != Codebook.ArgKind.NONE,
                                    modifier = Modifier.weight(1f),
                                    onClick = { if (entry.arg == Codebook.ArgKind.NONE) onSend(c, 0, recipient.senderId) else pendingCode = c },
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                }

                // Emergency, kept visually apart and long-press only (PRD F1).
                SectionTitle(stringResource(R.string.codebook_section_emergency))
                Surface(
                    color = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(64.dp).combinedClickable(
                        onClick = { sosHint = true },
                        onLongClick = { onSend(Codebook.HELP, 0, Protocol.TARGET_BROADCAST) },
                    ),
                ) {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(PingText.glyph(Codebook.HELP), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(PingText.labelRes(Codebook.HELP)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.codebook_hold), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (sosHint) {
                    Text(stringResource(R.string.codebook_sos_hint), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
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
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
    )
}

/** One message: glyph on the left, label, and a small chevron when a second step follows. */
@Composable
private fun MessageTile(code: Int, hasStep: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.height(64.dp).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(PingText.glyph(code), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(PingText.labelRes(code)),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (hasStep) Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun ArgPicker(code: Int, pois: List<Poi>, onPick: (Int) -> Unit, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.codebook_back)) }
        Text(PingText.glyph(code) + "  " + stringResource(PingText.labelRes(code)), style = MaterialTheme.typography.titleMedium)
    }
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    when (Codebook.entry(code)?.arg) {
        Codebook.ArgKind.MINUTES -> {
            Text(stringResource(R.string.codebook_minutes_title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(5, 10, 15, 30, 60).forEach { m ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f).height(64.dp).clickable { onPick(m) },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$m", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.codebook_minutes_hint), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        Codebook.ArgKind.POI -> {
            LazyColumn(Modifier.heightIn(max = 380.dp)) {
                item {
                    ListItem(
                        leadingContent = { Text("📍", style = MaterialTheme.typography.titleLarge) },
                        headlineContent = { Text(stringResource(R.string.codebook_here), fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(stringResource(R.string.codebook_here_hint)) },
                        modifier = Modifier.clickable { onPick(Codebook.POI_HERE) },
                    )
                }
                if (pois.isEmpty()) {
                    item { Text(stringResource(R.string.codebook_no_pois), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start) }
                }
                items(pois, key = { it.index }) { poi ->
                    ListItem(
                        leadingContent = { Text("${poi.index}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        headlineContent = { Text(poi.name) },
                        modifier = Modifier.clickable { onPick(poi.index) },
                    )
                }
            }
        }
        else -> Spacer(Modifier.height(0.dp))
    }
}
