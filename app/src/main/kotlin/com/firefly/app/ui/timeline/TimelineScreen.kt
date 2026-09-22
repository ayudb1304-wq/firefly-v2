package com.firefly.app.ui.timeline

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.firefly.app.R
import com.firefly.app.core.group.SenderId
import com.firefly.app.core.protocol.Protocol
import com.firefly.app.data.db.PingEntity
import com.firefly.app.ui.codebook.PingText
import com.firefly.app.ui.common.Format
import com.firefly.app.venue.Poi

/** Sent and received pings in order (PRD §5 screen 5). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    pings: List<PingEntity>,
    names: Map<Int, String>,
    pois: List<Poi>,
    nowMillis: Long,
    onReply: (senderId: Int) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.timeline_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (pings.isEmpty()) {
                item { Text(stringResource(R.string.timeline_empty), Modifier.padding(24.dp), style = MaterialTheme.typography.bodyMedium) }
            }
            items(pings, key = { it.id }) { p ->
                val incoming = p.direction == PingEntity.IN
                val other = if (incoming) p.senderId else p.target
                val otherName = if (other == Protocol.TARGET_BROADCAST) stringResource(R.string.everyone) else names[other] ?: SenderId.hex(other)
                val poiName = pois.firstOrNull { it.index == p.arg }?.name
                val status = when {
                    incoming -> if (p.hops > 0) stringResource(R.string.timeline_via_hops, p.hops) else ""
                    p.status == PingEntity.SEEN -> stringResource(R.string.timeline_seen, p.ackCount)
                    else -> stringResource(R.string.timeline_sent)
                }
                ListItem(
                    overlineContent = { Text(if (incoming) stringResource(R.string.timeline_from, otherName) else stringResource(R.string.timeline_to, otherName)) },
                    headlineContent = { Text(PingText.describe(context, p.code, p.arg, poiName)) },
                    supportingContent = { Text(listOf(Format.age(nowMillis, p.ts), status).filter { it.isNotEmpty() }.joinToString(" · ")) },
                    leadingContent = { Text(if (incoming) "↓" else "↑", style = MaterialTheme.typography.titleLarge) },
                    modifier = if (incoming) Modifier.clickable { onReply(p.senderId) } else Modifier,
                )
            }
        }
    }
}
