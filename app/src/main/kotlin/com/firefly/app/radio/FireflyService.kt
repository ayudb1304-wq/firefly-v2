package com.firefly.app.radio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.firefly.app.FireflyApp
import com.firefly.app.MainActivity
import com.firefly.app.R
import com.firefly.app.core.group.SenderId
import com.firefly.app.core.messaging.NamePolicy
import com.firefly.app.core.protocol.AccuracyBucket
import com.firefly.app.core.protocol.BeaconPolicy
import com.firefly.app.core.protocol.Packet
import com.firefly.app.core.protocol.PacketCodec
import com.firefly.app.core.protocol.Protocol
import com.firefly.app.core.protocol.toHexSpaced
import com.firefly.app.core.routing.DedupeCache
import com.firefly.app.data.repo.GroupSession
import com.firefly.app.location.Fix
import com.firefly.app.ui.codebook.PingText
import com.firefly.app.ui.common.Permissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/**
 * Foreground service that owns the radio (ARCHITECTURE.md §4, §6).
 * Advertises our own BEACON, bursts queued pings/ACKs/names, scans for the
 * group's packets, updates members and the timeline. Relaying arrives in Phase 3;
 * the dedupe step already runs before anything else.
 */
class FireflyService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val app get() = application as FireflyApp
    private val container get() = app.container
    private val status get() = container.radioStatus

    private var adapter: BluetoothAdapter? = null
    private var advertiser: Advertiser? = null
    private var scanner: Scanner? = null
    private lateinit var battery: BatteryLogger
    private lateinit var alerts: Alerts
    private val dedupe = DedupeCache()
    private val inbound = Channel<RawAdvert>(capacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Only one thing is on air at a time: own beacon updates wait for bursts to finish. */
    private val airtime = Mutex()

    private var session: GroupSession? = null

    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> { Log.i(TAG, "Bluetooth on — restarting radio"); startRadio() }
                BluetoothAdapter.STATE_OFF -> { Log.w(TAG, "Bluetooth off — degraded"); stopRadio(); degrade("Bluetooth is off") }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        battery = BatteryLogger(this)
        alerts = Alerts(this)
        adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        ContextCompat.registerReceiver(this, btReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf(); return START_NOT_STICKY
        }
        if (!Permissions.allGranted(this)) {
            Log.w(TAG, "permissions missing; not starting")
            stopSelf(); return START_NOT_STICKY
        }
        startInForeground()
        if (session == null) scope.launch { boot() }
        return START_STICKY
    }

    private suspend fun boot() {
        val s = container.groupRepository.session.first()
        if (s == null) {
            Log.w(TAG, "no group session; stopping"); stopSelf(); return
        }
        session = s
        Log.i(TAG, "group=${s.code} groupId=${"%08X".format(s.groupId)} me=${SenderId.hex(s.senderId)}")
        status.update { it.copy(running = true, degradedReason = null) }
        container.locationSource.start()
        battery.start(scope)
        startRadio()
        scope.launch { pipeline() }
        scope.launch { beaconLoop() }
        scope.launch { txLoop() }
        scope.launch { nameLoop() }
    }

    private fun startRadio() {
        val bt = adapter
        if (bt == null || !bt.isEnabled) { degrade("Bluetooth is off"); return }
        status.update { it.copy(degradedReason = null) }
        if (advertiser == null) {
            advertiser = Advertiser(bt) { on, err -> status.update { it.copy(advertising = on, degradedReason = err ?: it.degradedReason) } }
        }
        if (scanner == null) {
            scanner = Scanner(bt, onAdvert = { inbound.trySend(it) }) { on, err ->
                status.update { it.copy(scanning = on, degradedReason = err ?: it.degradedReason) }
            }
        }
        advertiser?.start(currentBeacon())
        scanner?.startCycling(scope)
    }

    private fun stopRadio() {
        advertiser?.stop(); advertiser = null
        scanner?.stop(); scanner = null
    }

    private fun degrade(reason: String) = status.update { it.copy(advertising = false, scanning = false, degradedReason = reason) }

    // ---------------- outbound ----------------

    private suspend fun beaconLoop() {
        status.update { it.copy(packetsSent = it.packetsSent + 1) } // the beacon startRadio() put on air
        while (scope.isActive) {
            val fix = container.locationSource.fixes.value
            delay(BeaconPolicy.nextIntervalMillis(fix?.speedMps, AccuracyBucket.fromMetres(fix?.accuracyMetres)))
            val adv = advertiser ?: continue
            airtime.withLock {
                adv.update(currentBeacon())
                status.update { it.copy(packetsSent = it.packetsSent + 1) }
            }
        }
    }

    /** Drain the transmit queue: priority lane first, each request as N bursts of [BURST_MS]. */
    private suspend fun txLoop() {
        val q = container.txQueue
        while (scope.isActive) {
            val req = q.priority.tryReceive().getOrNull() ?: select<TxRequest> {
                q.priority.onReceive { it }
                q.normal.onReceive { it }
            }
            for (i in 0 until req.repeats) {
                val adv = advertiser
                if (adv == null) { Log.w(TAG, "radio down, dropping ${req.label}"); break }
                airtime.withLock {
                    adv.update(req.bytes)
                    status.update { it.copy(packetsSent = it.packetsSent + 1) }
                    delay(BURST_MS)
                    adv.update(currentBeacon())
                }
                if (i < req.repeats - 1) delay((req.spacingMillis - BURST_MS).coerceAtLeast(0))
            }
        }
    }

    /** NAME packets on the PRD A2 schedule whenever a display name is set. */
    private suspend fun nameLoop() {
        container.settings.displayName.collectLatest { raw ->
            val name = NamePolicy.sanitise(raw.orEmpty())
            if (name.isEmpty()) return@collectLatest
            while (scope.isActive) {
                container.pingRepository.sendName(name)
                val joined = container.settings.joinedAt.first() ?: System.currentTimeMillis()
                delay(NamePolicy.intervalMillis(System.currentTimeMillis() - joined))
            }
        }
    }

    private fun currentBeacon(): ByteArray {
        val s = session ?: error("no session")
        val fix: Fix? = container.locationSource.fixes.value
        val packet = Packet(
            type = Protocol.Type.BEACON,
            groupId = s.groupId,
            senderId = s.senderId,
            seq = container.seqCounter.next(),
            ttl = Protocol.Ttl.DEFAULT,
            hops = 0,
            latE6 = fix?.let { Packet.toE6(it.lat) } ?: 0,
            lonE6 = fix?.let { Packet.toE6(it.lon) } ?: 0,
            accuracyBucket = AccuracyBucket.fromMetres(fix?.accuracyMetres),
            ts = Packet.tsByte(System.currentTimeMillis()),
        )
        return PacketCodec.encode(packet)
    }

    // ---------------- inbound ----------------

    private suspend fun pipeline() {
        val s = session ?: return
        for (raw in inbound) {
            val now = System.currentTimeMillis()
            val packet = PacketCodec.decode(raw.bytes)
            if (packet == null) {
                Log.v(TAG, "RX undecodable ${raw.bytes.toHexSpaced()}"); continue
            }
            if (packet.groupId != s.groupId) {
                status.update { it.copy(packetsForeign = it.packetsForeign + 1) }; continue
            }
            // Dedupe before anything else (CLAUDE.md non-negotiable).
            if (!dedupe.checkAndInsert(packet.senderId, packet.seq, now)) {
                status.update { it.copy(packetsDeduped = it.packetsDeduped + 1) }; continue
            }
            status.update { it.copy(packetsReceived = it.packetsReceived + 1, lastPacketAtMillis = now) }
            Log.i(
                TAG,
                "RX type=${packet.type} code=${packet.code} from=${SenderId.hex(packet.senderId)} seq=${packet.seq} hops=${packet.hops} ttl=${packet.ttl} " +
                    "rssi=${raw.rssi} target=${"%04X".format(packet.target)} pos=${if (packet.hasPosition) "${packet.latitude},${packet.longitude}" else "-"}",
            )
            if (packet.senderId == s.senderId) continue
            container.memberRepository.onPacket(packet, raw.rssi, now)

            val forMe = packet.isBroadcast || packet.target == s.senderId
            when (packet.type) {
                Protocol.Type.PING -> if (forMe) onPing(packet, raw.rssi, now)
                Protocol.Type.ACK -> if (packet.target == s.senderId) container.pingRepository.onAck(packet, now)
            }
        }
    }

    private suspend fun onPing(packet: Packet, rssi: Int, now: Long) {
        val ping = container.pingRepository.onIncoming(packet, rssi, now)
        container.publishIncoming(ping)
        val senderName = container.memberRepository.name(packet.senderId) ?: SenderId.hex(packet.senderId)
        val poiName = container.venueRepository.venue.value?.pois?.firstOrNull { it.index == packet.arg }?.name
        alerts.onPing(ping, senderName, PingText.describe(this, packet.code, packet.arg, poiName), app.inForeground)
        if (packet.ackRequested) {
            // Jitter so several receivers of a broadcast do not ACK in the same instant.
            scope.launch {
                delay(Random.nextLong(ACK_JITTER_MIN_MS, ACK_JITTER_MAX_MS))
                container.pingRepository.sendAck(packet, System.currentTimeMillis())
            }
        }
    }

    // ---------------- lifecycle ----------------

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.notif_channel_desc)
            },
        )
        val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), types)
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setOngoing(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        Log.i(TAG, "stopping")
        runCatching { unregisterReceiver(btReceiver) }
        stopRadio()
        battery.stop()
        container.locationSource.stop()
        scope.cancel()
        status.reset()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "Firefly/Service"
        private const val CHANNEL_ID = "firefly_radio"
        private const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.firefly.app.action.STOP"

        /** How long a queued packet replaces the beacon on air per repeat (≈2–3 adverts at 250 ms). */
        const val BURST_MS = 600L
        const val ACK_JITTER_MIN_MS = 150L
        const val ACK_JITTER_MAX_MS = 700L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, FireflyService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, FireflyService::class.java).setAction(ACTION_STOP))
        }
    }
}
