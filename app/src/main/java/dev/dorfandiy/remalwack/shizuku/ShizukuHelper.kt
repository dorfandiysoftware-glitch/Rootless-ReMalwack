package dev.dorfandiy.remalwack.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import dev.dorfandiy.remalwack.BuildConfig
import dev.dorfandiy.remalwack.IShellService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku

enum class ShizukuStatus { NOT_RUNNING, NO_PERMISSION, READY }

object ShizukuHelper {
    private val _status = MutableStateFlow(ShizukuStatus.NOT_RUNNING)
    val status: StateFlow<ShizukuStatus> = _status
    private var shell: IShellService? = null
    private var bound = false

    private val permListener = Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }
    private val binderListener = Shizuku.OnBinderReceivedListener { refresh() }
    private val deadListener = Shizuku.OnBinderDeadListener { shell = null; bound = false; refresh() }

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName?, b: IBinder?) {
            shell = if (b != null && b.pingBinder()) IShellService.Stub.asInterface(b) else null
        }
        override fun onServiceDisconnected(n: ComponentName?) { shell = null; bound = false }
    }

    fun init() {
        Shizuku.addRequestPermissionResultListener(permListener)
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(deadListener)
        refresh()
    }

    fun refresh() {
        _status.value = try {
            when {
                !Shizuku.pingBinder() -> ShizukuStatus.NOT_RUNNING
                Shizuku.isPreV11() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> ShizukuStatus.NO_PERMISSION
                else -> ShizukuStatus.READY
            }
        } catch (e: Throwable) { ShizukuStatus.NOT_RUNNING }
        if (_status.value == ShizukuStatus.READY) bind()
    }

    fun requestPermission() {
        try { if (Shizuku.pingBinder()) Shizuku.requestPermission(100) } catch (_: Throwable) {}
    }

    private fun bind() {
        if (bound) return
        bound = true
        val args = Shizuku.UserServiceArgs(ComponentName(BuildConfig.APPLICATION_ID, ShellService::class.java.name))
            .daemon(false).processNameSuffix("shell").debuggable(BuildConfig.DEBUG).version(1)
        try { Shizuku.bindUserService(args, conn) } catch (e: Throwable) { bound = false }
    }

    /** Runs a command as shell. Returns output, or null when the service isn't connected yet. */
    fun exec(cmd: String): String? = try { shell?.exec(cmd) } catch (e: Throwable) { "ERR: ${e.message}" }

    val connected get() = shell != null

    /** Grants WRITE_SECURE_SETTINGS and tries to pre-approve the VPN so no consent dialog appears. */
    fun setupPermissions(ctx: Context): String {
        val pkg = ctx.packageName
        val a = exec("pm grant $pkg android.permission.WRITE_SECURE_SETTINGS") ?: return "Shizuku service not connected yet, try again"
        val b = exec("appops set $pkg ACTIVATE_VPN allow") ?: ""
        return if (a.startsWith("ERR") ) a else if (b.startsWith("ERR")) "Granted secure settings (VPN pre-approval failed: $b)" else "Permissions granted"
    }

    const val DNS_HOST = "dns.adguard-dns.com"

    /** VPN-free mode: switch system Private DNS to a filtering DoT provider. */
    fun setPrivateDns(on: Boolean): String {
        val cmd = if (on) "settings put global private_dns_specifier $DNS_HOST && settings put global private_dns_mode hostname"
        else "settings put global private_dns_mode opportunistic"
        return exec(cmd) ?: "Shizuku service not connected yet, try again"
    }

    fun privateDnsActive(): Boolean =
        exec("settings get global private_dns_mode")?.trim() == "hostname" &&
            exec("settings get global private_dns_specifier")?.trim() == DNS_HOST
}
