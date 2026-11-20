package dev.dorfandiy.remalwack.dns

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import dev.dorfandiy.remalwack.data.Prefs
import dev.dorfandiy.remalwack.shizuku.ShizukuHelper

/**
 * VPN-free protection: points the system "Private DNS" (DNS over TLS) at a filtering resolver.
 * It does not use the on-device hosts lists; filtering is done by the provider.
 */
object PrivateDns {
    class Provider(val id: String, val title: String, val host: String, val note: String)

    val presets = listOf(
        Provider("adguard", "AdGuard DNS", "dns.adguard-dns.com", "Ads, trackers, malware"),
        Provider("adguard_family", "AdGuard Family", "family.adguard-dns.com", "Ads, trackers, adult content"),
        Provider("mullvad", "Mullvad Adblock", "adblock.dns.mullvad.net", "Ads, trackers"),
        Provider("quad9", "Quad9", "dns.quad9.net", "Malware and phishing only"),
    )
    const val CUSTOM = "custom"

    fun host(prefs: Prefs): String =
        if (prefs.privateDnsId == CUSTOM) prefs.privateDnsCustom.trim().lowercase()
        else presets.firstOrNull { it.id == prefs.privateDnsId }?.host ?: presets[0].host

    fun validHost(h: String) =
        h.length in 4..253 && h.contains('.') && h.all { it.isLetterOrDigit() || it == '.' || it == '-' }

    /** True once WRITE_SECURE_SETTINGS has been granted (by Shizuku or `adb shell pm grant`). */
    fun canWrite(ctx: Context) =
        ctx.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED

    val adbCommand = "adb shell pm grant dev.dorfandiy.rootlessremalwack android.permission.WRITE_SECURE_SETTINGS"

    /** @return mode ("off", "opportunistic", "hostname") and the configured host. Readable without special rights. */
    fun current(ctx: Context): Pair<String, String> {
        val cr = ctx.contentResolver
        val mode = Settings.Global.getString(cr, "private_dns_mode") ?: "opportunistic"
        val host = Settings.Global.getString(cr, "private_dns_specifier") ?: ""
        return mode to host
    }

    fun isActive(ctx: Context, host: String): Boolean {
        val (m, h) = current(ctx)
        return m == "hostname" && h.equals(host, ignoreCase = true)
    }

    sealed class Result {
        object Applied : Result()
        /** The system settings could not be written; the hostname was copied and must be entered by hand. */
        class Manual(val host: String) : Result()
        class Failed(val message: String) : Result()
    }

    fun apply(ctx: Context, host: String): Result {
        if (!validHost(host)) return Result.Failed("Enter a valid hostname, for example dns.example.com")
        if (canWrite(ctx)) {
            return try {
                val cr = ctx.contentResolver
                Settings.Global.putString(cr, "private_dns_specifier", host)
                Settings.Global.putString(cr, "private_dns_mode", "hostname")
                Result.Applied
            } catch (e: Exception) { Result.Failed(e.message ?: "Could not write settings") }
        }
        val r = ShizukuHelper.setPrivateDns(host, true)
        if (r != null && !r.startsWith("ERR")) return if (isActive(ctx, host)) Result.Applied else Result.Manual(host)
        return Result.Manual(host)
    }

    fun restore(ctx: Context): Boolean {
        if (canWrite(ctx)) {
            return try {
                Settings.Global.putString(ctx.contentResolver, "private_dns_mode", "opportunistic"); true
            } catch (e: Exception) { false }
        }
        val r = ShizukuHelper.setPrivateDns("", false)
        return r != null && !r.startsWith("ERR")
    }

    fun copyHost(ctx: Context, host: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Private DNS", host))
    }

    fun openNetworkSettings(ctx: Context) {
        runCatching { ctx.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
