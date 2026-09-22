package com.firefly.app.data.repo

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.firefly.app.core.protocol.Packet
import com.firefly.app.core.protocol.toHexSpaced
import com.firefly.app.core.util.Csv
import com.firefly.app.data.db.PacketLogDao
import com.firefly.app.data.db.PacketLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Field-test log: every radio event, batched into Room off the hot path,
 * capped as a ring buffer, exportable as CSV through the share sheet.
 */
class FieldLogRepository(
    private val context: Context,
    private val dao: PacketLogDao,
    scope: CoroutineScope,
) {
    private val queue = Channel<PacketLogEntity>(capacity = 4096, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    @Volatile var enabled: Boolean = true

    val count: Flow<Int> = dao.observeCount()
    fun uniqueSendersSince(sinceTs: Long): Flow<Int> = dao.observeUniqueSenders(sinceTs)

    init {
        scope.launch(Dispatchers.IO) { writer() }
    }

    // ---- recording ----

    fun packet(event: String, p: Packet, rssi: Int? = null, bytes: ByteArray? = null, ts: Long = System.currentTimeMillis()) {
        if (!enabled) return
        queue.trySend(
            PacketLogEntity(
                ts = ts, event = event, type = p.type, senderId = p.senderId, seq = p.seq, hops = p.hops, ttl = p.ttl,
                target = p.target, code = p.code, arg = p.arg, rssi = rssi, latE6 = p.latE6, lonE6 = p.lonE6,
                bytesHex = bytes?.toHexSpaced(),
            ),
        )
    }

    fun raw(event: String, bytes: ByteArray, rssi: Int?, ts: Long = System.currentTimeMillis()) {
        if (!enabled) return
        queue.trySend(PacketLogEntity(ts = ts, event = event, rssi = rssi, bytesHex = bytes.toHexSpaced()))
    }

    fun battery(level: Int, charging: Boolean, drainPerHour: Double?) {
        if (!enabled) return
        queue.trySend(PacketLogEntity(ts = System.currentTimeMillis(), event = PacketLogEntity.BATTERY, extra = "level=$level charging=$charging drain=${drainPerHour?.let { "%.2f".format(it) } ?: "-"}"))
    }

    fun event(text: String) {
        queue.trySend(PacketLogEntity(ts = System.currentTimeMillis(), event = PacketLogEntity.EVENT, extra = text))
    }

    private suspend fun writer() {
        var sinceTrim = 0
        while (true) {
            val batch = ArrayList<PacketLogEntity>(64)
            batch += queue.receive()
            withTimeoutOrNull(FLUSH_MS) { while (batch.size < 256) batch += queue.receive() }
            runCatching { dao.insertAll(batch) }.onFailure { Log.w(TAG, "log insert failed", it) }
            sinceTrim += batch.size
            if (sinceTrim >= TRIM_EVERY) {
                sinceTrim = 0
                val over = dao.count() - MAX_ROWS
                if (over > 0) dao.deleteOldest(over + TRIM_SLACK)
            }
        }
    }

    // ---- export ----

    /** Write the whole log to a CSV in the app cache and return a shareable content URI. */
    suspend fun export(groupCode: String?, senderIdHex: String, appVersion: String, settingsSummary: String): android.net.Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 3_600_000L }?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val device = (Build.MANUFACTURER + "_" + Build.MODEL).replace(Regex("[^A-Za-z0-9_-]"), "")
        val file = File(dir, "firefly_${groupCode ?: "nogroup"}_${senderIdHex}_${device}_$stamp.csv")
        file.bufferedWriter().use { w ->
            w.write("# firefly field log v1 · app=$appVersion · device=${Build.MANUFACTURER} ${Build.MODEL} · android=${Build.VERSION.RELEASE} · me=$senderIdHex · group=${groupCode ?: "-"} · $settingsSummary\n")
            w.write("ts_ms,iso,event,type,sender,seq,hops,ttl,target,code,arg,rssi,lat,lon,bytes,extra\n")
            val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US)
            var after = 0L
            while (true) {
                val page = dao.page(after, 2_000)
                if (page.isEmpty()) break
                for (r in page) {
                    w.write(
                        Csv.row(
                            r.ts, iso.format(Date(r.ts)), r.event, r.type, r.senderId?.let { "%04X".format(it) }, r.seq, r.hops, r.ttl,
                            r.target?.let { "%04X".format(it) }, r.code, r.arg, r.rssi,
                            r.latE6?.takeIf { it != 0 }?.let { it / 1e6 }, r.lonE6?.takeIf { it != 0 }?.let { it / 1e6 }, r.bytesHex, r.extra,
                        ),
                    )
                    w.write("\n")
                }
                after = page.last().id
            }
        }
        FileProvider.getUriForFile(context, context.packageName + ".files", file)
    }

    suspend fun clear() = dao.clear()

    companion object {
        private const val TAG = "Firefly/Log"
        const val MAX_ROWS = 50_000
        private const val TRIM_EVERY = 500
        private const val TRIM_SLACK = 1_000
        private const val FLUSH_MS = 2_000L
    }
}
