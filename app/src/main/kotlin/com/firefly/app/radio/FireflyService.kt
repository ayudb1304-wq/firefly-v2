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
import com.firefly.app.core.protocol.AccuracyBucket
import com.firefly.app.core.protocol.BeaconPolicy
import com.firefly.app.core.protocol.Packet
import com.firefly.app.core.protocol.PacketCodec
import com.firefly.app.core.protocol.Protocol
import com.firefly.app.core.protocol.toHexSpaced
import com.firefly.app.core.routing.DedupeCache
import com.firefly.app.data.repo.GroupSession
import com.firefly.app.location.Fix
import com.firefly.app.ui.common.Permissions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Foreground service that owns the radio (ARCHITECTURE.md §4, §6).
 * Advertises our own BEACON, scans for the group's packets, updates members.
 * Relaying arrives in Phase 3; the dedupe step already runs before anything else.
 */
class FireflyService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val container get() = (application as FireflyApp).container
    private val status get() = container.radioStatus

    private var adapter: BluetoothAdapter? = null
    private var advertiser: Advertiser? = null
    private var scanner: Scanner? = null
    private lateinit var battery: BatteryLogger
    private val dedupe = DedupeCache()
    private val inbound = Channel<RawAdvert>(capacity = 256, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)

    private var session: GroupSession? = null
    /** Per-sender sequence. Starts random so a restart within the dedupe TTL does not collide (DECISIONS.md). */
    private var seq = Random.nextInt(0, 0x10000)

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
        // startRadio() already put the first beacon on air.
        status.update { it.copy(packetsSent = it.packetsSent + 1) }
        while (scope.isActive) {
            val fix = container.locationSource.fixes.value
            delay(BeaconPolicy.nextIntervalMillis(fix?.speedMps, AccuracyBucket.fromMetres(fix?.accuracyMetres)))
            advertiser?.let { adv ->
                adv.update(currentBeacon())
                status.update { it.copy(packetsSent = it.packetsSent + 1) }
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
            seq = nextSeq(),
            ttl = Protocol.Ttl.DEFAULT,
            hops = 0,
            latE6 = fix?.let { Packet.toE6(it.lat) } ?: 0,
            lonE6 = fix?.let { Packet.toE6(it.lon) } ?: 0,
            accuracyBucket = AccuracyBucket.fromMetres(fix?.accuracyMetres),
            ts = Packet.tsByte(System.currentTimeMillis()),
        )
        return PacketCodec.encode(packet)
    }

    private fun nextSeq(): Int { seq = (seq + 1) and 0xFFFF; return seq }

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
                "RX type=${packet.type} from=${SenderId.hex(packet.senderId)} seq=${packet.seq} hops=${packet.hops} ttl=${packet.ttl} " +
                    "rssi=${raw.rssi} acc=${packet.accuracyBucket} pos=${if (packet.hasPosition) "${packet.latitude},${packet.longitude}" else "-"}",
            )
            if (packet.senderId == s.senderId) continue
            container.memberRepository.onPacket(packet, raw.rssi, now)
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

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, FireflyService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, FireflyService::class.java).setAction(ACTION_STOP))
        }
    }
}
