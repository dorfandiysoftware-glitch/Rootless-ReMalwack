package dev.dorfandiy.remalwack.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import dev.dorfandiy.remalwack.data.Prefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = Prefs(ctx)
        if (prefs.startOnBoot && VpnService.prepare(ctx) == null) DnsVpnService.start(ctx)
    }
}
