package dev.dorfandiy.remalwack.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.dorfandiy.remalwack.MainActivity
import dev.dorfandiy.remalwack.R
import dev.dorfandiy.remalwack.data.Blocklist
import dev.dorfandiy.remalwack.data.Prefs
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

/**
 * Local DNS filter. The VPN interface only routes the (fake) DNS server address, so the rest of the
 * traffic never enters the tunnel. DNS queries for blocked names are answered with 0.0.0.0 / ::,
 * everything else is forwarded to the real upstream resolver.
 */
class DnsVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    @Volatile private var running = false
    private val pool = Executors.newFixedThreadPool(6)
    private lateinit var out: FileOutputStream
    @Volatile private var upstreams: List<InetAddress> = emptyList()
    @Volatile private var good = 0
    private val handler = Handler(Looper.getMainLooper())
    private var netCallback: ConnectivityManager.NetworkCallback? = null
    private var restarts = 0
    private var startedAt = 0L
    private var lastNotified = -1L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = Prefs(this)
        when (intent?.action) {
            ACTION_STOP -> {
                prefs.vpnWanted = false
                handler.removeCallbacksAndMessages(null)
                stopVpn(); stopForegroundCompat(); stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RELOAD -> { Thread { Blocklist.load(this) }.start(); return START_STICKY }
        }
        if (intent == null && !prefs.vpnWanted) { stopSelf(); return START_NOT_STICKY }
        prefs.vpnWanted = true
        goForeground(0)
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (running) return
        val prefs = Prefs(this)
        Blocklist.load(this)
        upstreams = buildUpstreams(prefs)
        good = 0
        val b = Builder()
            .setSession("Re-Malwack")
            .addAddress(CLIENT, 32)
            .addDnsServer(FAKE_DNS)
            .addRoute(FAKE_DNS, 32)
            .setMtu(1500)
            .setBlocking(true)
        // Apps listed as excluded (for example a browser with uBlock Origin) skip the VPN completely.
        for (pkg in prefs.excludedApps) runCatching { b.addDisallowedApplication(pkg) }
        if (Build.VERSION.SDK_INT >= 29) b.setMetered(false)
        val fd = runCatching { b.establish() }.getOrNull()
        if (fd == null) {
            // Permission was revoked or another VPN took over: nothing to keep alive.
            prefs.vpnWanted = false
            VpnState.running.value = false
            stopForegroundCompat(); stopSelf()
            return
        }
        tun = fd
        running = true
        startedAt = System.currentTimeMillis()
        VpnState.running.value = true
        watchNetwork()
        val input = FileInputStream(fd.fileDescriptor)
        out = FileOutputStream(fd.fileDescriptor)
        worker = Thread({
            val buf = ByteArray(32767)
            try {
                while (running) {
                    val n = input.read(buf)
                    if (n <= 0) continue
                    handle(buf, n)
                }
            } catch (_: Exception) {
            } finally {
                if (running) recover()
            }
        }, "dns-vpn").also { it.start() }
    }

    /** The tunnel broke while we still want it: close it and open it again, waiting a little longer each time. */
    private fun recover() {
        closeTunnel()
        if (!Prefs(this).vpnWanted) return
        if (System.currentTimeMillis() - startedAt > 60_000) restarts = 0
        val delay = (1000L shl restarts.coerceAtMost(5)).coerceAtMost(30_000L)
        restarts++
        handler.postDelayed({ if (Prefs(this).vpnWanted) startVpn() }, delay)
    }

    private fun closeTunnel() {
        running = false
        unwatchNetwork()
        runCatching { tun?.close() }
        tun = null
    }

    private fun stopVpn() {
        closeTunnel()
        VpnState.running.value = false
    }

    private fun buildUpstreams(prefs: Prefs): List<InetAddress> {
        val ips = (listOf(prefs.upstreamDns) + FALLBACKS).distinct()
        return ips.mapNotNull { runCatching { InetAddress.getByName(it) }.getOrNull() }
            .ifEmpty { listOf(InetAddress.getByName("1.1.1.1")) }
    }

    /** After the network changes, start with the preferred resolver again. */
    private fun watchNetwork() {
        if (netCallback != null) return
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { good = 0 }
            override fun onLost(network: Network) { good = 0 }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb); netCallback = cb }
    }

    private fun unwatchNetwork() {
        val cb = netCallback ?: return
        runCatching { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) }
        netCallback = null
    }

    // ---- foreground notification ----

    private fun goForeground(blocked: Long) {
        if (!Prefs(this).seamless) return
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Protection", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) })
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1,
            Intent(this, DnsVpnService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Re-Malwack protection is on")
            .setContentText("%,d requests blocked".format(blocked))
            .setContentIntent(open)
            .addAction(0, "Turn off", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        try {
            val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
            ServiceCompat.startForeground(this, ID, n, type)
            lastNotified = blocked
        } catch (_: Throwable) { /* the system refused a foreground start; the VPN still runs */ }
    }

    private fun stopForegroundCompat() {
        runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
    }

    private fun maybeUpdateNotification() {
        val b = VpnState.blocked.value
        if (b - lastNotified >= 25 && Prefs(this).seamless) goForeground(b)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopVpn()
        pool.shutdownNow()
        super.onDestroy()
    }

    override fun onRevoke() {
        // Another VPN replaced ours (or the person turned it off in system settings).
        Prefs(this).vpnWanted = false
        stopVpn()
        stopForegroundCompat()
        super.onRevoke()
    }

    // ---- packet handling ----

    private fun handle(buf: ByteArray, len: Int) {
        if ((buf[0].toInt() shr 4) != 4) return
        val ihl = (buf[0].toInt() and 0x0F) * 4
        if (len < ihl + 8 || buf[9].toInt() != 17) return // UDP only
        val dstPort = ((buf[ihl + 2].toInt() and 0xFF) shl 8) or (buf[ihl + 3].toInt() and 0xFF)
        if (dstPort != 53) return
        val total = (((buf[2].toInt() and 0xFF) shl 8) or (buf[3].toInt() and 0xFF)).coerceAtMost(len)
        val dns = buf.copyOfRange(ihl + 8, total)
        if (dns.size < 17) return
        val srcIp = buf.copyOfRange(12, 16)
        val dstIp = buf.copyOfRange(16, 20)
        val srcPort = ((buf[ihl].toInt() and 0xFF) shl 8) or (buf[ihl + 1].toInt() and 0xFF)

        val q = parseQuestion(dns) ?: run { forward(dns, srcIp, dstIp, srcPort); return }
        if (q.name in DOH_CANARY) {
            // Firefox/Gecko disables its built-in DNS-over-HTTPS when this domain returns NXDOMAIN.
            VpnState.record(q.name, true)
            val r = ByteArray(q.end)
            System.arraycopy(dns, 0, r, 0, q.end)
            r[2] = (0x80 or (dns[2].toInt() and 0x01)).toByte()
            r[3] = 0x83.toByte() // RA=1, RCODE=3 (NXDOMAIN)
            r[6] = 0; r[7] = 0; r[8] = 0; r[9] = 0; r[10] = 0; r[11] = 0
            write(r, srcIp, dstIp, srcPort)
            return
        }
        val blocked = Blocklist.isBlocked(q.name)
        VpnState.record(q.name, blocked)
        if (blocked && (q.type == 1 || q.type == 28 || q.type == 65)) {
            write(buildBlocked(dns, q), srcIp, dstIp, srcPort)
            maybeUpdateNotification()
        } else {
            forward(dns, srcIp, dstIp, srcPort)
        }
    }

    private val DOH_CANARY = setOf("use-application-dns.net", "mask.icloud.com", "mask-h2.icloud.com")

    private class Question(val name: String, val type: Int, val end: Int)

    private fun parseQuestion(d: ByteArray): Question? {
        if (d.size < 12) return null
        val qd = ((d[4].toInt() and 0xFF) shl 8) or (d[5].toInt() and 0xFF)
        if (qd < 1) return null
        var p = 12
        val sb = StringBuilder()
        while (true) {
            if (p >= d.size) return null
            val l = d[p].toInt() and 0xFF
            if (l == 0) { p++; break }
            if (l and 0xC0 != 0 || p + 1 + l > d.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 1..l) sb.append((d[p + i].toInt() and 0xFF).toChar())
            p += 1 + l
        }
        if (p + 4 > d.size) return null
        val type = ((d[p].toInt() and 0xFF) shl 8) or (d[p + 1].toInt() and 0xFF)
        return Question(sb.toString().lowercase(), type, p + 4)
    }

    private fun buildBlocked(q: ByteArray, qu: Question): ByteArray {
        val answerLen = when (qu.type) { 1 -> 16; 28 -> 28; else -> 0 }
        val r = ByteArray(qu.end + answerLen)
        System.arraycopy(q, 0, r, 0, qu.end)
        r[2] = (0x80 or (q[2].toInt() and 0x01)).toByte() // QR=1, keep RD
        r[3] = 0x80.toByte()                                // RA=1, RCODE=0
        r[6] = 0; r[7] = if (answerLen > 0) 1 else 0       // ANCOUNT
        r[8] = 0; r[9] = 0; r[10] = 0; r[11] = 0           // NS/AR = 0
        if (answerLen > 0) {
            var p = qu.end
            r[p++] = 0xC0.toByte(); r[p++] = 0x0C            // name pointer
            r[p++] = 0; r[p++] = qu.type.toByte()            // type
            r[p++] = 0; r[p++] = 1                           // class IN
            r[p++] = 0; r[p++] = 0; r[p++] = 0; r[p++] = 60  // TTL
            r[p++] = 0; r[p++] = if (qu.type == 1) 4 else 16 // RDLENGTH, RDATA stays zero
        }
        return r
    }

    /** Sends the query to the preferred resolver, then to the others if it does not answer. */
    private fun forward(dns: ByteArray, srcIp: ByteArray, dstIp: ByteArray, srcPort: Int) {
        pool.execute {
            val list = upstreams
            if (list.isEmpty()) return@execute
            val first = good.coerceIn(0, list.size - 1)
            for (k in list.indices) {
                val idx = (first + k) % list.size
                try {
                    DatagramSocket().use { s ->
                        protect(s)
                        s.soTimeout = if (k == 0) 2500 else 3500
                        s.send(DatagramPacket(dns, dns.size, list[idx], 53))
                        val rb = ByteArray(4096)
                        val p = DatagramPacket(rb, rb.size)
                        s.receive(p)
                        good = idx
                        write(rb.copyOf(p.length), srcIp, dstIp, srcPort)
                    }
                    return@execute
                } catch (_: Exception) { }
            }
            // Nobody answered: say so at once, so apps fail fast instead of hanging.
            write(serverFailure(dns), srcIp, dstIp, srcPort)
        }
    }

    private fun serverFailure(q: ByteArray): ByteArray {
        val end = parseQuestion(q)?.end ?: 12
        val r = q.copyOf(end.coerceAtMost(q.size))
        r[2] = (0x80 or (q[2].toInt() and 0x01)).toByte()
        r[3] = 0x82.toByte() // RA=1, RCODE=2 (SERVFAIL)
        r[6] = 0; r[7] = 0; r[8] = 0; r[9] = 0; r[10] = 0; r[11] = 0
        return r
    }

    /** Wraps a DNS payload in IPv4+UDP (addresses swapped) and writes it to the tunnel. */
    private fun write(dns: ByteArray, clientIp: ByteArray, dnsIp: ByteArray, clientPort: Int) {
        val total = 20 + 8 + dns.size
        val p = ByteArray(total)
        p[0] = 0x45
        p[2] = (total shr 8).toByte(); p[3] = total.toByte()
        p[6] = 0x40 // don't fragment
        p[8] = 64; p[9] = 17
        System.arraycopy(dnsIp, 0, p, 12, 4)
        System.arraycopy(clientIp, 0, p, 16, 4)
        var sum = 0
        for (i in 0 until 20 step 2) sum += ((p[i].toInt() and 0xFF) shl 8) or (p[i + 1].toInt() and 0xFF)
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        val ck = sum.inv() and 0xFFFF
        p[10] = (ck shr 8).toByte(); p[11] = ck.toByte()
        p[20] = 0; p[21] = 53
        p[22] = (clientPort shr 8).toByte(); p[23] = clientPort.toByte()
        val ul = 8 + dns.size
        p[24] = (ul shr 8).toByte(); p[25] = ul.toByte()
        System.arraycopy(dns, 0, p, 28, dns.size)
        synchronized(this) { runCatching { out.write(p) } }
    }

    companion object {
        const val ACTION_STOP = "dev.dorfandiy.remalwack.STOP"
        const val ACTION_RELOAD = "dev.dorfandiy.remalwack.RELOAD"
        private const val CHANNEL = "protection"
        private const val ID = 7
        private val FALLBACKS = listOf("1.1.1.1", "9.9.9.9", "8.8.8.8")
        private const val CLIENT = "10.111.222.1"
        private const val FAKE_DNS = "10.111.222.2"

        fun start(ctx: Context) {
            val i = Intent(ctx, DnsVpnService::class.java)
            runCatching {
                if (Prefs(ctx).seamless) ContextCompat.startForegroundService(ctx, i) else ctx.startService(i)
            }
        }
        fun stop(ctx: Context) {
            Prefs(ctx).vpnWanted = false
            runCatching { ctx.startService(Intent(ctx, DnsVpnService::class.java).setAction(ACTION_STOP)) }
        }
        fun reload(ctx: Context) {
            if (VpnState.running.value) ctx.startService(Intent(ctx, DnsVpnService::class.java).setAction(ACTION_RELOAD))
        }
    }
}
