package com.zyrotech.vsolrouter.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zyrotech.vsolrouter.R
import com.zyrotech.vsolrouter.data.Prefs
import com.zyrotech.vsolrouter.data.Snapshot
import com.zyrotech.vsolrouter.net.Device
import com.zyrotech.vsolrouter.net.RouterApi
import com.zyrotech.vsolrouter.net.Section
import com.zyrotech.vsolrouter.net.VsolClient
import com.zyrotech.vsolrouter.net.WebFormRunner
import com.zyrotech.vsolrouter.ui.MainActivity
import com.zyrotech.vsolrouter.widget.StatusWidget
import java.util.concurrent.TimeUnit

/** Shared helpers for the new-device alerts and the home-screen widget. */
object Background {
    private const val PERIODIC = "router-check"
    private const val ONCE = "router-check-now"
    const val CHANNEL = "new_devices"

    /** True while the app is on screen: the background check then stays away from the router's single login. */
    @Volatile var appVisible = false

    fun widgetIds(ctx: Context): IntArray =
        AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, StatusWidget::class.java))

    /** Run the 15-minute check only while alerts are on or a widget is placed. */
    fun schedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        val needed = Prefs(ctx).notifyNewDevices || widgetIds(ctx).isNotEmpty()
        if (!needed) { wm.cancelUniqueWork(PERIODIC); return }
        val req = PeriodicWorkRequestBuilder<RouterCheckWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun refreshNow(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<RouterCheckWorker>().build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, req)
    }

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "New devices on Wi-Fi", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "A device joined your router for the first time"
                },
            )
        }
    }

    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * Compare [devices] with the MACs seen before on [routerId]. Announces new ones when alerts are on.
     * The first time a router is seen, everything is just remembered (no flood of alerts).
     */
    fun checkNewDevices(ctx: Context, routerId: String, routerName: String, devices: List<Device>) {
        val prefs = Prefs(ctx)
        val known = prefs.knownMacs(routerId)
        val macs = devices.map { it.mac.lowercase() }.toSet()
        val fresh = if (known.isEmpty()) emptyList() else devices.filter { it.mac.lowercase() !in known }
        prefs.setKnownMacs(routerId, known + macs)
        if (fresh.isEmpty() || !prefs.notifyNewDevices || !canNotify(ctx)) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        for (d in fresh.take(5)) {
            val title = "New device on $routerName"
            val text = listOfNotNull(d.name ?: "Unknown device", d.ip, d.band?.label).joinToString(" · ")
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_router)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$text\nMAC: ${d.mac}"))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            try { NotificationManagerCompat.from(ctx).notify(d.mac.hashCode(), n) } catch (_: SecurityException) {}
        }
    }

    /** Pull the optical Rx power out of the status sections. */
    fun rxFrom(sections: List<Section>): String? =
        sections.asSequence().flatMap { it.rows.asSequence() }
            .firstOrNull { (k, v) -> v.isNotBlank() && listOf("rx power", "receive", "rx optical", "rx").any { k.contains(it, true) } }
            ?.second

    fun saveSnapshot(ctx: Context, model: String, rx: String?, devices: Int) {
        Prefs(ctx).snapshot = Snapshot(model, rx, devices, System.currentTimeMillis())
        StatusWidget.updateAll(ctx)
    }
}

/** Every 15 minutes (while on Wi-Fi): log in briefly, read devices and Rx, log out. */
class RouterCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (Background.appVisible) return Result.success() // the app holds the login right now
        val prefs = Prefs(ctx)
        val r = prefs.active() ?: return Result.success()
        val client = VsolClient(r.host)
        try {
            client.login(r.username, r.password)
            val api = RouterApi(client, WebFormRunner(ctx))
            val devices = api.devices()
            Background.checkNewDevices(ctx, r.id, r.name, devices)
            if (Background.widgetIds(ctx).isNotEmpty()) {
                val rx = try { Background.rxFrom(api.dashboard()) } catch (_: Exception) { null }
                Background.saveSnapshot(ctx, r.model ?: r.name, rx, devices.size)
            }
        } catch (_: Exception) {
            // Not on the router's Wi-Fi, or its single login is busy: try again next time.
        } finally {
            try { client.logout() } catch (_: Exception) {}
        }
        return Result.success()
    }
}
