package com.firefly.app.ui.common

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * OEM battery killers (TESTING.md "Known device gotchas"). Detects the maker and
 * offers the deepest settings screen we can open without special permissions.
 */
object OemBattery {
    data class Advice(val maker: String, val steps: List<String>, val intents: List<Intent>)

    private val maker: String get() = Build.MANUFACTURER.lowercase()

    fun advice(context: Context): Advice {
        val pkg = context.packageName
        val generic = listOf(
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)),
        )
        return when {
            maker.contains("xiaomi") || maker.contains("redmi") || maker.contains("poco") -> Advice(
                "Xiaomi / Redmi / POCO",
                listOf(
                    "Settings → Apps → Manage apps → Firefly → Battery saver → No restrictions",
                    "Same screen → Autostart → On",
                    "Recents: long-press Firefly → lock (padlock) so it is not swiped away",
                ),
                listOf(
                    Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
                    Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")).putExtra("package_name", pkg),
                ) + generic,
            )
            maker.contains("vivo") || maker.contains("iqoo") -> Advice(
                "Vivo / iQOO",
                listOf(
                    "Settings → Battery → Background power consumption management → Firefly → Allow",
                    "i Manager → App manager → Autostart → Firefly → On",
                    "If prompted \"high background power consumption\", choose Allow",
                ),
                listOf(
                    Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
                    Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
                ) + generic,
            )
            maker.contains("oppo") || maker.contains("realme") || maker.contains("oneplus") -> Advice(
                "OPPO / Realme / OnePlus",
                listOf(
                    "Settings → Battery → More settings → Optimise battery use → All apps → Firefly → Don't optimise",
                    "Settings → Apps → Firefly → Battery → Allow background activity and Auto-launch",
                    "Recents: lock Firefly so it is not cleared",
                ),
                listOf(
                    Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
                    Intent().setComponent(ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")),
                ) + generic,
            )
            maker.contains("samsung") -> Advice(
                "Samsung",
                listOf(
                    "Settings → Apps → Firefly → Battery → Unrestricted",
                    "Settings → Battery → Background usage limits → make sure Firefly is not in Sleeping or Deep sleeping apps",
                    "Turn off Adaptive battery for the festival day",
                ),
                listOf(
                    Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")),
                ) + generic,
            )
            maker.contains("huawei") || maker.contains("honor") -> Advice(
                "Huawei / Honor",
                listOf("Settings → Battery → App launch → Firefly → Manage manually → all three on"),
                listOf(Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"))) + generic,
            )
            else -> Advice(
                Build.MANUFACTURER,
                listOf("Settings → Apps → Firefly → Battery → Unrestricted (or \"Don't optimise\")"),
                generic,
            )
        }
    }

    /** Open the first settings screen that exists on this device. */
    fun open(context: Context, advice: Advice): Boolean {
        for (intent in advice.intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (_: Exception) { /* not on this device */ }
        }
        return false
    }
}
