package com.firefly.app.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firefly.app.core.geo.MapProjection
import com.firefly.app.core.group.SenderId
import com.firefly.app.core.protocol.AccuracyBucket
import com.firefly.app.data.db.MemberEntity
import com.firefly.app.location.Fix
import com.firefly.app.ui.common.Format
import com.firefly.app.venue.VenuePack
import kotlin.math.min
import kotlin.math.roundToInt

private val MeColor = Color(0xFF4FC3F7)
private const val STALE_AFTER_MS = 60_000L

/** Static venue image with POIs, group members and me (PRD B2). Fit-to-box, no pan/zoom in Phase 1. */
@Composable
fun VenueMap(
    venue: VenuePack,
    projection: MapProjection,
    me: Fix?,
    members: List<MemberEntity>,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    val image = remember(venue) { venue.image.asImageBitmap() }
    val measurer = rememberTextMeasurer()
    val scheme = MaterialTheme.colorScheme
    // The venue image is always dark, so map labels ignore the app theme.
    val onMap = Color(0xFFECE6DA)
    val labelStyle = TextStyle(fontSize = 11.sp, color = onMap, fontWeight = FontWeight.Medium)
    val poiStyle = TextStyle(fontSize = 10.sp, color = onMap.copy(alpha = 0.75f))

    Canvas(modifier) {
        val scale = min(size.width / image.width, size.height / image.height)
        val drawnW = image.width * scale
        val drawnH = image.height * scale
        val origin = Offset((size.width - drawnW) / 2f, (size.height - drawnH) / 2f)
        drawImage(
            image = image,
            dstOffset = IntOffset(origin.x.roundToInt(), origin.y.roundToInt()),
            dstSize = IntSize(drawnW.roundToInt(), drawnH.roundToInt()),
        )
        val pxPerMetre = (projection.pixelsPerMetre() * scale).toFloat()
        fun at(lat: Double, lon: Double): Offset {
            val p = projection.toPixel(lat, lon)
            return Offset(origin.x + (p.x * scale).toFloat(), origin.y + (p.y * scale).toFloat())
        }

        // POIs
        venue.pois.forEach { poi ->
            if (!projection.contains(poi.lat, poi.lon)) return@forEach
            val o = at(poi.lat, poi.lon)
            drawCircle(onMap.copy(alpha = 0.6f), radius = 3.dp.toPx(), center = o)
            drawText(measurer, poi.name, topLeft = o + Offset(5.dp.toPx(), -7.dp.toPx()), style = poiStyle)
        }

        // Members
        members.forEach { m ->
            val lat = m.lat ?: return@forEach
            val lon = m.lon ?: return@forEach
            if (!projection.contains(lat, lon)) return@forEach
            val o = at(lat, lon)
            val stale = nowMillis - m.lastSeen > STALE_AFTER_MS
            val alpha = if (stale) 0.45f else 1f
            val ring = AccuracyBucket.radiusMetres(m.accuracy) * pxPerMetre
            if (ring > 0f) drawCircle(scheme.primary.copy(alpha = 0.12f * alpha), radius = ring, center = o)
            if (stale) drawCircle(scheme.primary.copy(alpha = 0.6f), radius = 11.dp.toPx(), center = o, style = Stroke(1.5.dp.toPx()))
            drawCircle(scheme.primary.copy(alpha = alpha), radius = 7.dp.toPx(), center = o)
            val label = "${m.name ?: SenderId.hex(m.senderId)} · ${Format.age(nowMillis, m.lastSeen)}" + if (m.hops > 0) " · ${m.hops}h" else ""
            drawText(measurer, label, topLeft = o + Offset(10.dp.toPx(), -8.dp.toPx()), style = labelStyle)
        }

        // Me
        me?.let { f ->
            val o = at(f.lat, f.lon)
            val ring = (f.accuracyMetres ?: 0f) * pxPerMetre
            if (ring > 0f) drawCircle(MeColor.copy(alpha = 0.15f), radius = ring, center = o)
            drawCircle(Color.White, radius = 9.dp.toPx(), center = o)
            drawCircle(MeColor, radius = 7.dp.toPx(), center = o)
        }
    }
}
