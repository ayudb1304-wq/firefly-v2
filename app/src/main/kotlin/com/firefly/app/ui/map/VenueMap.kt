package com.firefly.app.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firefly.app.core.geo.FreeMap
import com.firefly.app.core.geo.MapProjection
import com.firefly.app.core.group.SenderId
import com.firefly.app.core.protocol.AccuracyBucket
import com.firefly.app.data.db.MemberEntity
import com.firefly.app.location.Fix
import com.firefly.app.ui.common.Format
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

private val MeColor = Color(0xFF4FC3F7)
private val MapBackground = Color(0xFF161B23)
private val GridMinor = Color(0xFF272E3A)
private val GridMajor = Color(0xFF3A4453)
private val OnMap = Color(0xFFECE6DA)
private const val STALE_AFTER_MS = 60_000L

/**
 * The map: a venue image when one is loaded and I am on it, otherwise a
 * north-up metric grid centred near me (PRD B2). Fit-to-box, no pan/zoom yet.
 */
@Composable
fun VenueMap(
    frame: MapFrame,
    me: Fix?,
    members: List<MemberEntity>,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    val image = remember(frame.venue) { frame.venue?.image?.asImageBitmap() }
    val measurer = rememberTextMeasurer()
    val scheme = MaterialTheme.colorScheme
    val labelStyle = TextStyle(fontSize = 11.sp, color = OnMap, fontWeight = FontWeight.Medium)
    val poiStyle = TextStyle(fontSize = 10.sp, color = OnMap.copy(alpha = 0.75f))
    val hudStyle = TextStyle(fontSize = 10.sp, color = OnMap.copy(alpha = 0.8f))
    Canvas(modifier) {
        val centre = frame.centre
        val projection: MapProjection = frame.projection
            ?: if (centre != null && frame.widthMetres != null && size.width > 0 && size.height > 0) {
                MapProjection.centredOn(centre.lat, centre.lon, frame.widthMetres, size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1))
            } else return@Canvas
        drawRect(MapBackground)
        val scale = min(size.width / projection.imageWidth, size.height / projection.imageHeight)
        val drawnW = projection.imageWidth * scale
        val drawnH = projection.imageHeight * scale
        val origin = Offset((size.width - drawnW) / 2f, (size.height - drawnH) / 2f)
        val pxPerMetre = (projection.pixelsPerMetre() * scale).toFloat()

        if (image != null) {
            drawImage(image, dstOffset = IntOffset(origin.x.roundToInt(), origin.y.roundToInt()), dstSize = IntSize(drawnW.roundToInt(), drawnH.roundToInt()))
        } else {
            drawRect(MapBackground, topLeft = origin, size = Size(drawnW, drawnH))
            drawGrid(origin, drawnW, drawnH, pxPerMetre, measurer, hudStyle)
        }
        drawRect(Color(0xFF8A6100), topLeft = origin, size = Size(drawnW, drawnH), style = Stroke(1.dp.toPx()))

        fun at(lat: Double, lon: Double): Offset {
            val p = projection.toPixel(lat, lon)
            return Offset(origin.x + (p.x * scale).toFloat(), origin.y + (p.y * scale).toFloat())
        }

        frame.venue?.pois?.forEach { poi ->
            if (!projection.contains(poi.lat, poi.lon)) return@forEach
            val o = at(poi.lat, poi.lon)
            drawCircle(OnMap.copy(alpha = 0.6f), radius = 3.dp.toPx(), center = o)
            drawText(measurer, poi.name, topLeft = o + Offset(5.dp.toPx(), -7.dp.toPx()), style = poiStyle)
        }

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

        me?.let { f ->
            if (!projection.contains(f.lat, f.lon)) return@let
            val o = at(f.lat, f.lon)
            val ring = (f.accuracyMetres ?: 0f) * pxPerMetre
            if (ring > 0f) drawCircle(MeColor.copy(alpha = 0.15f), radius = ring, center = o)
            drawCircle(Color.White, radius = 9.dp.toPx(), center = o)
            drawCircle(MeColor, radius = 7.dp.toPx(), center = o)
        }

        // North arrow (both modes are north-up).
        val n = origin + Offset(drawnW - 16.dp.toPx(), 18.dp.toPx())
        drawLine(OnMap, n + Offset(0f, 8.dp.toPx()), n + Offset(0f, -8.dp.toPx()), strokeWidth = 2.dp.toPx())
        drawLine(OnMap, n + Offset(-4.dp.toPx(), -3.dp.toPx()), n + Offset(0f, -8.dp.toPx()), strokeWidth = 2.dp.toPx())
        drawLine(OnMap, n + Offset(4.dp.toPx(), -3.dp.toPx()), n + Offset(0f, -8.dp.toPx()), strokeWidth = 2.dp.toPx())
        drawText(measurer, "N", topLeft = n + Offset(-4.dp.toPx(), 9.dp.toPx()), style = hudStyle)
    }
}

/** Metric grid plus a scale bar, so distances are readable without a venue image. */
private fun DrawScope.drawGrid(origin: Offset, w: Float, h: Float, pxPerMetre: Float, measurer: TextMeasurer, style: TextStyle) {
    if (pxPerMetre <= 0f) return
    val stepM = FreeMap.gridSpacingMetres(pxPerMetre.toDouble(), 56.dp.toPx().toDouble())
    val stepPx = (stepM * pxPerMetre).toFloat()
    val cx = origin.x + w / 2
    val cy = origin.y + h / 2
    val nx = ceil(w / 2 / stepPx).toInt()
    val ny = ceil(h / 2 / stepPx).toInt()
    for (i in -nx..nx) {
        val x = cx + i * stepPx
        if (x < origin.x || x > origin.x + w) continue
        drawLine(if (i % 5 == 0) GridMajor else GridMinor, Offset(x, origin.y), Offset(x, origin.y + h), strokeWidth = 1f)
    }
    for (j in -ny..ny) {
        val y = cy + j * stepPx
        if (y < origin.y || y > origin.y + h) continue
        drawLine(if (j % 5 == 0) GridMajor else GridMinor, Offset(origin.x, y), Offset(origin.x + w, y), strokeWidth = 1f)
    }
    // Scale bar, bottom-left.
    val barY = origin.y + h - 14.dp.toPx()
    val barX = origin.x + 12.dp.toPx()
    drawLine(OnMap, Offset(barX, barY), Offset(barX + stepPx, barY), strokeWidth = 2.dp.toPx())
    drawLine(OnMap, Offset(barX, barY - 4.dp.toPx()), Offset(barX, barY + 4.dp.toPx()), strokeWidth = 2.dp.toPx())
    drawLine(OnMap, Offset(barX + stepPx, barY - 4.dp.toPx()), Offset(barX + stepPx, barY + 4.dp.toPx()), strokeWidth = 2.dp.toPx())
    val label = if (stepM >= 1000) "${(stepM / 1000).toInt()} km" else "${stepM.toInt()} m"
    drawText(measurer, label, topLeft = Offset(barX, barY - 18.dp.toPx()), style = style)
}
