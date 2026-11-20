package dev.dorfandiy.remalwack.vpn

import android.content.Context
import android.net.VpnService
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import dev.dorfandiy.remalwack.data.Mode
import dev.dorfandiy.remalwack.data.Prefs
import java.util.concurrent.TimeUnit

/** Uninterrupted mode: every 15 minutes, brings the VPN back if the system stopped it while it should run. */
class Watchdog(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val c = applicationContext
        val p = Prefs(c)
        if (p.seamless && p.mode == Mode.VPN && p.vpnWanted && !VpnState.running.value && VpnService.prepare(c) == null) {
            DnsVpnService.start(c)
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "vpn_watchdog"
        fun schedule(ctx: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(ctx)
            if (!enabled) { wm.cancelUniqueWork(NAME); return }
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<Watchdog>(15, TimeUnit.MINUTES).build())
        }
    }
}
