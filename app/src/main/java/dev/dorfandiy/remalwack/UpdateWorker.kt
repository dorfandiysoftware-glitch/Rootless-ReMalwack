package dev.dorfandiy.remalwack

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.Constraints
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.dorfandiy.remalwack.data.Updater
import dev.dorfandiy.remalwack.vpn.DnsVpnService
import java.util.concurrent.TimeUnit

class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val r = Updater.update(applicationContext)
        if (r.isSuccess) DnsVpnService.reload(applicationContext)
        return if (r.isSuccess) Result.success() else Result.retry()
    }

    companion object {
        private const val NAME = "hosts_update"
        fun schedule(ctx: Context, enabled: Boolean, hours: Int = 24) {
            val wm = WorkManager.getInstance(ctx)
            if (!enabled) { wm.cancelUniqueWork(NAME); return }
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(hours.toLong(), TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
